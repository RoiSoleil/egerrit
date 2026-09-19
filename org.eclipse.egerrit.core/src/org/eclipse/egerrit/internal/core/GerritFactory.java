/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Francois Chouinard - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.core;

import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.osgi.framework.Version;

/**
 * The GerritFactory instantiates a {@link GerritClient} object that matches the gerrit server version.
 * <p>
 * If there is no exact match for the repository version, the factory creates an object with version <= repository
 * version.
 *
 * @since 1.0
 */
public final class GerritFactory {

	// --------------------------------------------------------------------
	// Constants
	// --------------------------------------------------------------------

	/**
	 * The minimal version supported
	 */
	public static final Version MINIMAL_VERSION = new Version(2, 9, 0);

	// ------------------------------------------------------------------------
	// The factory
	// ------------------------------------------------------------------------

	/**
	 * Instantiate a proper Gerrit instance based on the repository version.
	 *
	 * @param gerritRepository
	 *            The repository to interface with (can not be null)
	 * @return the appropriate gerrit object
	 * @throws EGerritException
	 */
	public static GerritClient create(GerritRepository gerritRepository) throws EGerritException {

		// --------------------------------------------------------------------
		// Quick validations
		// --------------------------------------------------------------------

		if (gerritRepository == null) {
			throw new EGerritException("Invalid gerrit repository"); //$NON-NLS-1$
		}
		gerritRepository.connect();
		Version version = gerritRepository.getVersion();
		if (version == null) {
			return null; // Invalid Credential or server not available
		}
		if (version.compareTo(MINIMAL_VERSION) < 0) {
			throw new EGerritException("Unsupported gerrit version (< " + MINIMAL_VERSION.toString() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return instantiateGerritClient(gerritRepository, version);

	}

	/**
	 * Find the proper version of the GerritClient.
	 *
	 * @param gerritRepository
	 * @param version
	 * @return GerritClient
	 * @throws EGerritException
	 */
	private static GerritClient instantiateGerritClient(GerritRepository gerritRepository, Version version)
			throws EGerritException {
		// --------------------------------------------------------------------
		// Instantiate the correct gerrit instance based on its version
		//
		// The version has the form: [major].[minor].[micro].[qualifier]
		//
		// We first try to match the version "as-is". If it doesn't match, we
		// test again after removing the [qualifier].
		//
		// If it still doesn't match, we iteratively test by decrementing
		// [micro], then [minor], then [major] until a match is found.
		//
		// If no match is found, we return the most basic version supported.
		// --------------------------------------------------------------------

		GerritClient gerrit = null;
		Version workVersion = new Version(version.getMajor(), version.getMinor(), version.getMicro(),
				version.getQualifier());
		do {
			switch (workVersion.toString()) {

			case Gerrit_2_9.GERRIT_VERSION:
				gerrit = new Gerrit_2_9(gerritRepository);
				break;
			case Gerrit_2_11.GERRIT_VERSION:
				gerrit = new Gerrit_2_11(gerritRepository);
				break;
			case Gerrit_3_0.GERRIT_VERSION:
				gerrit = new Gerrit_3_0(gerritRepository);
				break;

			default:
				// Downgrade the version as per logic above
				if (!workVersion.getQualifier().isEmpty()) {
					// Remove [qualifier]
					workVersion = new Version(version.getMajor(), version.getMinor(), version.getMicro(), null);
				} else if (workVersion.getMicro() > 0) {
					// Decrement [micro]
					workVersion = new Version(version.getMajor(), version.getMinor(), workVersion.getMicro() - 1);
				} else if (workVersion.getMinor() > 0) {
					// Decrement [minor]
					workVersion = new Version(version.getMajor(), workVersion.getMinor() - 1, 0);
				} else if (workVersion.getMajor() > MINIMAL_VERSION.getMajor()) {
					// Decrement [major]
					workVersion = new Version(workVersion.getMajor() - 1, 0, 0);
				} else {
					// Return the most basic version supported
					// We should get here if the check against MINIMAL_VERSION was done
					workVersion = MINIMAL_VERSION;
				}
				break;
			}

		} while (gerrit == null);
		return gerrit;
	}

	// ------------------------------------------------------------------------
	// Constructor
	// ------------------------------------------------------------------------

	/**
	 * The default constructor. Do not allow to build an object of this class
	 */
	private GerritFactory() {
	}

}
