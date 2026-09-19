/*******************************************************************************
 * Copyright (c) 2015-2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Ericsson - Initial API and implementation
 *******************************************************************************/
package org.eclipse.egerrit.core.tests.support;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egerrit.core.tests.Common;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * A helper to deal with Git repos for testing purpose
 *
 * @since 1.0
 */

@SuppressWarnings("nls")
public class GitAccess {
	private Git fGit = null;

	private String fChange_id;

	private String fCommit_id;

	private String lastCommitId;

	private File checkoutFolder;

	private String gitRepoURL;

	static {
		setDefaultAuthenticator();
	}

	public GitAccess() {
		this(null, null);
	}

	public GitAccess(String server, String project) {
		if (server == null) {
			server = Common.SCHEME + "://" + Common.HOST + ":" + Common.PORT + Common.PATH;
		}

		if (project == null) {
			project = Common.TEST_PROJECT;
		}
		gitRepoURL = server + '/' + project;
	}

	/**
	 * Instantiates a local git repo connected to the server.
	 */
	public Git getGitProject() throws Exception {
		if (fGit == null) {
			cloneRepo();
		}
		return fGit;
	}

	public GitAccess(File repo) {
		try {
			fGit = Git.open(repo);
			checkoutFolder = repo;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void cloneRepo() throws Exception {
		CloneCommand cloneCmd = Git.cloneRepository();
		checkoutFolder = createTempFolder("egerrit");
		System.out.println("Checking out " + gitRepoURL + " into " + checkoutFolder);
		cloneCmd.setGitDir(new File(checkoutFolder, ".git")).setURI(gitRepoURL).setDirectory(checkoutFolder);
		cloneCmd.setBare(false).setBranch("master").setNoCheckout(false);
		fGit = cloneCmd.call();
	}

	/**
	 * Creates a file and stage it
	 *
	 * @param fileName
	 *            , the path relative to the root of repo
	 * @param content
	 * @return
	 */
	public void addFile(String fileName, String content) throws Exception {
		File fileToAdd = new File(checkoutFolder, fileName);
		fileToAdd.getParentFile().mkdirs();
		if (fileToAdd.exists()) {
			fileToAdd.delete();
		}
		try (Writer writer = new FileWriter(fileToAdd)) {
			writer.write(content);
		}
		fGit.add().addFilepattern(fileName).call();
	}

	/**
	 * Modify a file and stage it
	 *
	 * @param fileName
	 * @param content
	 * @return
	 */
	public void modifyFile(String fileName, String newContent) throws Exception {
		File gitDir = fGit.getRepository().getDirectory();
		new File(gitDir, fileName).delete();
		addFile(fileName, newContent);
	}

	/**
	 * Commit the staged changes to the git repository.
	 *
	 * @param commitMessage
	 *            or null
	 * @throws Exception
	 */
	public void commitAndPush(String commitMessage) throws Exception {
		RevCommit commitId = fGit.commit()
				.setCommitter(Common.USER, Common.EMAIL)
				.setMessage(commitMessage == null ? "a commit" : commitMessage)
				.call();
		lastCommitId = ObjectId.toString(commitId.getId());
		fGit.push()
				.setCredentialsProvider(new UsernamePasswordCredentialsProvider(Common.USER, Common.PASSWORD))
				.call();
	}

	/**
	 * Commits and pushes to gerrit server
	 *
	 * @param draft
	 *            indicate whether the review should be created as a draft
	 * @param amend
	 *            indicate whether the previous comment should be amended
	 */
	public void pushFile(boolean draft, boolean amend) throws Exception {
		pushFile("Test commit message", draft, amend);
	}

	/**
	 * Commits and pushes to gerrit server
	 *
	 * @param commitMsg
	 *            the commit message
	 * @param draft
	 *            indicate whether the review should be created as a draft
	 * @param amend
	 *            indicate whether the previous comment should be amended
	 */
	public void pushFile(String commitMsg, boolean draft, boolean amend) throws Exception {
		//create a commit
		commitFile(commitMsg, true, amend);

		//push the commit
		Authenticator.setDefault(null);
		String refSpec = "HEAD:refs/for/master";
		if (draft) {
			if (Common.SUPPORTS_DRAFT_CHANGES) {
				refSpec = "HEAD:refs/drafts/master";
			} else {
				//Draft changes do not exist anymore: create a work-in-progress change instead
				refSpec = "HEAD:refs/for/master%wip";
			}
		}
		CredentialsProvider creds = new UsernamePasswordCredentialsProvider(Common.USER, Common.PASSWORD);
		Iterable<PushResult> result = fGit.push()
				.setCredentialsProvider(creds)
				.setRefSpecs(new RefSpec(refSpec))
				.call();

		Collection<RemoteRefUpdate> crru = result.iterator().next().getRemoteUpdates();
		RemoteRefUpdate rru = crru.iterator().next();
		fCommit_id = rru.getNewObjectId().toString().substring("AnyObjectId[".length(),
				rru.getNewObjectId().toString().length() - 1);
	}

	public void commitFile(String commitMsg, boolean generateChangeId, boolean amend) throws Exception {
		CommitCommand command = fGit.commit();
		RevCommit call = command.setAuthor("Test", Common.EMAIL) //$NON-NLS-1$
				.setCommitter(Common.USER, Common.EMAIL)
				.setInsertChangeId(generateChangeId)
				.setMessage(commitMsg)
				.setAmend(amend)
				.call();
		int cid = call.getFullMessage().indexOf("Change-Id: ");
		if (cid != -1) {
			fChange_id = call.getFullMessage().substring(cid + "Change-Id: ".length()).trim();
		}
	}

	public void commitInMaster(String commitMsg) throws Exception {
		commitFile(commitMsg, false, false);

		Authenticator.setDefault(null);
		String refSpec = "HEAD:refs/heads/master";
		CredentialsProvider creds = new UsernamePasswordCredentialsProvider(Common.USER, Common.PASSWORD);
		Iterable<PushResult> result = fGit.push()
				.setCredentialsProvider(creds)
				.setRefSpecs(new RefSpec(refSpec))
				.call();

		Collection<RemoteRefUpdate> crru = result.iterator().next().getRemoteUpdates();
		RemoteRefUpdate rru = crru.iterator().next();
		fCommit_id = rru.getNewObjectId().toString().substring("AnyObjectId[".length(),
				rru.getNewObjectId().toString().length() - 1);
	}

	private static File createTempFolder(String prefix) throws IOException {
		File location = File.createTempFile(prefix, null);
		location.delete();
		location.mkdirs();
		return location;
	}

	/**
	 * Returns the changed id of the last commit changed pushed to gerrit
	 *
	 * @return change id
	 */
	public String getChangeId() {
		return fChange_id;
	}

	/**
	 * Returns the last commit id
	 *
	 * @return string of the commit id
	 */
	public String getCommitId() {
		return fCommit_id;
	}

	/**
	 * Add git repo to the list of repositories known by egit
	 */
	public void addToGitView() {
		RepositoryUtil repoUtil = RepositoryUtil.INSTANCE;
		repoUtil.addConfiguredRepository(fGit.getRepository().getDirectory());
	}

	/**
	 * Remove git repo from the list of repositories known by egit
	 */
	public void removeFromGitView() {
		if (fGit == null) {
			return;
		}
		RepositoryUtil repoUtil = RepositoryUtil.INSTANCE;
		repoUtil.removeDir(fGit.getRepository().getDirectory());
	}

	/**
	 * Import the path as a project in the workspace
	 *
	 * @param projectFilePath
	 * @throws CoreException
	 */
	public void importProject(String projectFilePath) throws CoreException {
		IProjectDescription description = ResourcesPlugin.getWorkspace().loadProjectDescription(
				new org.eclipse.core.runtime.Path(new File(checkoutFolder, projectFilePath).getAbsolutePath()));
		if (description != null) {
			String projectName = description.getName();
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			if (project.exists()) {
				if (!project.isOpen()) {
					project.open(IResource.BACKGROUND_REFRESH, new NullProgressMonitor());
				}
			} else {
				project.create(description, new NullProgressMonitor());
				project.open(IResource.BACKGROUND_REFRESH, new NullProgressMonitor());
			}
		}
	}

	/**
	 * Remove a project from the file system but w/o deleting the underlying files
	 *
	 * @param projectFilePath
	 * @throws CoreException
	 */
	public void removeProject(String projectName) throws CoreException {
		ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).delete(false, true, new NullProgressMonitor());
	}

	/**
	 * Remove file from the git repository
	 *
	 * @param filename
	 *            relative filepath to remove
	 * @throws Exception
	 */
	public void removeFile(String filename) throws Exception {
		fGit.rm().addFilepattern(filename).call();
	}

	/**
	 * Return the id of the last commit done locally
	 *
	 * @return the commit id
	 */
	public String getLastLocalCommitId() {
		return lastCommitId;
	}

	/**
	 * Reset the local repository to the given commit id
	 *
	 * @param commitId
	 * @throws Exception
	 */
	public void resetTo(String commitId) throws Exception {
		fGit.reset().setMode(ResetType.HARD).setRef(commitId).call();
	}

	//This is necessary to avoid being prompted for username/pwd when we are pushing commit to the git repo
	private static void setDefaultAuthenticator() {
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				// ignore
				return new PasswordAuthentication(Common.USER, Common.PASSWORD.toCharArray());
			}
		});
	}

	public File getCheckoutFolder() {
		return checkoutFolder;
	}

	public void close() {
		fGit.close();
	}

	/**
	 * Get the current branch from egit
	 */
	public String getCurrentBranch() {
		RepositoryUtil repoUtil = RepositoryUtil.INSTANCE;
		String branch = null;
		try {
			branch = repoUtil.getShortBranch(fGit.getRepository());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return branch;
	}

	/**
	 * Create a new branch and make it the current one
	 *
	 * @param branchName
	 * @throws Exception
	 */
	public void createAndCheckoutBranch(String branchName) throws Exception {
		Repository repo = fGit.getRepository();
		CheckoutCommand command = null;
		try (Git gitRepo = new Git(repo)) {
			command = gitRepo.checkout();
			command.setCreateBranch(true);
			command.setName(branchName);
			command.setForced(false);
			command.call();
		} catch (Throwable t) {
			if (command != null) {
				CheckoutResult result = command.getResult();
				fail(result.getStatus().toString());
			}
			fail(t.getMessage());
		}
		System.out.println("Branch c/o: " + branchName);
	}

	/**
	 * Remove the selected branch
	 *
	 * @param oldBranchName
	 */
	public void branchRemove(String oldBranchName) {
		DeleteBranchCommand remove = fGit.branchDelete();
		try {
			remove.setBranchNames(oldBranchName).setForce(true).call();
		} catch (RefNotFoundException | InvalidRefNameException | RefAlreadyExistsException | DetachedHeadException e) {
			fail(e.getMessage());
		} catch (GitAPIException e) {
			fail(e.getMessage());
		}
	}

	public void commitFileInMaster(String fileName, String content) throws Exception {
		if (getCheckoutFolder().exists() && new File(getCheckoutFolder(), fileName).exists()) {
			return;
		}
		addFile(fileName, content);

		String commitMsg = "Add " + fileName; //$NON-NLS-1$
		commitInMaster(commitMsg);
	}
}
