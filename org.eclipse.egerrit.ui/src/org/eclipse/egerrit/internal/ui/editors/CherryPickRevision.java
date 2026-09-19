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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.FetchInfo;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.EGerritUIPlugin;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.internal.commit.RepositoryCommit;
import org.eclipse.egit.ui.internal.fetch.FetchOperationUI;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISources;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

/**
 * Code to cherry-pick a revision
 */
public class CherryPickRevision extends Action {
	private static final String EGIT_CHERRY_PICK = "org.eclipse.egit.ui.commit.CherryPick"; //$NON-NLS-1$

	private RevCommit commit;

	private Repository repo;

	private RevisionInfo revision;

	private GerritClient gerritClient;

	public CherryPickRevision(RevisionInfo gerritRevision, GerritClient gerritClient) {
		this.gerritClient = gerritClient;
		this.revision = gerritRevision;
		this.setText(Messages.CherryPickRevision_0);
	}

	@Override
	public void run() {
		repo = new FindLocalRepository(gerritClient, revision.getChangeInfo().getProject()).getRepository();
		if (repo == null) {
			Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CherryPickRevision_1);
			ErrorDialog.openError(Display.getDefault().getActiveShell(), Messages.CherryPickRevision_2,
					Messages.CherryPickRevision_3, status);
			return;
		}
		URIish remoteURI = getRemoteURI();
		if (remoteURI != null) {
			Job job = new WorkspaceJob(Messages.CherryPickRevision_4 + remoteURI.toPrivateString()) {
				@Override
				public IStatus runInWorkspace(IProgressMonitor monitor) {
					try {
						fetchChange(monitor);
					} catch (CoreException | URISyntaxException | IOException e) {
						return new Status(IStatus.ERROR, EGerritUIPlugin.PLUGIN_ID,
								Messages.CherryPickRevision_5 + remoteURI.toPrivateString(), e);
					}
					return Status.OK_STATUS;
				}

				@Override
				public boolean belongsTo(Object family) {
					if (JobFamilies.FETCH.equals(family)) {
						return true;
					}
					return super.belongsTo(family);
				}
			};
			job.setUser(true);
			job.schedule();
			try {
				job.join();
			} catch (InterruptedException e) {
				return;
			}
			cherryPickAfterFetch(job);
		}

	}

	/**
	 * @param job
	 */
	private void cherryPickAfterFetch(Job job) {
		try {
			if (!job.getResult().isOK()) {
				return;
			}
			initializeCommitFromRevision();
			if (commit == null) {
				Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CherryPickRevision_6);
				ErrorDialog.openError(Display.getDefault().getActiveShell(), Messages.CherryPickRevision_2,
						Messages.CherryPickRevision_3, status);
			}
			if (!invokeEGitCherryPickCommand()) {
				Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CherryPickRevision_9);
				ErrorDialog.openError(Display.getDefault().getActiveShell(), Messages.CherryPickRevision_2,
						Messages.CherryPickRevision_3, status);
			}
		} catch (CoreException | IOException e) {
			Status status = new Status(IStatus.ERROR, EGerritCorePlugin.PLUGIN_ID, Messages.CherryPickRevision_12);
			ErrorDialog.openError(Display.getDefault().getActiveShell(), Messages.CherryPickRevision_2,
					Messages.CherryPickRevision_3, status);
		}
	}

	private void initializeCommitFromRevision() throws IOException, CoreException {
		ObjectId commitId = repo.resolve(revision.getCommit().getCommit());
		commit = null;
		try (org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
			commit = revWalk.parseCommit(commitId);
			revWalk.dispose();
		}
	}

	private String getRef() {
		//Here we can pick any FetchInfo because the ref string is same everywhere
		if (!revision.getFetch().isEmpty()) {
			Entry<String, FetchInfo> x = revision.getFetch().iterator().next();
			return x.getValue().getRef();
		}
		return null;
	}

	private boolean invokeEGitCherryPickCommand() {
		final IStructuredSelection selection = new StructuredSelection(new RepositoryCommit(repo, commit));

		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		Command cmd = commandService.getCommand(EGIT_CHERRY_PICK);
		if (!cmd.isDefined()) {
			return false;
		}

		IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
		EvaluationContext c = null;
		c = new EvaluationContext(handlerService.createContextSnapshot(false), selection.toList());
		c.addVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME, selection);
		c.removeVariable(ISources.ACTIVE_MENU_SELECTION_NAME);
		try {
			handlerService.executeCommandInContext(new ParameterizedCommand(cmd, null), null, c);
		} catch (ExecutionException | NotDefinedException | NotEnabledException | NotHandledException e) {
			return false;
		}

		return true;
	}

	//Run the fetch in the workspace to give feedback to the user
	private void fetchChange(IProgressMonitor monitor) throws CoreException, URISyntaxException, IOException {
		final RefSpec spec = new RefSpec().setSource(getRef()).setDestination(Constants.FETCH_HEAD);
		List<RefSpec> specs = new ArrayList<>(1);
		specs.add(spec);

		new FetchOperationUI(repo, getRemoteURI(), specs, false).execute(monitor);
	}

	private URIish getRemoteURI() {
		try {
			return new URIish(gerritClient.getRepository().getURIBuilder(false).toString() + "/" //$NON-NLS-1$
					+ revision.getChangeInfo().getProject());
		} catch (URISyntaxException e) {
			return null;
		}

	}

	private boolean connectionTypeAvailable(String scheme) {
		return revision.getFetch().get(scheme) != null;
	}
}
