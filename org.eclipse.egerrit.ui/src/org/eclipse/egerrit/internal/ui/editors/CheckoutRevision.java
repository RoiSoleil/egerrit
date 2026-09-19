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

package org.eclipse.egerrit.internal.ui.editors;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.table.model.BranchMatch;
import org.eclipse.egerrit.internal.ui.utils.ActiveWorkspaceRevision;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.eclipse.egit.ui.internal.UIRepositoryUtils;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.fetch.FetchGerritChangeWizard;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RenameBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class CheckoutRevision extends Action {

	private RevisionInfo revisionCheckedOut;

	private GerritClient gerritClient;

	private ChangeInfo changeInfo;

	private String selectedBranch;

	private static final String RENAME_KEY = "branchRenameTip"; //$NON-NLS-1$

	public CheckoutRevision(RevisionInfo revision, GerritClient gerritClient) {
		this.revisionCheckedOut = revision;
		this.gerritClient = gerritClient;
		this.changeInfo = this.revisionCheckedOut.getChangeInfo();
		setText(Messages.CheckoutRevision_0);
	}

	@Override
	public void run() {
		Repository localRepo = new FindLocalRepository(gerritClient, changeInfo.getProject()).getRepository();
		boolean reActivateWorkspaceRevision = true;

		if (localRepo == null) {
			Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CheckoutRevision_1);
			ErrorDialog.openError(getShell(), Messages.CheckoutRevision_2, Messages.CheckoutRevision_3, status);
			return;
		}

		String refSelected = revisionCheckedOut.getRef();
		if ((refSelected == null) || refSelected.isEmpty()) {
			Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CheckoutRevision_4);
			ErrorDialog.openError(getShell(), Messages.CheckoutRevision_2, Messages.CheckoutRevision_3, status);
		}

		Map<String, BranchMatch> potentialBranches = findAllPotentialBranches(localRepo);
		reActivateWorkspaceRevision = selectAndCheckoutBranch(localRepo, refSelected, potentialBranches);

		//Verify if the user wants to rename the selected branch
		shouldRenameBranch(potentialBranches, refSelected, localRepo);

		if (reActivateWorkspaceRevision) {
			refreshWorkspace(localRepo);
			ActiveWorkspaceRevision.getInstance().activateCurrentRevision(gerritClient, revisionCheckedOut);
		}
	}

	/**
	 * Refresh the workspace projects located in the repository after a checkout, so that the resources are
	 * synchronized with the files modified by git.
	 *
	 * @param localRepo
	 *            the repository that has been checked out
	 */
	private void refreshWorkspace(Repository localRepo) {
		File workTree = localRepo.getWorkTree();
		if (workTree == null) {
			return;
		}
		IPath workTreePath = Path.fromOSString(workTree.getAbsolutePath());
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			IPath projectLocation = project.getLocation();
			if (projectLocation != null && project.isAccessible() && workTreePath.isPrefixOf(projectLocation)) {
				try {
					project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
				} catch (CoreException e) {
					EGerritCorePlugin.logError(e.getMessage());
				}
			}
		}
	}

	/**
	 * @param localRepo
	 * @param refSelected
	 * @param potentialBranches
	 * @return
	 */
	private boolean selectAndCheckoutBranch(Repository localRepo, String refSelected,
			Map<String, BranchMatch> potentialBranches) {
		boolean reActivateWorkspaceRevision = true;
		if (potentialBranches.size() > 1) {
			reActivateWorkspaceRevision = branchUiSelection(localRepo, potentialBranches);
		} else {
			if (potentialBranches.isEmpty()) {
				//New selected
				FetchGerritChangeWizard var = new FetchGerritChangeWizard(localRepo, refSelected);
				WizardDialog w = new WizardDialog(getShell(), var);
				w.create();
				int ret = w.open();
				if (ret == Window.CANCEL) {
					reActivateWorkspaceRevision = false;
				}
			} else if (potentialBranches.entrySet().iterator().next().getValue().equals(BranchMatch.PERFECT_MATCH)) {
				setSelectedBranch(potentialBranches.keySet().iterator().next()); //Get the only element PERFECT_MATCH
				try {
					checkoutBranch(getSelectedBranch(), localRepo);
				} catch (Exception e) {
				}
			} else {
				//Only one branch exist, but it is not the perfect match, so we need to allow the end-user to choose
				reActivateWorkspaceRevision = branchUiSelection(localRepo, potentialBranches);
			}
		}
		return reActivateWorkspaceRevision;
	}

	/**
	 * Test the selected branch match the branch to check-out and see if the user would like to rename it or not
	 *
	 * @param potentialBranches
	 * @param refSelected
	 * @param localRepo
	 */
	private void shouldRenameBranch(Map<String, BranchMatch> potentialBranches, String refSelected,
			Repository localRepo) {

		if (getSelectedBranch() == null) {
			return;
		}
		//Test to make sure it was a perfect match
		BranchMatch branchMatch = potentialBranches.get(getSelectedBranch());
		boolean isPerfect = branchMatch != null ? branchMatch.equals(BranchMatch.PERFECT_MATCH) : false;
		if (isPerfect && !getSelectedBranch().trim().isEmpty()) {
			//What would the branch name be from refspec:
			//Parse the ref from the revision to checkout
			Change revisionRef = Change.fromRef(refSelected);
			if (revisionRef != null && !revisionRef.getBranchNameLabel().contains(getSelectedBranch())) {
				//test to see if we can rename the branch or not by removing the patch set
				int lastSlash = getSelectedBranch().lastIndexOf('/');
				String perfectMatchModified = lastSlash == -1
						? getSelectedBranch()
						: getSelectedBranch().substring(0, lastSlash);

				checkBranchWithNoPatchSet(localRepo, revisionRef, perfectMatchModified);
			}
		}
	}

	/**
	 * Verify if we should allow to rename the selected branch to a branch showing the proper patch-set
	 *
	 * @param localRepo
	 * @param revisionRef
	 * @param branchNoPatchSet
	 */
	private void checkBranchWithNoPatchSet(Repository localRepo, Change revisionRef, String branchNoPatchSet) {
		if (revisionRef.getBranchNameLabel().contains(branchNoPatchSet)) {
			String newName = revisionRef.getBranchNameLabel();
			//Question whether to modify the branch name or keep the old name
			if (UIUtils.renameBranch(RENAME_KEY, Display.getDefault().getActiveShell(),
					Messages.CheckoutRevisionRenameBranchTitle,
					NLS.bind(Messages.CheckoutRevisionRenameBranch,
							new String[] { Integer.toString(revisionRef.getPatchSet()),
									Integer.toString(revisionRef.getChangeNumber()), getSelectedBranch(),
									newName })) == IDialogConstants.YES_ID) {
				renameBranch(newName, localRepo);
			}
		}
	}

	public Map<String, BranchMatch> findAllPotentialBranches(Repository localRepo) {
		Git gitRepo = new Git(localRepo);
		//Map <Key,value> = Map<Short branch name, commit id>
		Map<String, String> mapBranches = new HashMap<>();
		Map<String, BranchMatch> potentialBranches = null;
		//Map <Key,Map<keycommit, ListChangeIdvalue> = Map<Short branch name, commit id, list of changeId>
		Map<String, Map<String, List<String>>> mapBranchesChangeId = new HashMap<>();
		try (RevWalk walk = new RevWalk(localRepo)) {
			mapBranchNameWithCommitId(gitRepo, mapBranches, mapBranchesChangeId, walk);
			//Get only potential branches
			potentialBranches = mapPotentialBranch(mapBranchesChangeId);
		} catch (GitAPIException e) {
			EGerritCorePlugin.logError("find All Potential Branches()" + e.getMessage()); //$NON-NLS-1$
		}
		gitRepo.close();
		return potentialBranches;
	}

	//Build a map of all branches --> commit ID -> List of changeId
	//This goes over all the branches in the repo
	private void mapBranchNameWithCommitId(Git gitRepo, Map<String, String> mapBranches,
			Map<String, Map<String, List<String>>> mapBranchesChangeId, RevWalk walk) throws GitAPIException {
		for (Ref current : gitRepo.branchList().call()) {
			RevCommit commit;
			try {
				commit = walk.parseCommit(current.getObjectId());
				mapBranches.put(Repository.shortenRefName(current.getName()), commit.getName());

				//Map branch -> commitId -> List of changeId
				Map<String, List<String>> mapCommitChangeid = new HashMap<String, List<String>>();
				List<String> footerLines = commit.getFooterLines("Change-Id"); //$NON-NLS-1$

				mapCommitChangeid.put(commit.getName(), footerLines);
				mapBranchesChangeId.put(Repository.shortenRefName(current.getName()), mapCommitChangeid);
			} catch (IOException e) {
				EGerritCorePlugin.logError(gerritClient.getRepository().formatGerritVersion() + e.getMessage());
			}
		}
	}

	/**
	 * Open the dialog to let the user selects his options
	 *
	 * @param localRepo
	 * @param potentialBranches
	 */
	private boolean branchUiSelection(Repository localRepo, Map<String, BranchMatch> potentialBranches) {
		BranchSelectionDialog branchSelectDialog = new BranchSelectionDialog(null, potentialBranches, changeInfo,
				revisionCheckedOut);
		int result = branchSelectDialog.open();
		boolean returnOK = true;
		if (result == Window.CANCEL) {
			returnOK = false;
		}
		if (result == IDialogConstants.OK_ID) {
			//New selected
			String psSelected = revisionCheckedOut.getRef();
			FetchGerritChangeWizard var = new FetchGerritChangeWizard(localRepo, psSelected);
			WizardDialog w = new WizardDialog(getShell(), var);
			w.create();
			int retCode = w.open();
			if (retCode == Window.CANCEL) {
				returnOK = false;
			}
		} else if (result == IDialogConstants.CLIENT_ID) { // SWITCH
			try {
				setSelectedBranch(branchSelectDialog.getSelectedBranch());
				if (getSelectedBranch() != null) {
					checkoutBranch(getSelectedBranch(), localRepo);
				}
			} catch (Exception e) {
			}
		}
		return returnOK;
	}

	//Filter the branches that need to be shown and indicate the level of matching for these branches
	private Map<String, BranchMatch> mapPotentialBranch(Map<String, Map<String, List<String>>> mapBranchesChangeId) {
		Map<String, BranchMatch> mapBranches = new TreeMap<>();
		String defaultBranchName = changeInfo.get_number() + "/" //$NON-NLS-1$
				+ revisionCheckedOut.get_number();
		Iterator<Entry<String, Map<String, List<String>>>> iterBranch = mapBranchesChangeId.entrySet().iterator();
		while (iterBranch.hasNext()) {
			Entry<String, Map<String, List<String>>> entryBranch = iterBranch.next();
			Map<String, List<String>> mapCommitId = entryBranch.getValue();
			Iterator<Entry<String, List<String>>> iteratorcommitId = mapCommitId.entrySet().iterator();
			while (iteratorcommitId.hasNext()) {
				Entry<String, List<String>> entryCommitIds = iteratorcommitId.next();
				List<String> listChangeIds = entryCommitIds.getValue();
				Iterator<String> iterChangeId = listChangeIds.iterator();
				while (iterChangeId.hasNext()) {
					fillMappForPotentialBranch(mapBranches, defaultBranchName, entryBranch, entryCommitIds,
							iterChangeId);
				}
			}
		}
		return mapBranches;
	}

	/**
	 * @param mapBranches
	 * @param defaultBranchName
	 * @param entryBranch
	 * @param entryCommitIds
	 * @param iterChangeId
	 */
	private void fillMappForPotentialBranch(Map<String, BranchMatch> mapBranches, String defaultBranchName,
			Entry<String, Map<String, List<String>>> entryBranch, Entry<String, List<String>> entryCommitIds,
			Iterator<String> iterChangeId) {
		String lookingChangeId = revisionCheckedOut.getChangeInfo().getChange_id().trim();
		String lookingCommitIdForRevision = revisionCheckedOut.getCommit().getCommit().trim();
		String changeId = iterChangeId.next().trim();
		if (matchesPerfectlyAnotherRevision(revisionCheckedOut, entryCommitIds.getKey())) {
			return;
		}
		if (lookingCommitIdForRevision.equals(entryCommitIds.getKey())) {
			mapBranches.put(entryBranch.getKey(), BranchMatch.PERFECT_MATCH);//Perfect match branch with commit Id
			return;
		}
		if (lookingChangeId.equals(changeId)) {
			mapBranches.put(entryBranch.getKey(), BranchMatch.CHANGE_ID_MATCH);//Potential branch for this changeId, but with some modification on the branch
			return;
		}
		if (entryBranch.getKey().contains(defaultBranchName)) {
			mapBranches.put(entryBranch.getKey(), BranchMatch.BRANCH_NAME_MATCH);//Perfect match branch
			return;
		}
	}

	//Check if the given commitId matches the commitId of a revision that is not the current one
	private boolean matchesPerfectlyAnotherRevision(RevisionInfo revisionToCheckOut, String commitId) {
		ChangeInfo review = revisionToCheckOut.getChangeInfo();
		for (RevisionInfo aRevision : review.getRevisions().values()) {
			if (aRevision == revisionToCheckOut) {
				continue;
			}

			if (commitId.equals(aRevision.getCommit().getCommit().trim())) {
				return true;
			}
		}
		return false;
	}

	private void checkoutBranch(String branchName, Repository repo) throws Exception {
		CheckoutCommand command = null;
		try (Git gitRepo = new Git(repo)) {
			command = gitRepo.checkout();
			command.setCreateBranch(false);
			command.setName(branchName);
			command.setForced(false);
			command.call();
		} catch (Throwable t) {
			if (command != null) {
				CheckoutResult result = command.getResult();
				if (result != null) {
					UIRepositoryUtils.showCleanupDialog(repo, result.getConflictList(),
							UIText.BranchResultDialog_CheckoutConflictsTitle,
							Display.getDefault().getActiveShell());
				} else {
					EGerritCorePlugin.logError(gerritClient.getRepository().formatGerritVersion() + t.getMessage());
				}
			}
		}
	}

	private Shell getShell() {
		return Display.getDefault().getActiveShell();
	}

	private void setSelectedBranch(String selectedBranch) {
		this.selectedBranch = selectedBranch;
	}

	private String getSelectedBranch() {
		return selectedBranch;
	}

	/**
	 * Rename the branch using the default naming: change/<reviewId>/<patch-set>. Catching exception for
	 * RefNotFoundException, InvalidRefNameException, RefAlreadyExistsException, DetachedHeadException, GitAPIException
	 *
	 * @param newName
	 * @param repo
	 */
	private void renameBranch(String newName, Repository repo) {
		RenameBranchCommand command = null;
		try (Git gitRepo = new Git(repo)) {
			command = gitRepo.branchRename();
			command.setNewName(newName);
			command.call();
		} catch (Exception e) {

			UIUtils.displayInformation(Messages.CheckoutRevisionRenameBranchTitle,
					NLS.bind(Messages.CheckoutRevisionRenameException, e.getMessage()));
		}
	}

	/**
	 * Private class reading the information associated to a ref change
	 */
	private static final class Change {
		private final String refName;

		private final Integer changeNumber;

		private final Integer patchSetNumber;

		private static final String REF_CHANGES = "refs/changes/"; //$NON-NLS-1$

		static Change fromRef(String refName) {
			try {
				if (!refName.startsWith(REF_CHANGES)) {
					return null;
				}
				int reflen = REF_CHANGES.length();
				String[] tokens = refName.substring(reflen).split("/"); //$NON-NLS-1$
				if (tokens.length != 3) {
					return null;
				}
				Integer changeNumber = Integer.valueOf(tokens[1]);
				Integer patchSetNumber = Integer.valueOf(tokens[2]);
				return new Change(refName, changeNumber, patchSetNumber);
			} catch (NumberFormatException e) {
				// if we can't parse this, just return null
				return null;
			} catch (IndexOutOfBoundsException e) {
				// if we can't parse this, just return null
				return null;
			}
		}

		private Change(String refName, Integer changeNumber, Integer patchSetNumber) {
			this.refName = refName;
			this.changeNumber = changeNumber;
			this.patchSetNumber = patchSetNumber;
		}

		public String getBranchNameLabel() {
			return "change/" + changeNumber + "/" + patchSetNumber; //$NON-NLS-1$ //$NON-NLS-2$
		}

		public Integer getPatchSet() {
			return patchSetNumber;
		}

		public Integer getChangeNumber() {
			return changeNumber;
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return refName;
		}
	}

}
