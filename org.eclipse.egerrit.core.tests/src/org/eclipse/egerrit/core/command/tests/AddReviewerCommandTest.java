/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jacques Bouthillier - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.core.command.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.eclipse.egerrit.internal.core.command.AddReviewerCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.AddReviewerInput;
import org.eclipse.egerrit.internal.core.rest.AddReviewerResult;
import org.junit.Test;

/**
 * Test suite for {@link org.eclipse.egerrit.internal.core.command.AddReviewerCommand}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class AddReviewerCommandTest extends CommandTestWithSimpleReview {

	/**
	 * Test method for {@link org.eclipse.egerrit.internal.core.command.AddReviewerCommand#call()}.
	 */
	@Test
	public void testCall() {
		// Add a valid reviewer ...
		AddReviewerCommand command = fGerrit.addReviewer(change_id);
		AddReviewerInput addreviewerInput = new AddReviewerInput();
		addreviewerInput.setReviewer("test1");

		command.setCommandInput(addreviewerInput);

		AddReviewerResult reviewerCmdResult = null;
		try {
			reviewerCmdResult = command.call();
			assertEquals("test1", reviewerCmdResult.getReviewers().get(0).getUsername());
			assertEquals(false, reviewerCmdResult.getConfirm());
		} catch (EGerritException e) {
			fail(e.getMessage());
		}

	}

	/**
	 * Test method for {@link org.eclipse.egerrit.internal.core.command.AddReviewerCommand#call()}.
	 */
	@Test
	public void testAddGroupCall() {
		// Add a valid reviewer group. I.e. the current Group defined: "Administrator".
		AddReviewerCommand command = fGerrit.addReviewer(change_id);
		AddReviewerInput addreviewerInput = new AddReviewerInput();
		addreviewerInput.setReviewer("test1");
		command.setCommandInput(addreviewerInput);

		AddReviewerResult reviewerCmdResult = null;
		try {
			reviewerCmdResult = command.call();
			assertFalse(reviewerCmdResult.getReviewers().isEmpty());
			assertEquals(false, reviewerCmdResult.getConfirm());
		} catch (EGerritException e) {
			fail(e.getMessage());
		}
	}

	/**
	 * Test method for {@link org.eclipse.egerrit.internal.core.command.AddReviewerCommand#call()}.
	 */
	@Test
	public void testAddUndefinedReviewerCall() {
		// Add a valid reviewer group. I.e. the current Group defined: "Administrator".
		AddReviewerCommand command = fGerrit.addReviewer(change_id);
		AddReviewerInput addreviewerInput = new AddReviewerInput();
		addreviewerInput.setReviewer("AUserThatDoesNotExist");

		command.setCommandInput(addreviewerInput);

		try {
			command.call();
		} catch (EGerritException e) {
			if (e.getCode() != EGerritException.SHOWABLE_MESSAGE) {
				fail(e.getMessage());
			}
		}
	}
}
