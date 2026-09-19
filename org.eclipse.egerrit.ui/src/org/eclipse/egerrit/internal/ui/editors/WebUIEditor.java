/*******************************************************************************
 * Copyright (c) 2026 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson AB - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.ui.editors;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Base64;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.ChangeOption;
import org.eclipse.egerrit.internal.core.command.GetChangeCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.FileInfo;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.EGerritImages;
import org.eclipse.egerrit.internal.ui.EGerritUIPlugin;
import org.eclipse.egerrit.internal.ui.editors.model.ChangeDetailEditorInput;
import org.eclipse.egerrit.internal.ui.utils.ActiveWorkspaceRevision;
import org.eclipse.egerrit.internal.ui.utils.GerritWebLink;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.eclipse.egerrit.internal.ui.utils.WebUIInjection;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.part.EditorPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Editor showing the web UI of the Gerrit server for a change.
 * <p>
 * The web UI is displayed in an embedded browser. A small Eclipse button is injected next to the files so that they can
 * be opened in the Eclipse editor. If the change is not checked out in the workspace, the user is asked to check it out
 * before the file is opened.
 *
 * @since 1.4
 */
public class WebUIEditor extends EditorPart {

	public static final String EDITOR_ID = "org.eclipse.egerrit.ui.editors.WebUIEditor"; //$NON-NLS-1$

	private static final Logger logger = LoggerFactory.getLogger(WebUIEditor.class);

	private static final String OPEN_FILE_FUNCTION = "egerritOpenFile"; //$NON-NLS-1$

	private static final String OPEN_FILE_SCHEME = "egerrit-open-in-eclipse:"; //$NON-NLS-1$

	private static final String LOGO_PATH = "icons/eclipse16.png"; //$NON-NLS-1$

	/**
	 * Mouse buttons used to navigate to the previous page: 8 on X11 (see the Eclipse bug 170097), 4 on the other
	 * platforms
	 */
	private static final int[] MOUSE_PREVIOUS_BUTTONS = new int[] { 8, 4 };

	/**
	 * Mouse buttons used to navigate to the next page: 9 on X11 (see the Eclipse bug 170097), 5 on the other platforms
	 */
	private static final int[] MOUSE_NEXT_BUTTONS = new int[] { 9, 5 };

	private GerritClient fGerritClient;

	private ChangeInfo fChangeInfo;

	private String fUrl;

	private Browser fBrowser;

	private BrowserFunction fOpenFileFunction;

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if (!(input instanceof ChangeDetailEditorInput)) {
			throw new PartInitException("Invalid editor input"); //$NON-NLS-1$
		}
		setSite(site);
		setInput(input);
		ChangeDetailEditorInput changeInput = (ChangeDetailEditorInput) input;
		fGerritClient = changeInput.getClient();
		fChangeInfo = changeInput.getChange();
		setPartName(changeInput.getName());
		setTitleToolTip(changeInput.getToolTipText());
		setTitleImage(EGerritImages.get(EGerritImages.WEB_UI_IMAGE));
		fUrl = UIUtils.buildChangeWebUIUrl(fGerritClient.getRepository().getServerInfo(),
				fGerritClient.getRepository().getVersion(), fChangeInfo);
	}

	@Override
	public void createPartControl(Composite parent) {
		try {
			fBrowser = new Browser(parent, SWT.NONE);
		} catch (SWTError error) {
			createFallback(parent, error);
			return;
		}
		fOpenFileFunction = new BrowserFunction(fBrowser, OPEN_FILE_FUNCTION) {
			@Override
			public Object function(Object[] arguments) {
				if (arguments != null && arguments.length > 0 && arguments[0] != null) {
					String link = arguments[0].toString();
					//Do the work asynchronously: the browser is waiting for this call to return
					Display.getDefault().asyncExec(() -> openFileInEclipse(link));
				}
				return null;
			}
		};
		//Second way to reach the Java code from the injected buttons, in case the
		//JavaScript bridge (BrowserFunction) is not available: the custom URL is
		//intercepted and the navigation is cancelled
		fBrowser.addLocationListener(new LocationListener() {
			@Override
			public void changing(LocationEvent event) {
				String location = event.location;
				if (location != null && location.startsWith(OPEN_FILE_SCHEME)) {
					event.doit = false;
					String link = decodeLink(location.substring(OPEN_FILE_SCHEME.length()));
					Display.getDefault().asyncExec(() -> openFileInEclipse(link));
				}
			}

			@Override
			public void changed(LocationEvent event) {
				//Nothing to do
			}
		});
		//The "previous" and "next" buttons of the mouse are not handled by the browser
		//widget: use them to navigate in the history. The event must be cancelled
		//(doit = false) so that SWT claims it and does not propagate it to the parent
		//widgets, where the workbench would switch the editors.
		fBrowser.addListener(SWT.MouseDown, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (contains(MOUSE_PREVIOUS_BUTTONS, event.button)) {
					event.doit = false;
					logger.debug("Previous mouse button pressed, going back"); //$NON-NLS-1$
					fBrowser.back();
				} else if (contains(MOUSE_NEXT_BUTTONS, event.button)) {
					event.doit = false;
					logger.debug("Next mouse button pressed, going forward"); //$NON-NLS-1$
					fBrowser.forward();
				}
			}
		});
		fBrowser.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {
				injectScript();
			}
		});
		fBrowser.setUrl(fUrl);
	}

	private static boolean contains(int[] values, int value) {
		for (int candidate : values) {
			if (candidate == value) {
				return true;
			}
		}
		return false;
	}

	private static String decodeLink(String value) {
		try {
			return URLDecoder.decode(value, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			return value;
		}
	}

	/**
	 * Fallback used when the embedded browser cannot be created: show a message and offer to open the page in an
	 * external browser.
	 */
	private void createFallback(Composite parent, SWTError error) {
		EGerritCorePlugin.logError(error.getMessage());
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		Label label = new Label(composite, SWT.WRAP);
		label.setText(Messages.WebUIEditor_noBrowser);
		label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		Button button = new Button(composite, SWT.PUSH);
		button.setText(Messages.WebUIEditor_openExternalBrowser);
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				openInExternalBrowser();
			}
		});
	}

	private void openInExternalBrowser() {
		try {
			IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
			IWebBrowser browser = support.getExternalBrowser();
			browser.openURL(new URL(fUrl));
		} catch (PartInitException | MalformedURLException e) {
			EGerritCorePlugin.logError(e.getMessage());
		}
	}

	private void injectScript() {
		if (fBrowser == null || fBrowser.isDisposed()) {
			return;
		}
		try {
			fBrowser.evaluate(WebUIInjection.getScript(getLogoDataUrl()));
			logger.debug("eGerrit button injection script sent to {}", fUrl); //$NON-NLS-1$
		} catch (SWTError e) {
			EGerritCorePlugin.logError(e.getMessage());
		}
	}

	private String getLogoDataUrl() {
		try (InputStream stream = FileLocator.openStream(EGerritUIPlugin.getDefault().getBundle(), new Path(LOGO_PATH),
				false)) {
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(stream.readAllBytes()); //$NON-NLS-1$
		} catch (IOException e) {
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * Open a link of the web UI in the Eclipse editor.
	 *
	 * @param link
	 *            the link of the file in the web UI
	 */
	private void openFileInEclipse(String link) {
		GerritWebLink webLink = GerritWebLink.parse(link);
		if (webLink == null || webLink.getPath() == null || webLink.isMagicPath()) {
			//Nothing to open in the workspace
			return;
		}
		RevisionInfo revision;
		try {
			revision = findRevision(webLink);
		} catch (EGerritException e) {
			EGerritCorePlugin.logError(e.getMessage());
			return;
		}
		if (revision == null || !ensureCheckedOut(revision)) {
			return;
		}
		FileInfo fileInfo = revision.getFiles().get(webLink.getPath());
		boolean opened;
		if (fileInfo != null) {
			opened = UIUtils.openSingleFile(fileInfo, fGerritClient, revision, 0);
		} else {
			opened = UIUtils.openSingleFile(webLink.getPath(), fGerritClient, revision, 0);
		}
		if (!opened) {
			UIUtils.displayInformation(Messages.UIFilesTable_3, Messages.UIFilesTable_2 + '\n' + webLink.getPath());
		}
	}

	/**
	 * Find the revision of the change displayed in the web UI. If the web UI shows another change than the one this
	 * editor was opened with, the change is fetched from the server.
	 */
	private RevisionInfo findRevision(GerritWebLink webLink) throws EGerritException {
		ChangeInfo change = fChangeInfo;
		if (change.getRevision() == null || !webLink.getChangeNumber().equals(Integer.toString(change.get_number()))) {
			GetChangeCommand command = fGerritClient.getChange(webLink.getChangeNumber());
			command.addOption(ChangeOption.ALL_REVISIONS);
			command.addOption(ChangeOption.ALL_FILES);
			change = command.call();
		}
		if (change == null) {
			return null;
		}
		int patchset = webLink.getPatchsetNumber();
		if (patchset >= 0) {
			for (RevisionInfo revision : change.getRevisions().values()) {
				if (revision.get_number() == patchset) {
					return revision;
				}
			}
		}
		return change.getRevision();
	}

	/**
	 * Make sure the revision is checked out in the workspace. If not, ask the user whether it should be checked out.
	 *
	 * @return <code>true</code> if the revision is (now) checked out and the file can be opened
	 */
	private boolean ensureCheckedOut(RevisionInfo revision) {
		if (isActiveRevision(revision)) {
			return true;
		}
		boolean checkout = MessageDialog.openQuestion(getSite().getShell(), Messages.WebUIEditor_checkoutTitle,
				NLS.bind(Messages.WebUIEditor_checkoutMessage, Integer.toString(revision.getChangeInfo().get_number()),
						Integer.toString(revision.get_number())));
		if (!checkout) {
			return false;
		}
		CheckoutRevision checkoutRevision = new CheckoutRevision(revision, fGerritClient);
		//Fetch and check out the change without showing the "Fetch a change from Gerrit" wizard
		checkoutRevision.setSilentCheckout(true);
		checkoutRevision.run();
		//CheckoutRevision already reported the problem if the checkout did not happen
		return isActiveRevision(revision);
	}

	private static boolean isActiveRevision(RevisionInfo revision) {
		RevisionInfo active = ActiveWorkspaceRevision.getInstance().getActiveRevision();
		if (active == null || revision == null) {
			return false;
		}
		if (active == revision) {
			return true;
		}
		ChangeInfo activeChange = active.getChangeInfo();
		ChangeInfo change = revision.getChangeInfo();
		if (activeChange == null || change == null) {
			return false;
		}
		return activeChange.getId().equals(change.getId()) && active.get_number() == revision.get_number();
	}

	@Override
	public void setFocus() {
		if (fBrowser != null && !fBrowser.isDisposed()) {
			fBrowser.setFocus();
		}
	}

	@Override
	public void dispose() {
		if (fOpenFileFunction != null && !fOpenFileFunction.isDisposed()) {
			fOpenFileFunction.dispose();
		}
		super.dispose();
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public void doSave(org.eclipse.core.runtime.IProgressMonitor monitor) {
		//Nothing to save
	}

	@Override
	public void doSaveAs() {
		//Nothing to save
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}
}
