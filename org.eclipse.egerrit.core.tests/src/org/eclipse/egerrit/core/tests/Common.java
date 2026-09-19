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

import org.osgi.framework.Version;

/**
 * Common stuff for the test cases
 *
 * @since 1.0
 */
@SuppressWarnings("nls")
public class Common {

	// ------------------------------------------------------------------------
	// Constants
	// ------------------------------------------------------------------------
	private static String getProperty(String name, String defaultValue) {
		String value = System.getProperty(name);
		if (value == null || value.trim().length() == 0) {
			return defaultValue;
		}
		return value.trim();
	}

	static String initHost() {
		return getProperty("EGerritGerritTestServerHost", "localhost");
	}

	static int initPort() {
		return Integer.valueOf(getProperty("EGerritGerritTestServerPort", "28112"));
	}

	static String initUser() {
		return getProperty("EGerritGerritTestServerUser", "admin");
	}

	static String initPassword() {
		return getProperty("EGerritGerritTestServerPassword", "egerritTest");
	}

	static String initProject() {
		return getProperty("EGerritGerritTestServerProject", "egerrit/test");
	}

	static String initEmail() {
		return getProperty("EGerritGerritTestServerEmail", "admin@localhost");
	}

	static String initVersion() {
		return getProperty("EGerritGerritTestServerVersion", "2.11.5");
	}

	public static final String SCHEME = getProperty("EGerritGerritTestServerScheme", "http");

	public static final String HOST = initHost();

	public static final int PORT = initPort();

	public static final String PATH = "";

	public static final String TEST_PROJECT = initProject();

	public static final String USER = initUser();

	public static final String PASSWORD = initPassword();

	public static final String EMAIL = initEmail();

	public static final String GERRIT_VERSION = initVersion();

	public static final String CHANGES_PATH = PATH + "/changes/";

	/**
	 * The version of the server under test.
	 */
	public static final Version VERSION = Version.parseVersion(GERRIT_VERSION);

	/**
	 * Gerrit 3.x servers.
	 */
	public static final boolean IS_GERRIT_3 = VERSION.getMajor() >= 3;

	/**
	 * Draft changes were removed in Gerrit 2.16 and replaced by work-in-progress changes. The
	 * <code>refs/drafts/*</code> refs and the draft change REST endpoints do not exist anymore on
	 * newer servers.
	 */
	public static final boolean SUPPORTS_DRAFT_CHANGES = VERSION.compareTo(new Version(2, 16, 0)) < 0;

	/**
	 * The user used as a reviewer by the tests.
	 */
	public static final String REVIEWER = getProperty("EGerritGerritTestServerReviewer", "test1");
}
