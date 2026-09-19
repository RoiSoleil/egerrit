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

package org.eclipse.egerrit.internal.ui.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * A link to a change or to a file of a change in the web UI of a Gerrit server.
 * <p>
 * The supported URL formats are:
 * <ul>
 * <li>Gerrit 3.x: <code>#/c/&lt;project&gt;/+/&lt;change&gt;/&lt;patchset&gt;/&lt;path&gt;</code></li>
 * <li>Gerrit 2.x: <code>#/c/&lt;change&gt;/&lt;patchset&gt;/&lt;path&gt;</code></li>
 * </ul>
 *
 * @since 1.4
 */
public class GerritWebLink {

	private static final String CHANGE_SEGMENT = "/c/"; //$NON-NLS-1$

	private static final String PROJECT_MARKER = "/+/"; //$NON-NLS-1$

	private final String fProject;

	private final String fChangeNumber;

	private final String fPatchset;

	private final String fPath;

	private GerritWebLink(String project, String changeNumber, String patchset, String path) {
		fProject = project;
		fChangeNumber = changeNumber;
		fPatchset = patchset;
		fPath = path;
	}

	/**
	 * Parse a Gerrit web UI link. Both full URLs and relative hash links are supported.
	 *
	 * @param link
	 *            the link to parse
	 * @return the parsed link or <code>null</code> if the link does not point to a change
	 */
	public static GerritWebLink parse(String link) {
		if (link == null) {
			return null;
		}
		String value = link.trim();

		//Gerrit 2.x uses a hash in the URL (#/c/...): look for the change in the fragment first
		int hashIndex = value.lastIndexOf('#');
		if (hashIndex >= 0 && value.indexOf(CHANGE_SEGMENT, hashIndex) >= 0) {
			value = value.substring(hashIndex + 1);
		}

		//Drop everything before the /c/ segment (scheme, host, query string, ...)
		int changeIndex = value.indexOf(CHANGE_SEGMENT);
		if (changeIndex < 0) {
			return null;
		}
		value = value.substring(changeIndex + CHANGE_SEGMENT.length());

		//The path of the file may contain a query string or a fragment
		int end = value.length();
		int queryIndex = value.indexOf('?');
		if (queryIndex >= 0) {
			end = queryIndex;
		}
		int fragmentIndex = value.indexOf('#');
		if (fragmentIndex >= 0 && fragmentIndex < end) {
			end = fragmentIndex;
		}
		value = value.substring(0, end);

		//Gerrit 3.x links contain the project: <project>/+/<change>/<patchset>/<path>
		String project = null;
		int projectMarker = value.indexOf(PROJECT_MARKER);
		if (projectMarker >= 0) {
			project = value.substring(0, projectMarker);
			value = value.substring(projectMarker + PROJECT_MARKER.length());
		}

		String[] segments = value.split("/", 3); //$NON-NLS-1$
		String changeNumber = segments[0];
		if (!changeNumber.matches("\\d+")) { //$NON-NLS-1$
			return null;
		}
		String patchset = segments.length > 1 && !segments[1].isEmpty() ? segments[1] : null;
		String path = segments.length > 2 && !segments[2].isEmpty() ? decode(segments[2]) : null;
		return new GerritWebLink(project, changeNumber, patchset, path);
	}

	private static String decode(String value) {
		try {
			//URLDecoder converts '+' to a space, which is wrong for a path
			return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			return value;
		}
	}

	/**
	 * @return the project of the change or <code>null</code> if the link does not contain it (Gerrit 2.x)
	 */
	public String getProject() {
		return fProject;
	}

	/**
	 * @return the change number
	 */
	public String getChangeNumber() {
		return fChangeNumber;
	}

	/**
	 * @return the patchset number, "edit" or <code>null</code> if the link does not contain it
	 */
	public String getPatchset() {
		return fPatchset;
	}

	/**
	 * @return the path of the file in the change or <code>null</code> if the link does not target a file
	 */
	public String getPath() {
		return fPath;
	}

	/**
	 * @return the patchset as an integer or -1 if the link does not contain a numeric patchset
	 */
	public int getPatchsetNumber() {
		if (fPatchset != null && fPatchset.matches("\\d+")) { //$NON-NLS-1$
			return Integer.parseInt(fPatchset);
		}
		return -1;
	}

	/**
	 * @return <code>true</code> if the path points to a magic file (like COMMIT_MSG) which does not exist in the local
	 *         repository
	 */
	public boolean isMagicPath() {
		return fPath != null && (fPath.startsWith("/") //$NON-NLS-1$
				|| fPath.equals("COMMIT_MSG") //$NON-NLS-1$
				|| fPath.equals("MERGE_LIST")); //$NON-NLS-1$
	}
}
