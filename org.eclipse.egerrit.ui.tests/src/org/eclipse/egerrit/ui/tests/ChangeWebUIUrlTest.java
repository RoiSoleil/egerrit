/*******************************************************************************
 * Copyright (c) 2026 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson AB - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.ui.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.eclipse.egerrit.internal.core.GerritServerInformation;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ModelFactory;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Test the web UI URL built for a change.
 *
 * @since 1.4
 */
@SuppressWarnings("nls")
public class ChangeWebUIUrlTest {

	private ChangeInfo createChange() {
		ChangeInfo change = ModelFactory.eINSTANCE.createChangeInfo();
		change.set_number(42);
		change.setProject("sandbox/test");
		return change;
	}

	private GerritServerInformation createServer(String scheme, int port) throws Exception {
		return new GerritServerInformation(new URI(scheme, null, "localhost", port, "", null, null).toASCIIString(),
				"Test server");
	}

	@Test
	public void testGerrit2Url() throws Exception {
		String url = UIUtils.buildChangeWebUIUrl(createServer("http", 8080), new Version(2, 11, 5), createChange());
		assertEquals("http://localhost:8080/#/c/42/", url);
	}

	@Test
	public void testGerrit3Url() throws Exception {
		String url = UIUtils.buildChangeWebUIUrl(createServer("http", 8081), new Version(3, 14, 3), createChange());
		assertEquals("http://localhost:8081/c/sandbox/test/+/42", url);
	}

	@Test
	public void testServerUriWithTrailingSlash() throws Exception {
		GerritServerInformation server = new GerritServerInformation("http://localhost:8081/", "Test server");
		String url = UIUtils.buildChangeWebUIUrl(server, new Version(3, 14, 3), createChange());
		assertTrue(url.endsWith("/c/sandbox/test/+/42"));
	}
}
