/*******************************************************************************
 * Copyright (c) 2015 Ericsson AB.
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
import java.net.URISyntaxException;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.FileInfo;
import org.eclipse.egerrit.internal.ui.compare.GerritMultipleInput;
import org.eclipse.egerrit.internal.ui.utils.GerritToGitMapping;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.osgi.framework.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenCompareEditor {
	private static final Logger logger = LoggerFactory.getLogger(OpenCompareEditor.class);

	private static final boolean SUPPORT_SWAP = Platform.getBundle("org.eclipse.compare") //$NON-NLS-1$
			.getVersion()
			.compareTo(new Version("3.7.0")) > 0; //$NON-NLS-1$

	private final GerritClient gerrit;

	private final ChangeInfo changeInfo;

	public OpenCompareEditor(GerritClient gerrit, ChangeInfo changeInfo) {
		this.gerrit = gerrit;
		this.changeInfo = changeInfo;
	}

	public void compareFiles(String leftSide, String rightSide, FileInfo fileToReveal) {
		if (!SUPPORT_SWAP) {
			CompareUI.openCompareEditor(new GerritMultipleInput(leftSide, rightSide, changeInfo, gerrit, fileToReveal));
			return;
		}

		CompareUI.openCompareEditor(new GerritMultipleInput(rightSide, leftSide, changeInfo, gerrit, fileToReveal));
	}

	public IFile getCorrespondingWorkspaceFile(FileInfo reviewFile) {
		if (reviewFile == null) {
			return null;
		}
		return getCorrespondingWorkspaceFile(reviewFile.getPath(), reviewFile.getOld_path());
	}

	public IFile getCorrespondingWorkspaceFile(String filePath) {
		return getCorrespondingWorkspaceFile(filePath, null);
	}

	private IFile getCorrespondingWorkspaceFile(String filePath, String oldFilePath) {
		File potentialFile = locateFileInLocalGitRepo(filePath, oldFilePath);
		IFile workspaceFile = null;
		if (potentialFile == null) {
			logger.debug("The corresponding file could not be found in any git repository known by the workspace."); //$NON-NLS-1$
		}

		if (potentialFile != null) {
			workspaceFile = getFileFromWorkspace(potentialFile);
			if (workspaceFile == null) {
				logger.debug(
						"The compare editor could not be opened because the corresponding file is not in the workspace."); //$NON-NLS-1$
			}
		}
		return workspaceFile;
	}

	private IFile getFileFromWorkspace(File potentialFile) {
		return ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
				org.eclipse.core.runtime.Path.fromOSString(potentialFile.getAbsolutePath()));

	}

	private File locateFileInLocalGitRepo(String filePath, String oldFilePath) {
		Repository repo;
		try {
			repo = new GerritToGitMapping(new URIish(gerrit.getRepository().getURIBuilder(false).toString()),
					changeInfo.getProject()).find();
			if (repo == null) {
				return null;
			}
		} catch (IOException | URISyntaxException e) {
			return null;
		}
		File workTree = repo.getWorkTree();
		if (workTree == null) {
			return null;
		}
		File potentialFile = new File(workTree, filePath);
		if (potentialFile.exists()) {
			return potentialFile;
		}

		//Try to find a file with the old name
		if (oldFilePath != null) {
			potentialFile = new File(workTree, oldFilePath);
			if (potentialFile.exists()) {
				return potentialFile;
			}
		}
		return null;
	}
}
