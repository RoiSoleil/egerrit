/*******************************************************************************
 * Copyright (c) 2016 Ericsson
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Map;

import org.eclipse.egerrit.core.tests.Common;
import org.eclipse.egerrit.internal.core.command.ListProjectsCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.model.ProjectInfo;
import org.junit.Test;

/**
 * Test suite for {@link org.eclipse.egerrit.internal.core.command.ListProjectsCommand}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class ListProjectsCommandTest extends CommandTestWithSimpleReview {
	@Test
	public void testCall() throws Exception {
		// Run test
		ListProjectsCommand listProjectsCommand = fGerrit.listProjects();
		try {
			Map<String, ProjectInfo> listProjectsResult = listProjectsCommand.call();
			System.out.println(listProjectsResult);
			assertNotNull(listProjectsResult.get(Common.TEST_PROJECT));
		} catch (EGerritException e) {
			fail(e.getMessage());
		}
	}

}
