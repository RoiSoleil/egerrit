/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jacques Bouthillier - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.core;

import org.eclipse.egerrit.internal.core.exception.EGerritException;

/**
 * Gerrit v3.0 and above (same as the base class)
 *
 * @since 1.4
 */
public class Gerrit_3_0 extends GerritClient {

	/**
	 * This gerrit version
	 */
	static final String GERRIT_VERSION = "3.0.0"; //$NON-NLS-1$

	// ------------------------------------------------------------------------
	// Constructors
	// ------------------------------------------------------------------------

	/**
	 * The constructor
	 *
	 * @param gerritRepository
	 *            the gerrit repository
	 */
	Gerrit_3_0(GerritRepository gerritRepository) throws EGerritException {
		super(gerritRepository);
	}

	// ------------------------------------------------------------------------
	// Overridden repository queries (i.e. version variants)
	// ------------------------------------------------------------------------

}
