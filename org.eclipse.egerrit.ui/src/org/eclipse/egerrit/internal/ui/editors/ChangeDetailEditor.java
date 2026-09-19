/*******************************************************************************
 * Copyright (c) 2015-2016 Ericsson
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Guy Perron - initial API and implementation
 *     Jacques Bouthillier - Fill the message tab
 *     Jacques Bouthillier - Refactor the class
 *     Patrick-Jeffrey Pollo Guilbert - Add hover message on subject
 *******************************************************************************/

package org.eclipse.egerrit.internal.ui.editors;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.conversion.text.NumberToStringConverter;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.AbandonCommand;
import org.eclipse.egerrit.internal.core.command.DeleteDraftChangeCommand;
import org.eclipse.egerrit.internal.core.command.PublishDraftChangeCommand;
import org.eclipse.egerrit.internal.core.command.RestoreCommand;
import org.eclipse.egerrit.internal.core.command.RevertCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.AbandonInput;
import org.eclipse.egerrit.internal.core.rest.RestoreInput;
import org.eclipse.egerrit.internal.core.rest.RevertInput;
import org.eclipse.egerrit.internal.model.ActionConstants;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.process.CherryPickProcess;
import org.eclipse.egerrit.internal.process.RebaseProcess;
import org.eclipse.egerrit.internal.process.ReplyProcess;
import org.eclipse.egerrit.internal.process.SubmitProcess;
import org.eclipse.egerrit.internal.ui.editors.model.ChangeDetailEditorInput;
import org.eclipse.egerrit.internal.ui.table.provider.DeleteDraftRevisionProvider;
import org.eclipse.egerrit.internal.ui.table.provider.PatchSetHandlerProvider;
import org.eclipse.egerrit.internal.ui.tabs.DetailsTabView;
import org.eclipse.egerrit.internal.ui.tabs.HistoryTabView;
import org.eclipse.egerrit.internal.ui.tabs.MessageTabView;
import org.eclipse.egerrit.internal.ui.tabs.ObservableCollector;
import org.eclipse.egerrit.internal.ui.utils.ActiveWorkspaceRevision;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.EditorPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.icu.text.NumberFormat;

public class ChangeDetailEditor extends EditorPart {
	private static Logger logger = LoggerFactory.getLogger(ChangeDetailEditor.class);

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String EDITOR_ID = "org.eclipse.egerrit.ui.editors.ChangeDetailEditor"; //$NON-NLS-1$

	private static final int REFRESH_RATE = 60000;

	private DetailsTabView detailsTab = null;

	private HistoryTabView historytab = null;

	private MessageTabView messageTab = null;

	private ChangeInfo fChangeInfo;

	private GerritClient fGerritClient = null;

	private Text subjectData;

	private Text statusData;

	private Text shortIdData;

	private Composite headerSection;

	private ModelLoader loader;
	// ------------------------------------------------------------------------
	// Constructor and life cycle
	// ------------------------------------------------------------------------

	private EContentAdapter subjectListener;

	private DataBindingContext bindingContext = new DataBindingContext();

	private DeleteDraftRevisionProvider deleteDraftRevision = null;

	private PatchSetHandlerProvider patchSetSelector;

	private HideControlObservable hidableRevertButton;

	private HideControlObservable hidableSubmitButton;

	private ObservableCollector observableCollector;

	private CheckForUpdate checkForUpdates;

	/**
	 * The constructor.
	 */
	public ChangeDetailEditor() {
		super();
	}

	@Override
	public void createPartControl(final Composite parent) {
		loader = ModelLoader.initialize(fGerritClient, fChangeInfo);
		loader.loadBasicInformation(false);
		parent.setLayout(new GridLayout(1, false));
		parent.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

		headerSection = headerSection(parent);
		headerSection.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		TabFolder tabFolder = new TabFolder(parent, SWT.NONE);
		tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tabFolder.setBackground(parent.getBackground());

		historytab = new HistoryTabView(fGerritClient, fChangeInfo);
		historytab.create(tabFolder);

		messageTab = new MessageTabView(fGerritClient, fChangeInfo);
		messageTab.create(tabFolder);

		detailsTab = new DetailsTabView(fGerritClient, fChangeInfo);
		detailsTab.create(tabFolder);

		Composite compButton = buttonSection(parent);
		compButton.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		setPartName(((ChangeDetailEditorInput) getEditorInput()).getName());
		observableCollector = new ObservableCollector(bindingContext);

		checkForUpdates = new CheckForUpdate();
		checkForUpdates.schedule(REFRESH_RATE);
	}

	private Composite headerSection(final Composite parent) {
		Group groupHeader = new Group(parent, SWT.NONE);
		groupHeader.setLayout(new GridLayout(10, false));
		groupHeader.setBackground(parent.getBackground());

		Label lblId = new Label(groupHeader, SWT.NONE);
		lblId.setText(Messages.ChangeDetailEditor_2);

		shortIdData = new Text(groupHeader, SWT.NONE);
		shortIdData.setEditable(false);
		shortIdData.setBackground(parent.getBackground());
		shortIdData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		statusData = new Text(groupHeader, SWT.LEFT);
		GridData gdLblStatus = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		statusData.setLayoutData(gdLblStatus);
		statusData.setEditable(false);
		statusData.addListener(SWT.Modify, event -> statusData.getParent().layout());

		Label lblSubject = new Label(groupHeader, SWT.NONE);
		lblSubject.setText(Messages.ChangeDetailEditor_3);
		GridData gdSubject = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gdSubject.horizontalIndent = 10;
		lblSubject.setLayoutData(gdSubject);

		subjectData = new Text(groupHeader, SWT.NONE);
		GridData gdLblSubjectData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		subjectData.setLayoutData(gdLblSubjectData);
		subjectData.setEditable(false);
		subjectData.setBackground(parent.getBackground());

		final ToolTip tip = new ToolTip(parent.getShell(), SWT.NONE);

		subjectData.addListener(SWT.MouseMove, event -> tip.setVisible(false));
		subjectData.addListener(SWT.MouseHover, event -> {
			tip.setMessage(subjectData.getText());
			tip.setVisible(true);
		});

		subjectData.addListener(SWT.Modify, event -> subjectData.getParent().layout());

		final String ACTIVATION_MESSAGE = Messages.ChangeDetailEditor_4;
		Button activeReview = new Button(groupHeader, SWT.CHECK);
		activeReview.setSelection(false);
		activeReview.setText(ACTIVATION_MESSAGE);
		activeReview.setToolTipText(Messages.ChangeDetailEditor_5);

		IObservableValue observeActiveRevisionStateForButtonText = BeanProperties.value("activeRevision") //$NON-NLS-1$
				.observe(ActiveWorkspaceRevision.getInstance());
		bindingContext.bindValue(WidgetProperties.text().observe(activeReview), observeActiveRevisionStateForButtonText,
				new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new UpdateValueStrategy() {
					@Override
					public Object convert(Object value) {
						if (value == null) {
							return ACTIVATION_MESSAGE;
						}
						if (((RevisionInfo) value).getChangeInfo().getId().equals(fChangeInfo.getId())) {
							return Messages.ChangeDetailEditor_7 + ((RevisionInfo) value).get_number();
						}
						return ACTIVATION_MESSAGE;
					}
				});

		IObservableValue observeActiveRevisionStateForButtonEnablement = BeanProperties.value("activeRevision") //$NON-NLS-1$
				.observe(ActiveWorkspaceRevision.getInstance());
		bindingContext.bindValue(WidgetProperties.buttonSelection().observe(activeReview),
				observeActiveRevisionStateForButtonEnablement,
				new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new UpdateValueStrategy() {
					@Override
					public Object convert(Object value) {
						if (value == null) {
							return false;
						}
						if (((RevisionInfo) value).getChangeInfo().getId().equals(fChangeInfo.getId())) {
							return true;
						}
						return false;
					}
				});

		activeReview.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (activeReview.getSelection()) {
					CompletableFuture.runAsync(() -> {
						ActiveWorkspaceRevision.getInstance().activateCurrentRevision(fGerritClient,
								fChangeInfo.getUserSelectedRevision() != null
										? fChangeInfo.getUserSelectedRevision()
										: fChangeInfo.getRevision());
					});
				} else {
					ActiveWorkspaceRevision.getInstance().deactiveCurrentRevision();
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// ignore
			}
		});

		//Add Delete revision button for DRAFT patchset
		deleteDraftRevision = new DeleteDraftRevisionProvider();
		deleteDraftRevision.create(groupHeader, fGerritClient, fChangeInfo);

		//Create the PatchSetButton
		patchSetSelector = new PatchSetHandlerProvider();
		patchSetSelector.create(groupHeader, fChangeInfo, fGerritClient);

		//Set the binding for this section
		headerSectionDataBindings();
		return groupHeader;
	}

	private Composite buttonSection(final Composite parent) {
		final int NUMBER_OF_BUTTONS = 8;
		final Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(NUMBER_OF_BUTTONS, true));

		Button refresh = new Button(c, SWT.PUSH);
		refresh.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		refresh.setText(ActionConstants.REFRESH.getLiteral());
		refresh.setToolTipText(Messages.ChangeDetailEditor_9);
		refresh.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refreshStatus();
			}
		});

		Button submitButton = new Button(c, SWT.PUSH);
		submitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		submitButton.setText(ActionConstants.SUBMIT.getLiteral());
		submitButton.setEnabled(false);

		Button revertButton = new Button(c, SWT.PUSH);
		revertButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		revertButton.setText(ActionConstants.REVERT.getLiteral());
		revertButton.setEnabled(false);

		//Bind the submit button
		bindSubmitButton(submitButton, revertButton);

		//Bind the revert button
		bindRevertButton(submitButton, revertButton);

		submitButton.addSelectionListener(submitSelectionListener());

		revertButton.addSelectionListener(revertSelectionListener(revertButton));

		Button fAbandon = new Button(c, SWT.PUSH);
		fAbandon.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		fAbandon.setEnabled(false);
		fAbandon.setText(ActionConstants.ABANDON.getLiteral());

		Button fRestore = new Button(c, SWT.PUSH);
		fRestore.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		fRestore.setEnabled(false);
		fRestore.setText(ActionConstants.RESTORE.getLiteral());

		//Bind the abandon button
		bindAbandonButton(fAbandon, fRestore);

		//Bind the restore button
		bindRestoreButton(fAbandon, fRestore);

		fAbandon.addSelectionListener(abandonSelectionListener(fAbandon));

		fRestore.addSelectionListener(restoreSelectionListener(fAbandon));

		Button rebaseButton = new Button(c, SWT.PUSH);
		rebaseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		rebaseButton.setText(ActionConstants.REBASE.getLiteral() + "..."); //$NON-NLS-1$

		//Bind the rebase button
		bindRebaseButton(rebaseButton);
		rebaseButton.addSelectionListener(rebaseButtonListener(rebaseButton.getShell()));

		Button download = new Button(c, SWT.PUSH);
		download.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		download.setText(Messages.ChangeDetailEditor_24);
		download.addSelectionListener(downloadButtonListener(parent));

		Button cherryPickToRemoteBranch = new Button(c, SWT.PUSH);
		cherryPickToRemoteBranch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		cherryPickToRemoteBranch.setText(ActionConstants.CHERRYPICK.getLiteral());
		cherryPickToRemoteBranch.setToolTipText(Messages.ChangeDetailEditor_cherryPickBranch);
		IObservableValue cherryPickAble = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION)
				.value(ModelPackage.Literals.REVISION_INFO__CHERRYPICKABLE)
				.observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.enabled().observe(cherryPickToRemoteBranch), cherryPickAble, null,
				null);

		cherryPickToRemoteBranch.addSelectionListener(cherryPickSelectionListener(cherryPickToRemoteBranch));

		Button replyButton = new Button(c, SWT.PUSH | SWT.DROP_DOWN | SWT.ARROW_DOWN);
		replyButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		replyButton.setText(ActionConstants.REPLY.getLiteral());
		if (fGerritClient.getRepository().getServerInfo().isAnonymous()) {
			replyButton.setEnabled(false);
		}
		replyButton.addSelectionListener(replySelectionListener(replyButton));

		Button draftButton = new Button(c, SWT.PUSH | SWT.DROP_DOWN | SWT.ARROW_DOWN);
		draftButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		draftButton.setText(ActionConstants.DRAFT.getLiteral());
		IObservableValue observeDeleteable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__DELETEABLE)
				.observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.enabled().observe(draftButton), observeDeleteable,
				new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
		draftButton.addSelectionListener(draftSelectionListener(draftButton));

		c.setSize(c.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		return c;
	}

	/**
	 * @param draftButton
	 * @return
	 */
	private SelectionAdapter draftSelectionListener(Button draftButton) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				org.eclipse.swt.widgets.Menu menu = new org.eclipse.swt.widgets.Menu(draftButton.getShell(),
						SWT.POP_UP);

				final MenuItem itemPublish = new MenuItem(menu, SWT.PUSH);
				itemPublish.setText(ActionConstants.PUBLISH.getLiteral());
				itemPublish.addSelectionListener(new SelectionListener() {

					@Override
					public void widgetSelected(SelectionEvent e) {
						PublishDraftChangeCommand publishCommand = fGerritClient
								.publishDraftChange(fChangeInfo.getId());
						try {
							publishCommand.call();
							refreshStatus();
						} catch (EGerritException e1) {
							EGerritCorePlugin
									.logError(fGerritClient.getRepository().formatGerritVersion() + e1.getMessage());
						}
					}

					@Override
					public void widgetDefaultSelected(SelectionEvent e) {
						// ignore
					}
				});

				MenuItem itemDeleteDraftChange = new MenuItem(menu, SWT.PUSH);
				itemDeleteDraftChange.setText(Messages.ChangeDetailEditor_27);

				itemDeleteDraftChange.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						super.widgetSelected(e);
						if (!MessageDialog.openConfirm(Display.getDefault().getActiveShell(),
								Messages.ChangeDetailEditor_28,
								NLS.bind(Messages.ChangeDetailEditor_29, fChangeInfo.getSubject()))) {
							return;
						}
						DeleteDraftChangeCommand deleteDraftChangeCmd = fGerritClient
								.deleteDraftChange(fChangeInfo.getId());
						try {
							deleteDraftChangeCmd.call();
							IWorkbench workbench = PlatformUI.getWorkbench();
							final IWorkbenchPage activePage = workbench.getActiveWorkbenchWindow().getActivePage();

							IEditorPart editor = activePage.getActiveEditor();
							activePage.closeEditor(editor, false);
						} catch (EGerritException e1) {
							EGerritCorePlugin
									.logError(fGerritClient.getRepository().formatGerritVersion() + e1.getMessage());
						}
					}
				});

				Point loc = draftButton.getLocation();
				Rectangle rect = draftButton.getBounds();

				Point mLoc = new Point(loc.x - 1, loc.y + rect.height);

				menu.setLocation(draftButton.getDisplay().map(draftButton.getParent(), null, mLoc));

				menu.setVisible(true);
			}
		};
	}

	/**
	 * @param replyButton
	 * @return
	 */
	private SelectionAdapter replySelectionListener(Button replyButton) {
		return new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				//Adjust the list of label which the current user can set to a maximum value
				String loginUser = fGerritClient.getRepository().getServerInfo().getUserName();
				Map<String, Integer> adjustedAllowedButton = fChangeInfo.getLabelsNotAtMax(loginUser);
				if (adjustedAllowedButton.isEmpty()) {
					//Should open directly the reply dialog, no sub-menu
					ReplyProcess replyProcess = new ReplyProcess();
					replyProcess.handleReplyDialog(replyButton.getShell(), fChangeInfo, fGerritClient,
							fChangeInfo.getUserSelectedRevision(), null);
				} else {
					MenuManager mgr = new MenuManager();
					buildReplyDynamicMenu(adjustedAllowedButton, mgr);
					mgr.createContextMenu(replyButton).setVisible(true);
				}
			}
		};
	}

	/**
	 * @param cherryPickToRemoteBranch
	 * @return
	 */
	private SelectionAdapter cherryPickSelectionListener(Button cherryPickToRemoteBranch) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				CherryPickProcess cherryPickProcess = new CherryPickProcess(cherryPickToRemoteBranch.getShell(),
						fGerritClient, fChangeInfo, fChangeInfo.getUserSelectedRevision());
				cherryPickProcess.handleCherryPick();
			}
		};
	}

	/**
	 * @return
	 */
	private SelectionAdapter submitSelectionListener() {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				SubmitProcess submitProcess = new SubmitProcess();
				submitProcess.handleSubmit(fChangeInfo, fGerritClient);
			}
		};
	}

	/**
	 * @param fAbandon
	 * @return
	 */
	private SelectionAdapter restoreSelectionListener(Button fAbandon) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				InputDialog inputDialog = new InputDialog(fAbandon.getParent().getShell(),
						Messages.ChangeDetailEditor_16, Messages.ChangeDetailEditor_17, "", null); //$NON-NLS-1$
				if (inputDialog.open() != Window.OK) {
					return;
				}

				RestoreCommand restoreCmd = fGerritClient.restore(fChangeInfo.getId());
				RestoreInput restoreInput = new RestoreInput();
				restoreInput.setMessage(inputDialog.getValue());

				restoreCmd.setCommandInput(restoreInput);

				CompletableFuture.runAsync(() -> {
					try {
						restoreCmd.call();
					} catch (EGerritException e1) {
						EGerritCorePlugin
								.logError(fGerritClient.getRepository().formatGerritVersion() + e1.getMessage());
					}
					refreshStatus();
				}).thenRun(() -> {
					new RefreshRelatedEditors(fChangeInfo, fGerritClient).schedule();
				});
			}
		};
	}

	/**
	 * @param fAbandon
	 * @return
	 */
	private SelectionAdapter abandonSelectionListener(Button fAbandon) {
		return new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);
				InputDialog inputDialog = new InputDialog(fAbandon.getParent().getShell(),
						Messages.ChangeDetailEditor_13, Messages.ChangeDetailEditor_14, "", null); //$NON-NLS-1$
				if (inputDialog.open() != Window.OK) {
					return;
				}

				AbandonCommand abandonCmd = fGerritClient.abandon(fChangeInfo.getId());
				AbandonInput abandonInput = new AbandonInput();
				abandonInput.setMessage(inputDialog.getValue());

				abandonCmd.setCommandInput(abandonInput);

				CompletableFuture.runAsync(() -> {
					try {
						abandonCmd.call();
					} catch (EGerritException e1) {
						EGerritCorePlugin
								.logError(fGerritClient.getRepository().formatGerritVersion() + e1.getMessage());
					}
					refreshStatus();
				}).thenRun(() -> {
					new RefreshRelatedEditors(fChangeInfo, fGerritClient).schedule();
				});
			}
		};
	}

	/**
	 * @param revertButton
	 * @return
	 */
	private SelectionAdapter revertSelectionListener(Button revertButton) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean revertSuccessfull = false;
				String revertMsg = NLS.bind(Messages.Revert_message, new Object[] { fChangeInfo.getSubject(),
						fChangeInfo.getRevision().getCommit().getCommit(), fChangeInfo.get_number() });
				ChangeInfo revertResult = null;
				String revertErrorMessage = null;

				while (!revertSuccessfull) {
					final String errorMsg = revertErrorMessage;
					final InputDialog replyDialog = new InputDialog(revertButton.getShell(),
							Messages.Revert_dialog_title, Messages.Revert_dialog_message, revertMsg,
							revertErrorMessage == null ? null : new IInputValidator() {
								//Because InputDialog does not allow us to set the text w/o disabling the ok button,
								//we need to trick the dialog in displaying what we want with this counter.
								private int count = 0;

								@Override
								public String isValid(String newText) {
									if (count == 0) {
										count++;
										return errorMsg;
									} else {
										return null;
									}
								}
							}) {
						@Override
						protected int getInputTextStyle() {
							return SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP;
						}

						@Override
						protected Control createDialogArea(Composite parent) {
							Control res = super.createDialogArea(parent);
							((GridData) this.getText().getLayoutData()).heightHint = 100;
							((GridData) this.getText().getLayoutData()).widthHint = 500;
							return res;
						}
					};
					if (replyDialog.open() != Window.OK) {
						return;
					}

					RevertCommand revertCmd = fGerritClient.revert(fChangeInfo.getId());
					RevertInput revertInput = new RevertInput();
					revertMsg = replyDialog.getValue();
					revertInput.setMessage(revertMsg);
					revertCmd.setCommandInput(revertInput);

					try {
						revertResult = revertCmd.call();
						if (revertResult == null) {
							revertErrorMessage = revertCmd.getFailureReason();
						} else {
							revertSuccessfull = true;
						}
					} catch (EGerritException e3) {
						EGerritCorePlugin
								.logError(fGerritClient.getRepository().formatGerritVersion() + e3.getMessage());
						return;
					}
				}
				UIUtils.openAnotherEditor(revertResult, fGerritClient);
			}
		};
	}

	private SelectionListener rebaseButtonListener(Composite parent) {
		return new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MenuManager mgr = new MenuManager();
				mgr.add(new RebaseProcess(false, parent.getShell(), fChangeInfo, fChangeInfo.getUserSelectedRevision(),
						fGerritClient));
				mgr.add(new RebaseProcess(true, parent.getShell(), fChangeInfo, fChangeInfo.getUserSelectedRevision(),
						fGerritClient));
				mgr.createContextMenu(parent).setVisible(true);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				//Nothing to do
			}
		};
	}

	/**
	 * @param rebaseButton
	 */
	private void bindRebaseButton(Button rebaseButton) {
		{
			IObservableValue observeRebasable = EMFProperties
					.value(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION)
					.value(ModelPackage.Literals.REVISION_INFO__REBASEABLE)
					.observe(fChangeInfo);

			bindingContext.bindValue(WidgetProperties.enabled().observe(rebaseButton), observeRebasable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
		}
	}

	/**
	 * @param fAbandon
	 * @param fRestore
	 */
	private void bindRestoreButton(Button fAbandon, Button fRestore) {
		{
			IObservableValue observeRestorable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__RESTOREABLE)
					.observe(fChangeInfo);
			bindingContext.bindValue(WidgetProperties.enabled().observe(fRestore), observeRestorable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
			bindingContext.bindValue(WidgetProperties.visible().observe(fRestore), observeRestorable, null,
					new NegateBooleanConverter());

			//When restore is not enable, we need to force the abandon button to be visible
			bindingContext.bindValue(WidgetProperties.visible().observe(fAbandon), observeRestorable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new NegateBooleanConverter());

			//We need to force a redraw of the layout to remove the whitespace that would otherwise be left by the buttons being hidden
			bindingContext.bindValue(new HideControlObservable(fAbandon), observeRestorable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new NegateBooleanConverter());
			bindingContext.bindValue(new HideControlObservable(fRestore), observeRestorable, null, null);
		}
	}

	/**
	 * @param fAbandon
	 * @param fRestore
	 */
	private void bindAbandonButton(Button fAbandon, Button fRestore) {
		{
			IObservableValue observeAbandonable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__ABANDONABLE)
					.observe(fChangeInfo);
			bindingContext.bindValue(WidgetProperties.enabled().observe(fAbandon), observeAbandonable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
			bindingContext.bindValue(WidgetProperties.visible().observe(fAbandon), observeAbandonable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
			bindingContext.bindValue(WidgetProperties.visible().observe(fAbandon), observeAbandonable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new UpdateValueStrategy() {
						@Override
						public Object convert(Object value) {
							boolean state = ((Boolean) value).booleanValue();
							if (state) {
								return true;
							}
							if (fRestore.isVisible()) {
								//We want to make sure to always show the submit button even if the restore is disabled so we at least get one button
								return false;
							}
							return true;
						}
					});
		}
	}

	/**
	 * @param submitButton
	 * @param revertButton
	 */
	private void bindRevertButton(Button submitButton, Button revertButton) {
		{
			IObservableValue observeRevertable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__REVERTABLE)
					.observe(fChangeInfo);
			bindingContext.bindValue(WidgetProperties.enabled().observe(revertButton), observeRevertable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
			bindingContext.bindValue(WidgetProperties.visible().observe(revertButton), observeRevertable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);

			//When the revert is not enable, we need to force the submit button to be visible
			bindingContext.bindValue(WidgetProperties.visible().observe(submitButton), observeRevertable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new NegateBooleanConverter());

			//We need to force a redraw of the layout to remove the whitespace that would otherwise be left by the buttons being hidden
			hidableSubmitButton = new HideControlObservable(submitButton);
			bindingContext.bindValue(hidableSubmitButton, observeRevertable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new NegateBooleanConverter());
			hidableRevertButton = new HideControlObservable(revertButton);
			bindingContext.bindValue(new HideControlObservable(revertButton), observeRevertable, null, null);
		}
	}

	/**
	 * @param submitButton
	 * @param revertButton
	 */
	private void bindSubmitButton(Button submitButton, Button revertButton) {
		{
			IObservableValue observeSubmitable = EMFProperties
					.value(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION)
					.value(ModelPackage.Literals.REVISION_INFO__SUBMITABLE)
					.observe(fChangeInfo);
			bindingContext.bindValue(WidgetProperties.enabled().observe(submitButton), observeSubmitable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), null);
			bindingContext.bindValue(WidgetProperties.visible().observe(submitButton), observeSubmitable,
					new UpdateValueStrategy(UpdateValueStrategy.POLICY_NEVER), new UpdateValueStrategy() {
						@Override
						public Object convert(Object value) {
							if (value == null) {
								return false;
							}
							boolean state = ((Boolean) value).booleanValue();
							if (state) {
								return true;
							}
							if (revertButton.isVisible()) {
								return false;
							}
							return true;
						}
					});
		}
	}

	private void buildReplyDynamicMenu(Map<String, Integer> adjustedAllowedButton, MenuManager mgr) {
		mgr.add(new ReplyHandler(fChangeInfo, fGerritClient, ActionConstants.REPLY.getLiteral())); //Add the Reply option

		//Adjust the list of label which the current user can set to a maximum value
		int size = adjustedAllowedButton.size();

		//If there is some menu to add, then lets create a separator
		if (size > 0) {
			mgr.add(new Separator());
		}
		//Add all potential menu items
		Iterator<Entry<String, Integer>> iter = adjustedAllowedButton.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, Integer> entry = iter.next();
			mgr.add(new ReplyHandler(fChangeInfo, fGerritClient, entry.getKey()));
		}
		//Add a selection for all buttons when there is more than one selection
		if (size > 1) {
			mgr.add(new ReplyHandler(fChangeInfo, fGerritClient, ReplyProcess.REPLY_ALL_BUTTONS));
		}
	}

	private SelectionListener downloadButtonListener(Composite parent) {
		return new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MenuManager mgr = new MenuManager();
				mgr.add(new CherryPickRevision(fChangeInfo.getUserSelectedRevision(), fGerritClient));
				mgr.add(new CheckoutRevision(fChangeInfo.getUserSelectedRevision(), fGerritClient));
				mgr.createContextMenu(parent).setVisible(true);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				//Nothing to do
			}
		};
	}

	@Override
	public void setFocus() {
		headerSection.setFocus();
	}

	/**
	 * This is the entry point to fill the editor with the Change info and the gerrit server being used.
	 *
	 * @param GerritClient
	 *            gerritClient
	 * @param ChangeInfo
	 *            element
	 */
	private void setChangeInfo(GerritClient gerritClient, ChangeInfo element) {
		fGerritClient = gerritClient;

		if (fChangeInfo == null) {
			fChangeInfo = element;
			//Initial setting for the user selected revision
			fChangeInfo.setUserSelectedRevision(element.getRevisions().get(element.getCurrent_revision()));
		} else {
			//Refresh only potential modified fields, otherwise the data binding seems not to know which fields are modified
			fChangeInfo.set_number(element.get_number());
			fChangeInfo.setChange_id(element.getChange_id());
			fChangeInfo.setStatus(element.getStatus());
			fChangeInfo.setStarred(element.isStarred());
			fChangeInfo.setCreated(element.getCreated());
			fChangeInfo.setUpdated(element.getUpdated());
			fChangeInfo.setReviewed(element.isReviewed());
			fChangeInfo.setMergeable(element.isMergeable());
			fChangeInfo.setInsertions(element.getInsertions());
			fChangeInfo.setDeletions(element.getDeletions());
			fChangeInfo.setCurrent_revision(element.getCurrent_revision());
		}
	}

	/**
	 * Refreshes the ChangeEditor
	 */
	public void refreshStatus() {
		//Force change the updated timestamp to cause a full reload to happen
		loader.reload(true);
	}

	private void headerSectionDataBindings() {
		//Show id
		IObservableValue idObservable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__NUMBER)
				.observe(fChangeInfo);
		NumberFormat numberFormat = NumberFormat.getInstance();
		numberFormat.setGroupingUsed(false);
		bindingContext.bindValue(WidgetProperties.text().observe(shortIdData), idObservable, null,
				new UpdateValueStrategy().setConverter(NumberToStringConverter.fromInteger(numberFormat, true)));

		//Show subject
		IObservableValue subjectObservable = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__SUBJECT)
				.observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(subjectData), subjectObservable, null, null);

		fChangeInfo.eAdapters().add(getSubjectListener());

		//Show status
		IObservableValue statusObserveValue = EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__STATUS)
				.observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(statusData), statusObserveValue, null, null);
	}

	private EContentAdapter getSubjectListener() {
		subjectListener = new EContentAdapter() {
			@Override
			public void notifyChanged(Notification msg) {
				if (msg.getFeature() == null) {
					return;
				}
				if (msg.getFeature().equals(ModelPackage.Literals.CHANGE_INFO__SUBJECT)) {
					Display.getDefault().asyncExec(() -> setPartName(fChangeInfo.getSubject()));
				}
			}
		};
		return subjectListener;
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		// ignore
	}

	@Override
	public void doSaveAs() {
		// ignore
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if (!(input instanceof ChangeDetailEditorInput)) {
			logger.debug("Input is not a ChangeDetailEditorInput"); //$NON-NLS-1$
		}
		setSite(site);
		setInput(input);
		setChangeInfo(((ChangeDetailEditorInput) input).getClient(), ((ChangeDetailEditorInput) input).getChange());
	}

	@Override
	public boolean isDirty() {
		// ignore
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		// ignore
		return false;
	}

	@Override
	public void dispose() {
		if (checkForUpdates != null) {
			checkForUpdates.cancel();
		}
		loader.dispose();
		fChangeInfo.eAdapters().remove(subjectListener);
		observableCollector.dispose();
		bindingContext.dispose();
		deleteDraftRevision.dispose();
		patchSetSelector.dispose();
		hidableRevertButton.dispose();
		hidableSubmitButton.dispose();
		historytab.dispose();
		messageTab.dispose();
		detailsTab.dispose();
	}

	//Job that checks if the review has changed on the server
	private class CheckForUpdate extends Job {
		public CheckForUpdate() {
			super(NLS.bind(Messages.RefreshJobName, fChangeInfo.get_number()));
			setSystem(true);
			setPriority(SHORT);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			if (monitor.isCanceled()) {
				return Status.OK_STATUS;
			}
			QueryHelpers.loadBasicInformation(fGerritClient, fChangeInfo, false);
			this.schedule(REFRESH_RATE);
			return Status.OK_STATUS;
		}
	}
}