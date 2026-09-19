/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Ericsson - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.core.command.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.egerrit.internal.core.command.ChangeOption;
import org.eclipse.egerrit.internal.core.command.DeleteDraftRevisionCommand;
import org.eclipse.egerrit.internal.core.command.GetChangeCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.core.tests.Common;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.emf.common.util.EMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Test suite for {@link org.eclipse.egerrit.internal.core.command.DeleteDraftRevisionCommand}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class DeleteDraftRevisionCommandTest extends CommandTestWithSimpleReview {

	@Before
	public void createDraftReview() {
		//Draft revisions were removed in Gerrit 2.16
		Assume.assumeTrue(Common.SUPPORTS_DRAFT_CHANGES);
		createReviewWithSimpleFile(true);
		amendLastCommit(true);
	}

	@Test
	public void testCall() throws EGerritException {
		//Make sure we have two revisions
		GetChangeCommand getDetails = fGerrit.getChange(change_id);
		getDetails.addOption(ChangeOption.ALL_REVISIONS);
		EMap<String, RevisionInfo> revisions = getDetails.call().getRevisions();
		assertEquals(2, revisions.size());

		DeleteDraftRevisionCommand command2 = fGerrit.deleteDraftRevision(change_id,
				revisions.keySet().iterator().next());
		try {
			command2.call();

		} catch (EGerritException e) {
			fail(e.getMessage());
		}

		//assert that we have one revision left
		getDetails = fGerrit.getChange(change_id);
		getDetails.addOption(ChangeOption.ALL_REVISIONS);
		revisions = getDetails.call().getRevisions();
		assertEquals(1, revisions.size());

	}
}
