/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Guy Perron - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.core.command.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.eclipse.egerrit.internal.core.command.AddReviewerCommand;
import org.eclipse.egerrit.internal.core.command.ListReviewersCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.AddReviewerInput;
import org.eclipse.egerrit.internal.model.ReviewerInfo;
import org.junit.Test;

/**
 * Test suite for {@link org.eclipse.egerrit.internal.core.command.QueryChangesCommand}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class ListReviewersCommandTest extends CommandTestWithSimpleReview {
	@Test
	public void testCall() {
		// Add a valid reviewer ...
		final String user = "test1";
		AddReviewerCommand addCommand = fGerrit.addReviewer(change_id);
		AddReviewerInput addreviewerInput = new AddReviewerInput();
		addreviewerInput.setReviewer(user);
		addCommand.setCommandInput(addreviewerInput);
		try {
			addCommand.call();
		} catch (EGerritException e) {
			fail(e.getMessage());
		}

		//Test
		ListReviewersCommand command = fGerrit.getReviewers(change_id);
		try {
			ReviewerInfo[] result = command.call();
			assertEquals(user, result[0].getUsername());
		} catch (EGerritException e) {
			fail(e.getMessage());
		}
	}

}
