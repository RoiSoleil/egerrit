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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.eclipse.egerrit.core.tests.Common;
import org.eclipse.egerrit.internal.core.command.CherryPickRevisionCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.CherryPickInput;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Test suite for {@link org.eclipse.egerrit.internal.core.command.CherryPickRevisionCommand}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class CherryPickRevisionCommandTest extends CommandTestWithSimpleReview {
	@Before
	public void ensureInitialCommit() throws Exception {
		gitAccess.commitFileInMaster("initialFile.txt", "initial commit to seed the repo");
		//Here we need to force a setup because the creation of review needs to be done from a repo that already has the commit.
		setUp();
		createReview();
	}

	/**
	 * Test method for {@link org.eclipse.egerrit.core.command.GerritCommand#call()}.
	 */
	@Test
	public void testCall() {
		CherryPickRevisionCommand command = fGerrit.cherryPickRevision(change_id, commit_id);
		CherryPickInput cherryPickInput = new CherryPickInput();
		cherryPickInput.setMessage("Implementing Feature X");
		// when the cherrypick works
		cherryPickInput.setDestination("master");
		command.setCommandInput(cherryPickInput);
		try {
			ChangeInfo result = null;
			result = command.call();
			assertNotEquals(change_id, result.getId());
			assertEquals("Implementing Feature X", result.getSubject());
		} catch (EGerritException e) {
			fail(e.getMessage());
		}
		// when the cherrypick cannot be performed because of an error
		Version version = new Version(Common.GERRIT_VERSION);
		if (version.getMajor() >= 3) {
			//Gerrit 3.x allows cherry-picking to HEAD, so use an invalid destination instead
			cherryPickInput.setDestination("does-not-exist");
		} else {
			cherryPickInput.setDestination("HEAD");
		}
		command.setCommandInput(cherryPickInput);
		try {
			ChangeInfo result = null;
			result = command.call();
			//Before Gerrit 2.12, the return value was null
			if (version.getMajor() >= 3) {
				assertNull(result);
				assertNotNull(command.getFailureReason());
			} else if (version.getMajor() == 2 && version.getMinor() < 12) {
				assertEquals(command.getFailureReason(), null);
			} else {
				assertEquals(command.getFailureReason(), "Branch HEAD does not exist.\n");
			}
		} catch (EGerritException e) {
			fail(e.getMessage());
		}

	}
}
