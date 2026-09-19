/*******************************************************************************
 * Copyright (c) 2016 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.process;

import java.util.concurrent.CompletableFuture;

import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.ChangeOption;
import org.eclipse.egerrit.internal.core.command.GetChangeCommand;
import org.eclipse.egerrit.internal.core.command.RebaseRevisionCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.RebaseInput;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.editors.FindLocalRepository;
import org.eclipse.egerrit.internal.ui.editors.QueryHelpers;
import org.eclipse.egerrit.internal.ui.editors.RefreshRelatedEditors;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCache;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCacheEntry;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * This class handle all aspect of the Rebase operation
 */
public class RebaseProcess extends Action {

	private Boolean rebaseLocally = false;

	private Shell shell;

	private ChangeInfo changeInfo;

	private RevisionInfo toRebase;

	private GerritClient gerritClient;

	/**
	 * The constructor.
	 */
	public RebaseProcess(Boolean rebaseLocally, Shell shell, ChangeInfo changeInfo, RevisionInfo toRebase,
			GerritClient gerritClient) {
		this.rebaseLocally = rebaseLocally;
		this.shell = shell;
		this.changeInfo = changeInfo;
		this.toRebase = toRebase;
		this.gerritClient = gerritClient;

		if (rebaseLocally) {
			this.setText(Messages.RebaseProcess_RebaseLocally);
		} else {
			this.setText(Messages.RebaseProcess_RebaseRemotely);
		}
	}

	@Override
	public void run() {
		if (rebaseLocally) {
			Repository repo;
			repo = new FindLocalRepository(gerritClient, toRebase.getChangeInfo().getProject()).getRepository();
			if (repo != null) {
				AutoRebaseProcess process = new AutoRebaseProcess(gerritClient, repo, toRebase, null);
				process.schedule();
			} else {
				showNoRepoDialog(true);
			}
		} else {

			/* If the rebase remotely button was pressed */

			InputDialog inputDialog = new RebaseInputDialog(shell, Messages.RebaseProcess_title,
					Messages.RebaseProcess_changeParent, "", null, toRebase); //$NON-NLS-1$

			if (inputDialog.open() != Window.OK) {
				return;
			}
			RebaseRevisionCommand rebaseCmd = gerritClient.rebase(changeInfo.getId(), toRebase.getId());
			RebaseInput rebaseInput = new RebaseInput();
			String base = inputDialog.getValue().trim().length() == 0 ? null : inputDialog.getValue().trim();
			rebaseInput.setBase(base);

			rebaseCmd.setCommandInput(rebaseInput);

			CompletableFuture.runAsync(() -> {
				/* Add listener for when the loadBasicInformation method will have finished updating the related changes */
				changeInfo.eAdapters().add(new EContentAdapter() {
					@Override
					public void notifyChanged(Notification msg) {
						if (msg.getFeature() == null) {
							return;
						}
						if (msg.getFeature().equals(ModelPackage.Literals.CHANGE_INFO__RELATED_CHANGES)
								&& msg.getEventType() == Notification.SET) {
							new RefreshRelatedEditors(changeInfo, gerritClient).schedule();
							/* We remove the adapter once the refresh has been started because it now has enough information to correctly
							 * refresh related opened editors */
							changeInfo.eAdapters().remove(this);
						}
					}
				});

				try {
					rebaseCmd.call();
				} catch (EGerritException e) {
					if (e.getCode() == EGerritException.SHOWABLE_MESSAGE) {
						ChangeInfo baseChange = null;
						if (base != null && base.length() > 0) {
							try {
								GetChangeCommand command = gerritClient.getChange(base);
								command.addOption(ChangeOption.CURRENT_REVISION);
								baseChange = command.call();

							} catch (EGerritException e1) {
								/* If the change number entered by the user is wrong, just show a dialog */
								if (e1.getCode() == EGerritException.SHOWABLE_MESSAGE) {
								}

								Display.getDefault().syncExec(new Runnable() {
									@Override
									public void run() {
										MessageDialog.open(MessageDialog.INFORMATION, null,
												Messages.RebaseProcess_FailedInvalidChangeNumber,
												Messages.RebaseProcess_FailedInvalidChangeNumberPleaseEnterValid,
												SWT.NONE);
									}
								});
								return;
							}
						}
						suggestLocalRebase(toRebase, gerritClient, baseChange);

					} else {
						EGerritCorePlugin.logError(gerritClient.getRepository().formatGerritVersion() + e.getMessage());
					}
				}
			}).thenRun(() -> {
				QueryHelpers.loadBasicInformation(gerritClient, changeInfo, true);
				changeInfo.setUserSelectedRevision(changeInfo.getRevision());
			});

		}
	}

	private void showNoRepoDialog(Boolean isLocal) {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				if (isLocal) {
					MessageDialog.open(MessageDialog.INFORMATION, null, Messages.RebaseProcess_TitleRebaseFailedNoRepo,
							Messages.RebaseProcess_LocalNoRepo, SWT.NONE);
				} else {
					MessageDialog.open(MessageDialog.INFORMATION, null, Messages.RebaseProcess_TitleRebaseFailedNoRepo,
							Messages.RebaseProcess_CouldNotPerformRemoteRebaseNoRepo, SWT.NONE);
				}
			}
		});
	}

	/**
	 * Suggest a local automatic rebase process to the user if the associated repository for the review is checked out
	 */
	private void suggestLocalRebase(RevisionInfo toRebase, GerritClient gerritClient, ChangeInfo baseChange) {
		Repository repo;
		repo = new FindLocalRepository(gerritClient, toRebase.getChangeInfo().getProject()).getRepository();
		if (repo != null) {
			IndexDiffCacheEntry entry = IndexDiffCache.INSTANCE.getIndexDiffCacheEntry(repo);

			RebaseRequestRunnable rebaseRequest = new RebaseRequestRunnable(entry.getIndexDiff().getModified().size());
			Display.getDefault().syncExec(rebaseRequest);

			if (rebaseRequest.getChoice() == IDialogConstants.OK_ID) {
				AutoRebaseProcess process = new AutoRebaseProcess(gerritClient, repo, toRebase, baseChange);
				process.schedule();
			}
		} else {
			showNoRepoDialog(false);
		}
	}

	/**
	 * Inner class that displays the rebase process text (number of changes to stash etc.)
	 */
	private class RebaseRequestRunnable implements Runnable {

		private int choice;

		private int numberOfChanges;

		public int getChoice() {
			return choice;
		}

		public RebaseRequestRunnable(int numberOfChanges) {
			this.numberOfChanges = numberOfChanges;
		}

		@Override
		public void run() {
			String stashString = ""; //$NON-NLS-1$
			if (numberOfChanges > 0) {
				stashString = " (" + Integer.toString(numberOfChanges) + Messages.RebaseProcess_Changes //$NON-NLS-1$
						+ Messages.RebaseProcess_ToStash;
			}

			MessageDialog result = new MessageDialog(null, Messages.RebaseProcess_AutomaticLocalRebase, null,

					Messages.RebaseProcess_notPerform + "\r\r" + Messages.RebaseProcess_WouldYouLikeAutomaticRebase //$NON-NLS-1$
							+ " : \r\r-" //$NON-NLS-1$
							+ Messages.RebaseProcess_FetchAppropriateBranches + "\r-" //$NON-NLS-1$
							+ Messages.RebaseProcess_StashLocalChanges + stashString + "\r-" //$NON-NLS-1$
							+ Messages.RebaseProcess_CreateAndCheckout + "\r-" //$NON-NLS-1$
							+ Messages.RebaseProcess_RebaseCheckedOut,
					MessageDialog.QUESTION, 0, IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL) {
				@Override
				protected int getShellStyle() {
					return super.getShellStyle() | SWT.SHEET;
				}
			};
			choice = result.open();
		}
	}

	/**
	 * Inner class for the rebase dialog
	 */
	private class RebaseInputDialog extends InputDialog {
		private RevisionInfo toRebase;

		public RebaseInputDialog(Shell parentShell, String dialogTitle, String dialogMessage, String initialValue,
				IInputValidator validator, RevisionInfo toRebase) {
			super(parentShell, dialogTitle, dialogMessage, initialValue, validator);
			this.toRebase = toRebase;
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			super.createButtonsForButtonBar(parent);
			getText().addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					if (!toRebase.isRebaseable()) {
						if (!getText().getText().isEmpty()) {
							getOkButton().setEnabled(true);
						} else {
							getOkButton().setEnabled(false);
							getOkButton().getParent().setToolTipText(toRebase.isRebaseable()
									? "" //$NON-NLS-1$
									: Messages.RebaseProcess_changeIsAlreadyUpToDate);
						}
					}
				}
			});
			getOkButton().setEnabled(toRebase.isRebaseable());
			getOkButton().getParent()
					.setToolTipText(toRebase.isRebaseable() ? "" : Messages.RebaseProcess_changeIsAlreadyUpToDate); //$NON-NLS-1$
			return;
		}
	}
}
