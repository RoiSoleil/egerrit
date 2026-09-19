/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francois Chouinard - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.core.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.GerritCredentials;
import org.eclipse.egerrit.internal.core.GerritFactory;
import org.eclipse.egerrit.internal.core.GerritRepository;
import org.eclipse.egerrit.internal.core.Gerrit_2_11;
import org.eclipse.egerrit.internal.core.Gerrit_3_0;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Test suite for {@link org.eclipse.egerrit.GerritFactory}
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class GerritFactoryTest {

	// ------------------------------------------------------------------------
	// Constants
	// ------------------------------------------------------------------------

	private final String SCHEME = Common.SCHEME;

	private final String HOST = Common.HOST;

	private final int PORT = Common.PORT;

	private final String PATH = Common.PATH;

	private final String USER = Common.USER;

	private final String PASSWORD = Common.PASSWORD;

	// ------------------------------------------------------------------------
	// Helper classes
	// ------------------------------------------------------------------------

	/**
	 * MyGerritRepository - used to have finer control over repo config
	 */
	class MyGerritRepository extends GerritRepository {
		private final Version fVersion;

		public MyGerritRepository(String version) {
			super(SCHEME, HOST, PORT, PATH);
			setCredentials(new GerritCredentials(USER, PASSWORD));
			fVersion = (version != null) ? new Version(version) : null;
		}

		@Override
		public Version getVersion() {
			return fVersion;
		}
	}

	// ------------------------------------------------------------------------
	// Test cases
	// ------------------------------------------------------------------------

	/**
	 * Test method for
	 * {@link org.eclipse.egerrit.internal.core.GerritFactory#create(org.eclipse.egerrit.internal.core.GerritRepository)}
	 * with null repository
	 */
	@Test
	public void testCreateNull() {

		// Initialize
		GerritRepository repo = null;

		// Run test
		GerritClient gerrit = null;
		try {
			GerritFactory.create(repo);
			fail("Exception should have been thrown");
		} catch (EGerritException e) {
		}

		// Verify result
		assertNull(gerrit);
	}

	/**
	 * Test method for
	 * {@link org.eclipse.egerrit.internal.core.GerritFactory#create(org.eclipse.egerrit.internal.core.GerritRepository)}
	 * with unknown repository version
	 */
	@Test
	public void testCreateVersionTooOld() {

		// Initialize
		GerritRepository repo = new MyGerritRepository("2.7.0.too-old");

		// Run test
		GerritClient gerrit = null;
		try {
			GerritFactory.create(repo);
			fail("Exception should have been thrown");
		} catch (EGerritException e) {
		}

		// Verify result
		assertNull(gerrit);
	}

	/**
	 * Test method for
	 * {@link org.eclipse.egerrit.internal.core.GerritFactory#create(org.eclipse.egerrit.internal.core.GerritRepository)}
	 * Normal case
	 */
	@Test
	public void testCreate() {

		// Initialize
		GerritRepository repo = new GerritRepository(SCHEME, HOST, PORT, PATH);
		repo.setCredentials(new GerritCredentials(USER, PASSWORD));

		// Run test
		GerritClient gerrit = null;
		try {
			gerrit = GerritFactory.create(repo);
		} catch (EGerritException e) {
			fail(e.getMessage());
		}

		// Verify result
		assertNotNull(gerrit);
		if (new Version(Common.GERRIT_VERSION).getMajor() >= 3) {
			assertTrue("Wrong gerrit version", gerrit instanceof Gerrit_3_0);
		} else {
			assertTrue("Wrong gerrit version", gerrit instanceof Gerrit_2_11);
		}
	}

}
