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

package org.eclipse.egerrit.internal.ui.utils;

import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * Given a file in a repository, this class is able to indicate where a given line has moved. It computes this new
 * position by comparing the file in HEAD with the most recent commit.
 */
public class MarkerRepositioner {
	private Repository repo;

	private String filePath;

	private EditList edits;

	MarkerRepositioner(Repository repo, String filePath) throws Exception {
		this.repo = repo;
		this.filePath = filePath;

		computeEdits();
	}

	private void computeEdits() throws Exception {
		try (Git git = new Git(repo)) {
			try (EditExtractor formatter = new EditExtractor(System.out)) {
				formatter.setPathFilter(PathFilter.create(filePath));
				formatter.setRepository(git.getRepository());
				AbstractTreeIterator commitTreeIterator = prepareTreeParser(git.getRepository(), Constants.HEAD);
				FileTreeIterator workTreeIterator = new FileTreeIterator(git.getRepository());
				List<DiffEntry> diffEntries = formatter.scan(commitTreeIterator, workTreeIterator);

				for (DiffEntry entry : diffEntries) {
					formatter.format(entry);
				}
				edits = formatter.getEdits();
			}
		}
	}

	private AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws Exception {
		Ref head = repository.exactRef(ref);
		try (RevWalk walk = new RevWalk(repository)) {
			RevCommit commit = walk.parseCommit(head.getObjectId());
			RevTree tree = walk.parseTree(commit.getTree().getId());

			CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
			try (ObjectReader oldReader = repository.newObjectReader()) {
				oldTreeParser.reset(oldReader, tree.getId());
			}
			return oldTreeParser;
		}
	}

	/**
	 * Given a line number, returns the location of the line in the new file
	 */
	public int getNewPositionFor(int originalLine) {
		if (edits == null) {
			return originalLine;
		}
		return computePosition(originalLine, edits, 0);
	}

	private int computePosition(int originalPosition, List<Edit> editList, int delta) {
		if (editList.isEmpty()) {
			return originalPosition + delta;
		}
		Edit current = editList.get(0);
		//We stop since the edit we are looking at is passed the position we are interested in
		if (current.getBeginA() > originalPosition) {
			return originalPosition + delta;
		}

		if (current.getBeginA() == originalPosition && current.getEndA() == originalPosition) {
			return originalPosition + delta;
		}

		if (originalPosition >= current.getBeginA() && originalPosition < current.getEndA()) {
			if (current.getType() == Type.DELETE) {
				return -(current.getBeginA());
			}
			if (current.getType() == Type.REPLACE) {
				return originalPosition;
			}
			return originalPosition;
		}

		return computePosition(originalPosition,
				editList.size() == 1 ? Collections.emptyList() : editList.subList(1, editList.size()),
				delta + lineDelta(current));
	}

	private int lineDelta(Edit edit) {
		if (edit.getType() == Type.INSERT) {
			return edit.getLengthB();
		}
		if (edit.getType() == Type.EMPTY) {
			return 0;
		}
		if (edit.getType() == Type.DELETE) {
			return -edit.getLengthA();
		}
		if (edit.getType() == Type.REPLACE) {
			return edit.getLengthB() - edit.getLengthA();
		}
		return 0;
	}

	/**
	 * For testing purpose only
	 *
	 * @param edits
	 */
	public void setEdits(EditList edits) {
		this.edits = edits;
	}

	/**
	 * For testing purpose only
	 */
	public MarkerRepositioner() {
	}

}
