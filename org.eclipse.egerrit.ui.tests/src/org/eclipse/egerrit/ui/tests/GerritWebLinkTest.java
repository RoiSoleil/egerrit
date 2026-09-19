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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.egerrit.internal.ui.utils.GerritWebLink;
import org.junit.Test;

/**
 * Test the parsing of the links of the Gerrit web UI.
 *
 * @since 1.4
 */
@SuppressWarnings("nls")
public class GerritWebLinkTest {

	@Test
	public void testGerrit3Link() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/1/folder/MyFile.java");
		assertEquals("sandbox/test", link.getProject());
		assertEquals("407", link.getChangeNumber());
		assertEquals("1", link.getPatchset());
		assertEquals(1, link.getPatchsetNumber());
		assertEquals("folder/MyFile.java", link.getPath());
		assertFalse(link.isMagicPath());
	}

	@Test
	public void testGerrit3FullUrl() {
		GerritWebLink link = GerritWebLink
				.parse("http://localhost:8081/c/sandbox/test/+/407/2/src/org/example/File.java");
		assertEquals("sandbox/test", link.getProject());
		assertEquals("407", link.getChangeNumber());
		assertEquals(2, link.getPatchsetNumber());
		assertEquals("src/org/example/File.java", link.getPath());
	}

	@Test
	public void testGerrit2Link() {
		GerritWebLink link = GerritWebLink.parse("#/c/407/1/folder/MyFile.java");
		assertNull(link.getProject());
		assertEquals("407", link.getChangeNumber());
		assertEquals("folder/MyFile.java", link.getPath());
	}

	@Test
	public void testChangeLinkWithoutFile() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/");
		assertEquals("sandbox/test", link.getProject());
		assertEquals("407", link.getChangeNumber());
		assertNull(link.getPatchset());
		assertNull(link.getPath());
	}

	@Test
	public void testChangeLinkWithoutPatchsetAndFile() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407");
		assertEquals("407", link.getChangeNumber());
		assertNull(link.getPatchset());
		assertNull(link.getPath());
	}

	@Test
	public void testEditPatchset() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/edit/folder/MyFile.java");
		assertEquals("edit", link.getPatchset());
		assertEquals(-1, link.getPatchsetNumber());
		assertEquals("folder/MyFile.java", link.getPath());
	}

	@Test
	public void testMagicPath() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/1/COMMIT_MSG");
		assertEquals("COMMIT_MSG", link.getPath());
		assertTrue(link.isMagicPath());
		assertTrue(GerritWebLink.parse("#/c/sandbox/test/+/407/1//COMMIT_MSG").isMagicPath());
		assertFalse(GerritWebLink.parse("#/c/sandbox/test/+/407/1/folder/File.java").isMagicPath());
	}

	@Test
	public void testEncodedPath() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/1/folder/My%20File.java");
		assertEquals("folder/My File.java", link.getPath());
	}

	@Test
	public void testPathContainingChangeSegment() {
		GerritWebLink link = GerritWebLink
				.parse("http://localhost:8081/c/sandbox/test/+/407/1/src/c/MyFile.c");
		assertEquals("sandbox/test", link.getProject());
		assertEquals("src/c/MyFile.c", link.getPath());
		assertEquals("src/c/MyFile.c", GerritWebLink.parse("#/c/sandbox/test/+/407/1/src/c/MyFile.c").getPath());
	}

	@Test
	public void testQueryStringIsIgnored() {
		GerritWebLink link = GerritWebLink.parse("#/c/sandbox/test/+/407/1/folder/MyFile.java?line=10");
		assertEquals("folder/MyFile.java", link.getPath());
	}

	@Test
	public void testNullAndInvalidLinks() {
		assertNull(GerritWebLink.parse(null));
		assertNull(GerritWebLink.parse(""));
		assertNull(GerritWebLink.parse("#/q/status:open"));
		assertNull(GerritWebLink.parse("#/settings/preferences"));
		assertNull(GerritWebLink.parse("#/c/notANumber/1/folder/MyFile.java"));
	}
}
