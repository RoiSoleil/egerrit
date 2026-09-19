/*******************************************************************************
 * Copyright (c) 2015-2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jacques Bouthillier - Initial Implementation of the plug-in utility
 ******************************************************************************/

package org.eclipse.egerrit.internal.ui.utils;

import java.text.SimpleDateFormat;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.GerritServerInformation;
import org.eclipse.egerrit.internal.core.command.CreateDraftCommand;
import org.eclipse.egerrit.internal.core.command.SetReviewCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.ReviewInput;
import org.eclipse.egerrit.internal.core.utils.Utils;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ChangeMessageInfo;
import org.eclipse.egerrit.internal.model.CommentInfo;
import org.eclipse.egerrit.internal.model.FileInfo;
import org.eclipse.egerrit.internal.model.ModelFactory;
import org.eclipse.egerrit.internal.model.ModelHelpers;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.EGerritUIPlugin;
import org.eclipse.egerrit.internal.ui.compare.CommentPrettyPrinter;
import org.eclipse.egerrit.internal.ui.editors.ChangeDetailEditor;
import org.eclipse.egerrit.internal.ui.editors.OpenCompareEditor;
import org.eclipse.egerrit.internal.ui.editors.QueryHelpers;
import org.eclipse.egerrit.internal.ui.editors.ReplyDialog;
import org.eclipse.egerrit.internal.ui.editors.model.ChangeDetailEditorInput;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.osgi.framework.Version;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the Gerrit UI utility.
 *
 * @since 1.0
 */

public class UIUtils {

	private static Logger logger = LoggerFactory.getLogger(UIUtils.class);

	private static final String EGERRIT_PREF = "org.eclipse.egerrit.prefs"; //$NON-NLS-1$

	private static final int TITLE_LENGTH = 75;

	/**
	 * The default constructor. Do not allow to build an object of this class
	 */
	private UIUtils() {
	}

	/**
	 * To display some information to the end-user
	 *
	 * @param title
	 * @param message
	 */
	public static void displayInformation(final String title, final String message) {
		PlatformUI.getWorkbench().getDisplay().syncExec(() -> MessageDialog.openInformation(null, title, message));
	}

	/**
	 * Compute the size of the Fonts used for this composite
	 *
	 * @param composite
	 * @return Point
	 */
	public static Point computeFontSize(Composite composite) {
		GC gc = new GC(composite);
		FontMetrics fm = gc.getFontMetrics();
		int charWidth = fm.getAverageCharWidth();

		gc.dispose();
		return new Point(charWidth, fm.getHeight());
	}

	/**
	 * Remove the link argument and return the text
	 *
	 * @return String
	 */
	public static String getLinkText(String text) {
		text = text.replaceFirst("<a>", ""); //$NON-NLS-1$ //$NON-NLS-2$
		text = text.replaceFirst("</a>", ""); //$NON-NLS-1$//$NON-NLS-2$
		return text.trim();
	}

	public static void replyToChange(Shell shell, RevisionInfo revisionInfo, String reason, GerritClient client,
			boolean waitForDataRefresh, ChangeMessageInfo messageInfo) {
		String current = revisionInfo.getId();
		QueryHelpers.loadDrafts(client, revisionInfo); //Force load the drafts to make sure they are shown in the dialog.
		final ReplyDialog replyDialog = new ReplyDialog(shell, reason, revisionInfo, client, messageInfo);
		Display.getDefault().syncExec(() -> {
			int ret = replyDialog.open();
			if (ret == IDialogConstants.OK_ID) {
				//Fill the data structure for the reply
				ReviewInput reviewInput = new ReviewInput();
				reviewInput.setMessage(replyDialog.getValue());
				reviewInput.setLabels(replyDialog.getRadiosSelection());
				reviewInput.setDrafts(ReviewInput.DRAFT_PUBLISH);

				CompletableFuture<Void> reloading = CompletableFuture.runAsync(() -> {
					try {
						SetReviewCommand reviewToEmit = client.setReview(revisionInfo.getChangeInfo().getId(), current);
						reviewToEmit.setCommandInput(reviewInput);
						reviewToEmit.call();
					} catch (EGerritException e1) {
						EGerritCorePlugin.logError(client.getRepository().formatGerritVersion() + e1.getMessage());

					}
				}).thenRun(() -> {
					//Here we don't use the model loader because we need the re-loading of the comments to be done synchronously
					QueryHelpers.loadBasicInformation(client, revisionInfo.getChangeInfo(), false);
				});
				if (waitForDataRefresh) {
					reloading.join();
				}
			}
		});
	}

	public static String revisionToString(RevisionInfo revisionInfo) {
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toString(revisionInfo.get_number())); //
		sb.append("   "); //$NON-NLS-1$
		appendCommitTime(sb, revisionInfo);
		sb.append("   "); //$NON-NLS-1$
		appendUserCommiter(sb, revisionInfo);
		if (revisionInfo.isDraft()) {
			sb.append(Messages.UIUtils_0);
		}
		return sb.toString();
	}

	public static void postReply(GerritClient gerritClient, CommentInfo comment, String reply, String revisionId) {
		if (reply.trim().length() == 0) {
			return;
		}
		CreateDraftCommand publishDraft = gerritClient
				.createDraftComments(ModelHelpers.getRevision(comment).getChangeInfo().getId(), revisionId);

		CommentInfo replyData = ModelFactory.eINSTANCE.createCommentInfo();
		replyData.setMessage(reply);
		replyData.setInReplyTo(comment.getId());
		replyData.setLine(comment.getLine());
		replyData.setSide(comment.getSide());
		replyData.setPath(ModelHelpers.getFileInfo(comment).getPath());
		publishDraft.setCommandInput(replyData);
		try {
			ModelHelpers.getFileInfo(comment).getDraftComments().add(publishDraft.call());
		} catch (EGerritException e) {
			EGerritCorePlugin.logError(gerritClient.getRepository().formatGerritVersion() + e.getMessage());
		}
	}

	private static void appendCommitTime(StringBuilder sb, RevisionInfo revisionInfo) {
		if (revisionInfo.getCommit() != null) {
			sb.append(Utils.formatDate(revisionInfo.getCommit().getCommitter().getDate(),
					new SimpleDateFormat("MMM dd, yyyy hh:mm a"))); //$NON-NLS-1$
		}
	}

	private static void appendUserCommiter(StringBuilder sb, RevisionInfo revisionInfo) {
		if (revisionInfo.getCommit() != null) {
			if (revisionInfo.getCommit().getAuthor() != null) {
				//Use the Author
				sb.append(revisionInfo.getCommit().getAuthor().getName());
			}
			if (!revisionInfo.getCommit()
					.getAuthor()
					.getName()
					.equals(revisionInfo.getCommit().getCommitter().getName())) {
				//Add the committer if different than the Author
				sb.append("/"); //$NON-NLS-1$
				sb.append(revisionInfo.getCommit().getCommitter().getName());
			}
		}
	}

	public static String formatMessageForMarkerView(CommentInfo commentInfo, int newPosition) {
		String psString = getPatchSetString(commentInfo);
		String author = commentInfo.getAuthor() != null ? commentInfo.getAuthor().getName() : null;
		String message = commentInfo.getMessage();
		boolean lineDeleted = newPosition < 0;
		String formattedMessage;
		if (author == null) {
			//This is a draft
			formattedMessage = NLS.bind(Messages.UIUtils_draft,
					new String[] { message, CommentPrettyPrinter.printDate(commentInfo), psString });
		} else {
			formattedMessage = NLS.bind(Messages.UIUtils_comment,
					new String[] { message, author, CommentPrettyPrinter.printDate(commentInfo), psString });
		}
		if (lineDeleted) {
			return formattedMessage + NLS.bind(Messages.UIUtils_lineDeleted, commentInfo.getLine());
		}
		return formattedMessage;
	}

	public static String formatMessageForQuickFix(CommentInfo commentInfo) {
		String message = commentInfo.getMessage();
		if (message.length() < 20) {
			return message;
		}
		int nextSpace = message.indexOf(' ', 20);
		if (nextSpace == -1) {
			return message;
		}
		return message.substring(0, nextSpace) + " ..."; //$NON-NLS-1$
	}

	public static String getPatchSetString(CommentInfo comment) {
		return getPatchSetString(ModelHelpers.getRevision(comment));
	}

	public static String getPatchSetString(RevisionInfo revision) {
		return revision.get_number() + "/" //$NON-NLS-1$
				+ ModelHelpers.getHighestRevisionNumber(revision.getChangeInfo().getRevisions().values());
	}

	/**
	 * Open a compare editor against the another version of the file
	 *
	 * @param key
	 * @param gerritClient
	 * @param fileInfo
	 * @param changeInfo
	 * @param leftSide
	 */
	public static void open(GerritClient gerritClient, FileInfo fileInfo, ChangeInfo changeInfo, String leftSide) {
		OpenCompareEditor compareEditor = new OpenCompareEditor(gerritClient, changeInfo);
		compareEditor.compareFiles(leftSide, fileInfo.getRevision().getId(), fileInfo);
	}

	/**
	 * Open a file in a single editor
	 *
	 * @param key
	 * @param revInfo
	 * @param line
	 * @return
	 */
	public static boolean openSingleFile(FileInfo fileToShow, GerritClient gerritClient, RevisionInfo revInfo,
			int line) {
		if (fileToShow == null) {
			return false;
		}
		IFile workspaceFile = new OpenCompareEditor(gerritClient, revInfo.getChangeInfo())
				.getCorrespondingWorkspaceFile(fileToShow);

		return openSingleFile(workspaceFile, line);
	}

	/**
	 * Open a file of a change in a single editor, using its path in the repository.
	 *
	 * @param filePath
	 *            the path of the file in the repository
	 * @param gerritClient
	 *            the client
	 * @param revInfo
	 *            the revision the file belongs to
	 * @param line
	 *            the line to reveal
	 * @return <code>true</code> if the file could be opened
	 */
	public static boolean openSingleFile(String filePath, GerritClient gerritClient, RevisionInfo revInfo, int line) {
		if (filePath == null || revInfo == null) {
			return false;
		}
		IFile workspaceFile = new OpenCompareEditor(gerritClient, revInfo.getChangeInfo())
				.getCorrespondingWorkspaceFile(filePath);

		return openSingleFile(workspaceFile, line);
	}

	public static boolean openSingleFile(IResource resource, int line) {
		IFile workspaceFile = (resource instanceof IFile) ? (IFile) resource : null;
		if (workspaceFile == null) {
			return false;
		}
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		try {
			IEditorPart editor = IDE.openEditor(page, workspaceFile);
			if (editor != null && line != 0) {
				selectLine(editor, line);
			}
		} catch (PartInitException e1) {
			logger.debug("Could not open the editor for " + workspaceFile.getFullPath(), e1); //$NON-NLS-1$
		}
		return true;
	}

	private static void selectLine(IEditorPart editorPart, int selectedLine) {
		if (editorPart instanceof ITextEditor) {
			ITextEditor editor = (ITextEditor) editorPart;
			IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
			if (document != null) {
				try {
					IRegion line = document.getLineInformation(selectedLine);
					editor.selectAndReveal(line.getOffset(), 0);
				} catch (BadLocationException e) {
					// line seems not to exist in
					// workspace version
				}
			}
		}
	}

	/**
	 * Show an EGerrit tip to the end-user until the toggle is selected
	 *
	 * @param key
	 * @param shell
	 * @param title
	 * @param value
	 */
	public static void showDialogTip(String key, Shell shell, String title, String value, String note) {
		Preferences prefs = ConfigurationScope.INSTANCE.getNode(EGERRIT_PREF);

		Preferences editorPrefs = prefs.node(key);
		boolean choice = editorPrefs.getBoolean(key, false);

		if (choice) {
			if (note != null) {
				//always show a dialog with the note here
				MessageDialog.openInformation(shell, Messages.UIUtils_EGerriNoteTitle, note);
			}
			return;
		}

		//Keep the title length to TITLE_LENGTH characters max
		if (title.length() > TITLE_LENGTH) {
			title = title.substring(0, TITLE_LENGTH - 3).concat("..."); //$NON-NLS-1$
		}

		if (note != null) {
			value = value + "\n\n" + note; //$NON-NLS-1$
		}

		MessageDialogWithToggle dialog = MessageDialogWithToggle.openInformation(shell, title, value,
				Messages.UIUtils_EGerriTipShowAgain, false, null, null);

		if (dialog.getToggleState()) {
			editorPrefs.putBoolean(key, true);
			try {
				editorPrefs.flush();
			} catch (BackingStoreException e) {
				//There is not much we can do
			}
		}
		return;
	}

	/**
	 * Show a dialog allowing the user to by-pass the dialogue when renaming a branch is available until the toggle is
	 * selected
	 *
	 * @param key
	 * @param shell
	 * @param title
	 * @param value
	 * @return
	 */
	public static int renameBranch(String key, Shell shell, String title, String message) {
		Preferences prefs = ConfigurationScope.INSTANCE.getNode(EGERRIT_PREF);

		Preferences editorPrefs = prefs.node(key);
		boolean choice = editorPrefs.getBoolean(key, false);

		if (choice) {
			return IDialogConstants.YES_ID;
		}

		MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoQuestion(shell, title, message,
				Messages.UIUtils_EGerriTipRenameShowAgain, false, null, null);

		if (dialog.getToggleState()) {
			editorPrefs.putBoolean(key, true);
			try {
				editorPrefs.flush();
			} catch (BackingStoreException e) {
				//There is not much we can do
			}
		}
		return dialog.getReturnCode();
	}

	/*********************************************/

	/**
	 * Open editor with the newest changeInfo
	 *
	 * @param changeInfo
	 * @param gerritClient
	 */
	public static void openAnotherEditor(ChangeInfo changeInfo, GerritClient fGerritClient) {
		IWorkbench workbench = EGerritUIPlugin.getDefault().getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		IWorkbenchPage page = null;
		if (window != null) {
			page = workbench.getActiveWorkbenchWindow().getActivePage();
		}

		if (page != null) {
			try {
				IEditorInput input = new ChangeDetailEditorInput(fGerritClient, changeInfo);
				IEditorPart reusedEditor = page.findEditor(input);
				page.openEditor(input, ChangeDetailEditor.EDITOR_ID);
				if (reusedEditor instanceof ChangeDetailEditor) {
					((ChangeDetailEditor) reusedEditor).refreshStatus();
				}
			} catch (PartInitException e) {
				EGerritCorePlugin.logError(fGerritClient != null
						? fGerritClient.getRepository().formatGerritVersion() + e.getMessage()
						: e.getMessage());
			}
		}
	}

	/**
	 * Create a acronym label based on the provided label
	 *
	 * @param label
	 * @return
	 */
	public static String getAcronymLabel(String label) {
		String ret = ""; //$NON-NLS-1$
		if (!label.isEmpty()) {
			String[] arraySt = label.split("-"); //$NON-NLS-1$
			for (String s : arraySt) {
				ret = ret.concat(s.substring(0, 1));
			}
		}
		return ret;
	}

	/**
	 * Build the URL of a change in the web UI of the Gerrit server.
	 *
	 * @param serverInfo
	 *            the Gerrit server information
	 * @param version
	 *            the version of the Gerrit server
	 * @param changeInfo
	 *            the change
	 * @return the URL of the change in the web UI of the server
	 */
	public static String buildChangeWebUIUrl(GerritServerInformation serverInfo, Version version,
			ChangeInfo changeInfo) {
		String serverUri = serverInfo != null ? serverInfo.getServerURI() : ""; //$NON-NLS-1$
		if (serverUri.endsWith("/")) { //$NON-NLS-1$
			serverUri = serverUri.substring(0, serverUri.length() - 1);
		}
		if (version != null && version.getMajor() >= 3) {
			//Gerrit 3.x web UI URL: http://host/c/<project>/+/<change number>
			return serverUri + "/c/" + changeInfo.getProject() + "/+/" + changeInfo.get_number(); //$NON-NLS-1$ //$NON-NLS-2$
		}
		//Gerrit 2.x web UI URL: http://host/#/c/<change number>/
		return serverUri + "/#/c/" + changeInfo.get_number() + "/"; //$NON-NLS-1$ //$NON-NLS-2$
	}

}
