/*******************************************************************************
 * Copyright (c) 2017 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.process;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egit.core.op.CreateLocalBranchOperation;
import org.eclipse.egit.core.op.DeleteBranchOperation;
import org.eclipse.egit.core.op.RebaseOperation;
import org.eclipse.egit.core.op.StashApplyOperation;
import org.eclipse.egit.core.op.StashCreateOperation;
import org.eclipse.egit.ui.internal.fetch.FetchOperationUI;
import org.eclipse.egit.ui.internal.staging.StagingView;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.TeamException;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * This class manages the process for automatically rebasing locally (from inside Eclipse) when the remote Gerrit server
 * cannot do it. This will then stash the current changes, fetch the branch associated with the change, checkout the
 * change's branch and rebase it on the correct branch.
 */
class AutoRebaseProcess extends Job {
	private Repository localRepo;

	private RevisionInfo revisionInfo;

	private GerritClient gerritClient;

	private String dateNowBranchFormat;

	private String branchBeforeAutoRebase = ""; //$NON-NLS-1$

	private RevCommit stashRefCommit = null;

	private String tempName;

	private ChangeInfo baseChange;

	private final String DOUBLE_LINE_FEED = "\r\r"; //$NON-NLS-1$

	private enum AutoRebaseStep {
		NONE, FETCH_PATCHSET, STASH_CHANGES, CREATE_BRANCH, CHECKOUT_BRANCH, FETCH_BRANCH, REBASE_INIT,
	};

	/**
	 * Initializes the process with the needed information
	 *
	 * @param gerritClient
	 *            Gerrit client to retrieve information on the local repository
	 * @param localRepo
	 *            Local git repo on which to run the rebase command
	 * @param revisionInfo
	 *            Current gerrit revision on which to execute the rebase
	 */
	AutoRebaseProcess(GerritClient gerritClient, Repository localRepo, RevisionInfo revisionInfo,
			ChangeInfo baseChange) {
		super(Messages.AutoRebaseProcess_AutomaticallyRebasing);
		this.gerritClient = gerritClient;
		this.localRepo = localRepo;
		this.revisionInfo = revisionInfo;
		this.baseChange = baseChange;
		DateFormat dateFormat = new SimpleDateFormat("yy/MM/dd.HH-mm-ss"); //$NON-NLS-1$
		Date date = new Date();
		this.dateNowBranchFormat = dateFormat.format(date);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {

		AutoRebaseStep lastSuccessfulStep = AutoRebaseStep.NONE;
		SubMonitor subMonitor = SubMonitor.convert(monitor, 6);

		/* Abort the automatic rebase if the current state of the repo doesn't allow checking out (already rebasing for instance) */
		if (!isRebaseable()) {
			RepositoryState currentState = localRepo.getRepositoryState();
			if (currentState == RepositoryState.REBASING || currentState == RepositoryState.REBASING_MERGE
					|| currentState == RepositoryState.REBASING_INTERACTIVE) {
				Display.getDefault().syncExec(new Runnable() {
					public void run() {
						MessageDialog.openError(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
								Messages.AutoRebaseProcess_RebaseCancelled,
								Messages.AutoRebaseProcess_FailedRepoFollowingState
										+ localRepo.getRepositoryState().getDescription() + DOUBLE_LINE_FEED
										+ Messages.AutoRebaseProcess_PleaseFinishOrCancelCurrentRebase);

						try {
							openStagingView();
						} catch (PartInitException e) {
							EGerritCorePlugin.logError(e.getMessage());
						}
					}
				});
				return Status.OK_STATUS;
			} else {
				Display.getDefault().syncExec(new Runnable() {

					public void run() {
						MessageDialog.openError(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
								Messages.AutoRebaseProcess_RebaseCancelled,
								Messages.AutoRebaseProcess_FailedRepoFollowingState
										+ localRepo.getRepositoryState().getDescription() + DOUBLE_LINE_FEED);
					}
				});
				return Status.OK_STATUS;
			}

		}

		/* Get current branch (in case of rollback needed */
		try {
			branchBeforeAutoRebase = localRepo.getBranch();
		} catch (

		IOException e) {
			EGerritCorePlugin.logError(e.getMessage());
			return Status.OK_STATUS;
		}

		RevCommit commit = fetchRemoteChange(subMonitor);

		if (commit == null) {
			/* Second fetch failed, nothing to rollback */
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.FETCH_PATCHSET;

		tempName = "automatic-rebase-" + revisionInfo.getRef().toString().substring(13) + "-" //$NON-NLS-1$ //$NON-NLS-2$
				+ dateNowBranchFormat;

		if (!stashCurrentChanges(subMonitor)) {
			/* Stash failed, nothing to rollback */
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.STASH_CHANGES;

		if (!createLocalBranch(subMonitor, commit)) {
			/* Create branch failed, must rollback (stash pop) */
			try {
				handleFailure(lastSuccessfulStep, subMonitor);
			} catch (Exception e) {
				EGerritCorePlugin.logError(e.getMessage());
			}
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.CREATE_BRANCH;

		if (!checkoutCreatedBranch(subMonitor)) {
			/* Checkout branch failed, must rollback (delete newly created branch, stash pop) */
			try {
				handleFailure(lastSuccessfulStep, subMonitor);
			} catch (Exception e) {
				EGerritCorePlugin.logError(e.getMessage());
			}
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.CHECKOUT_BRANCH;

		if (!fetchRemoteBranch(subMonitor)) {
			/* Second fetch failed, must rollback (delete newly created branch, stash pop) */
			try {
				handleFailure(lastSuccessfulStep, subMonitor);
			} catch (Exception e) {
				EGerritCorePlugin.logError(e.getMessage());
			}
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.FETCH_BRANCH;

		if (!rebaseLocally(subMonitor)) {
			/* Rebase branch failed, must rollback (checkout first branch, delete newly created branch, stash pop) */
			try {
				handleFailure(lastSuccessfulStep, subMonitor);
			} catch (Exception e) {
				EGerritCorePlugin.logError(e.getMessage());
			}
			return Status.OK_STATUS;
		}

		lastSuccessfulStep = AutoRebaseStep.REBASE_INIT;
		return Status.OK_STATUS;

	}

	/**
	 * Rollback changes made during the process in case of failure i.e unstash changes if changes were stashed, delete
	 * branch if a branch was created etc.
	 *
	 * @param lastSuccessfulStep
	 *            Last step that was successful before the failure
	 */
	private void handleFailure(AutoRebaseStep lastSuccessfulStep, IProgressMonitor monitor)
			throws CoreException, IOException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException,
			CheckoutConflictException, GitAPIException {

		monitor.setTaskName(Messages.AutoRebaseProcess_RevertingSteps);
		/* Rollback steps depending on the last successful step */
		switch (lastSuccessfulStep) {
		case STASH_CHANGES: {
			/* Unstash changes */
			if (stashRefCommit != null) {
				StashApplyOperation stashApplyOp = new StashApplyOperation(localRepo, stashRefCommit);
				stashApplyOp.execute(monitor);
			}
		}
			break;
		case CREATE_BRANCH: {
			/* Delete branch */
			Ref branch = null;

			branch = localRepo.findRef(tempName);
			DeleteBranchOperation delBranchOp = new DeleteBranchOperation(localRepo, branch, true);
			delBranchOp.execute(monitor);

			/* Unstash changes */
			if (stashRefCommit != null) {
				StashApplyOperation stashApplyOp = new StashApplyOperation(localRepo, stashRefCommit);
				stashApplyOp.execute(monitor);
			}
		}
			break;
		case CHECKOUT_BRANCH:
		case FETCH_BRANCH: {
			/* Checkout old branch */
			CheckoutCommand co = null;

			Git git = new Git(localRepo);
			co = git.checkout();
			co.setName(branchBeforeAutoRebase);
			co.call();
			git.close();

			/* Delete branch */
			Ref branch = null;
			branch = localRepo.findRef(tempName);

			DeleteBranchOperation delBranchOp = new DeleteBranchOperation(localRepo, branch, true);

			delBranchOp.execute(monitor);

			/* Unstash changes */
			if (stashRefCommit != null) {
				StashApplyOperation stashApplyOp = new StashApplyOperation(localRepo, stashRefCommit);
				stashApplyOp.execute(monitor);
			}
		}
			break;
		case REBASE_INIT: {
			/* Abort the rebase operation */
			RebaseOperation abortOp = new RebaseOperation(localRepo, Operation.ABORT);
			abortOp.execute(monitor);

			/* Checkout old branch */
			CheckoutCommand co = null;

			Git git = new Git(localRepo);
			co = git.checkout();
			co.setName(branchBeforeAutoRebase);
			co.call();
			git.close();

			/* Delete branch */
			Ref branch = null;
			branch = localRepo.findRef(tempName);

			DeleteBranchOperation delBranchOp = new DeleteBranchOperation(localRepo, branch, true);

			delBranchOp.execute(monitor);

			/* Unstash changes */
			if (stashRefCommit != null) {
				StashApplyOperation stashApplyOp = new StashApplyOperation(localRepo, stashRefCommit);
				stashApplyOp.execute(monitor);
			}
		}
			break;
		}
		monitor.done();
	}

	/**
	 * Initiates the local rebase
	 *
	 * @return True if the rebase operation was successfully started
	 */
	private boolean rebaseLocally(SubMonitor subMonitor) {
		/* Rebase the recently checked out branch on top of the remote branch (which is currently up-to-date thanks
		 * to the earlier fetch */
		subMonitor.setTaskName(Messages.AutoRebaseProcess_RebasingLocally);
		RebaseResult rebaseResult = null;
		try {
			rebaseResult = rebase(subMonitor);
		} catch (CoreException | IOException e) {
			EGerritCorePlugin.logError(e.getMessage());
			return false;
		}

		subMonitor.worked(1);

		CustomRebaseStatusDialog customRebaseStatus = new CustomRebaseStatusDialog(rebaseResult);

		Display.getDefault().syncExec(customRebaseStatus);

		return customRebaseStatus.isSuccess();
	}

	/**
	 * Checkout the newly created unique branch
	 *
	 * @return True if the checkout was successful
	 */
	private boolean checkoutCreatedBranch(SubMonitor subMonitor) {
		/* Checkout the newly created local branch */
		subMonitor.setTaskName(Messages.AutoRebaseProcess_CheckoutNewlyCreatedLocalBranch);

		CheckoutCommand co = null;

		Git git = new Git(localRepo);
		co = git.checkout();
		co.setName(tempName);

		try {
			co.call();
		} catch (GitAPIException e) {
			EGerritCorePlugin.logError(e.getMessage());
			git.close();
			return false;
		}

		if (co.getResult().getStatus() == CheckoutResult.Status.OK) {
			subMonitor.worked(1);
		} else {
			git.close();
			return false;
		}
		git.close();

		return true;
	}

	/**
	 * Creates a unique local branch for the rebase operation
	 *
	 * @return True if the branch operation was successful
	 */
	private boolean createLocalBranch(SubMonitor subMonitor, RevCommit commit) {
		/* Create a new local branch based on this patch with a unique identifier (current epoch time) */
		subMonitor.setTaskName(Messages.AutoRebaseProcess_CreatingNewLocalBranch);

		CreateLocalBranchOperation bop = new CreateLocalBranchOperation(localRepo, tempName, commit);
		try {
			bop.execute(subMonitor);
		} catch (CoreException e) {
			EGerritCorePlugin.logError(e.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * Stashes the current changes on the repo
	 *
	 * @param subMonitor
	 * @return True if the stash operation was successful (whether a stash was created or not)
	 */
	private boolean stashCurrentChanges(SubMonitor subMonitor) {
		subMonitor.setTaskName(Messages.AutoRebaseProcess_StashingCurrentChanges);
		try {
			stashRefCommit = stashChanges(subMonitor, tempName);
		} catch (TeamException e) {
			Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
					MessageDialog.open(MessageDialog.INFORMATION, null, Messages.AutoRebaseProcess_AutoRebaseFailed,
							Messages.AutoRebaseProcess_AutoRebaseCouldNotStartFixRepo, SWT.NONE);

				}
			});
			return false;
		} catch (CoreException e) {
			EGerritCorePlugin.logError(e.getMessage());
			return false;
		}
		subMonitor.worked(1);
		return true;
	}

	/**
	 * Fetches the revision commit associated with the remote change.
	 *
	 * @param subMonitor
	 * @return RevCommit associated with the remote change.
	 */
	private RevCommit fetchRemoteChange(SubMonitor subMonitor) {
		/* Fetch objects and refs from the selected review patch set */
		subMonitor.setTaskName(Messages.AutoRebaseProcess_FetchingRemotePatchSet);
		List<RefSpec> patchSpecs = new ArrayList<>(1);
		try {
			patchSpecs.add(getSourceRefSpec());
		} catch (IOException e1) {
			EGerritCorePlugin.logError(e1.getMessage());
		}

		FetchResult fetchRes = null;
		try {
			URIish uri = getRemoteURI();
			if (uri != null) {
				fetchRes = new FetchOperationUI(localRepo, uri, patchSpecs, false).execute(subMonitor);
			} else {
				return null;
			}
		} catch (CoreException e) {
			EGerritCorePlugin.logError(e.getMessage());
		}

		if (fetchRes == null) {
			return null;
		}

		RevCommit commit = null;
		try (RevWalk rw = new RevWalk(localRepo)) {
			try {
				commit = rw.parseCommit(fetchRes.getAdvertisedRef(getSourceRefSpec().getSource()).getObjectId());
			} catch (IOException e) {
				EGerritCorePlugin.logError(e.getMessage());
			}
		}

		if (commit == null) {
			return null;
		}

		subMonitor.worked(1);
		return commit;
	}

	/**
	 * Fetch the remote branch on which the change is being applied (so that the rebase can be up-to-date)
	 *
	 * @param subMonitor
	 * @return true if the operation succeeded, false otherwise.
	 */
	private boolean fetchRemoteBranch(SubMonitor subMonitor) {
		/* Do remote tasks before local ones */
		/* Fetch remote branches to make sure that the remote branch is up to date */
		subMonitor.setTaskName(Messages.AutoRebaseProcess_FetchingRemoteBranch);
		List<RefSpec> branchSpecs = new ArrayList<>(1);
		RefSpec refSpecBranch;
		if (baseChange == null) {
			refSpecBranch = new RefSpec().setSource("refs/heads/" + revisionInfo.getChangeInfo().getBranch()) //$NON-NLS-1$
					.setDestination(Constants.FETCH_HEAD);

		} else {
			refSpecBranch = new RefSpec().setSource(baseChange.getRevision().getRef())
					.setDestination(Constants.FETCH_HEAD);
		}

		branchSpecs.add(refSpecBranch);
		FetchResult fetchResBranch = null;

		try {
			URIish uri = getRemoteURI();
			if (uri != null) {
				fetchResBranch = new FetchOperationUI(localRepo, uri, branchSpecs, false).execute(subMonitor);
			} else {
				return false;
			}
		} catch (CoreException e) {
			EGerritCorePlugin.logError(e.getMessage());
		}

		if (fetchResBranch == null) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Returns true if the repo on which the AutoRebase process is running can currently be rebased (if not in another
	 * rebase process for instance)
	 */
	private Boolean isRebaseable() {
		return localRepo.getRepositoryState().canCheckout();
	}

	/**
	 * Initiate a git rebase on the local repo
	 *
	 * @return
	 * @throws CoreException
	 * @throws IOException
	 */
	private RebaseResult rebase(IProgressMonitor monitor) throws CoreException, IOException {
		RebaseOperation operation = new RebaseOperation(localRepo, getSourceRef(), null);
		operation.execute(monitor);

		return operation.getResult();
	}

	/**
	 * Stashes the current modifications before attempting a rebase
	 *
	 * @return Stash reference commit if it worked, null otherwise (if nothing to stash for instance).
	 * @throws CoreException
	 */
	private RevCommit stashChanges(IProgressMonitor monitor, String name) throws CoreException {
		final StashCreateOperation op = new StashCreateOperation(localRepo, name);
		op.execute(monitor);
		return op.getCommit();
	}

	/**
	 * @return Source Ref for the change
	 * @throws IOException
	 */
	private Ref getSourceRef() throws IOException {
		return localRepo.findRef(Constants.FETCH_HEAD);
	}

	/**
	 * Constructs and returns the git URI for this changeset
	 *
	 * @return git URI for this changeset
	 */
	private URIish getRemoteURI() {
		try {
			return new URIish(gerritClient.getRepository().getURIBuilder(false).toString() + "/" //$NON-NLS-1$
					+ revisionInfo.getChangeInfo().getProject());
		} catch (URISyntaxException e) {
			EGerritCorePlugin.logError(e.getMessage());
		}
		return null;
	}

	/**
	 * Returns the ref specification associated with the patch set being locally rebased
	 *
	 * @return Source ref specification
	 * @throws IOException
	 */
	private RefSpec getSourceRefSpec() throws IOException {
		return new RefSpec().setSource(revisionInfo.getRef()).setDestination(Constants.FETCH_HEAD);
	}

	/**
	 * Open the "Git Staging" view and the "Git Repositories" view with the correct repository selected for the "Git
	 * Staging" view to show the correct changes.
	 *
	 * @throws PartInitException
	 */
	private void openStagingView() throws PartInitException {
		PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow()
				.getActivePage()
				.showView("org.eclipse.egit.ui.StagingView"); //$NON-NLS-1$

		IViewPart part = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow()
				.getActivePage()
				.findView("org.eclipse.egit.ui.StagingView"); //$NON-NLS-1$

		/* In order for the Staging View to show the correct repo status, we have to reload it with the correct repo */
		if (part instanceof StagingView) {
			StagingView view = (StagingView) part;

			view.reload(localRepo);
		}
	}

	private class CustomRebaseStatusDialog implements Runnable {

		private RebaseResult status;

		public CustomRebaseStatusDialog(RebaseResult rebaseResult) {
			this.status = rebaseResult;
		}

		private boolean success = false;

		public boolean isSuccess() {
			return success;
		}

		public void run() {
			String dialogMessage = ""; //$NON-NLS-1$

			/* If the automatic rebase was successful, open the Git Staging view at the end */
			if (status.getStatus() == RebaseResult.Status.STOPPED
					|| status.getStatus() == RebaseResult.Status.FAST_FORWARD
					|| status.getStatus() == RebaseResult.Status.OK) {
				try {

					String stashMessage = Messages.AutoRebaseProcess_StashingTheLocalChanges;

					if (stashRefCommit != null) {
						stashMessage = Messages.AutoRebaseProcess_StashingLocalChanges + tempName;
					}

					dialogMessage = Messages.AutoRebaseProcess_FollowingStepsSuccessful + " :\r\r" //$NON-NLS-1$
							+ "-" + stashMessage + "\r" //$NON-NLS-1$//$NON-NLS-2$
							+ "-" + Messages.AutoRebaseProcess_CheckingOutBranchToRebase + " : " + tempName + "\r" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							+ "-" + Messages.AutoRebaseProcess_FetchingTheBranches + "\r" //$NON-NLS-1$ //$NON-NLS-2$
							+ "-" + Messages.AutoRebaseProcess_StartingTheRebaseProcess + DOUBLE_LINE_FEED //$NON-NLS-1$
							+ Messages.AutoRebaseProcess_currentRebaseStatus;

					String rebaseMessage = ""; //$NON-NLS-1$
					switch (status.getStatus()) {
					case STOPPED:
						rebaseMessage = Messages.AutoRebaseProcess_StoppedConflict;
						break;
					case OK:
						rebaseMessage = Messages.AutoRebaseProcess_RebaseSuccessful;
						break;
					case FAST_FORWARD:
						rebaseMessage = Messages.AutoRebaseProcess_RebaseFF;
						break;
					}

					dialogMessage += rebaseMessage + DOUBLE_LINE_FEED + Messages.AutoRebaseProcess_CommitAndPushChanges;

					MessageDialog.open(MessageDialog.INFORMATION, null, Messages.AutoRebaseProcess_AutoRebaseStarted,
							dialogMessage, SWT.NONE);

					openStagingView();
					success = true;
				} catch (PartInitException e) {
					EGerritCorePlugin.logError(e.getMessage());
				}
			} else if (status.getStatus() == RebaseResult.Status.UP_TO_DATE) {
				dialogMessage = Messages.AutoRebaseProcess_AlreadyUpToDate;
				MessageDialog.open(MessageDialog.INFORMATION, null, Messages.AutoRebaseProcess_AlreadyUpToDateTitle,
						dialogMessage, SWT.NONE);
				success = false;
			} else {
				dialogMessage = Messages.AutoRebaseProcess_FailedRevertedBack;
				MessageDialog.open(MessageDialog.INFORMATION, null, Messages.AutoRebaseProcess_AutoRebaseStarted,
						dialogMessage, SWT.NONE);
				success = false;
			}
		}
	}
}