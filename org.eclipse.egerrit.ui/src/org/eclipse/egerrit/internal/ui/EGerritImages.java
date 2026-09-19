/*******************************************************************************
 * Copyright (c) 2016 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.ui;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;

/**
 * Helper class to handle images
 */
public class EGerritImages {

	private static ImageRegistry fgImageRegistry = new ImageRegistry();

	public static final String AUTHOR_COMMENT = "icons/showAuthorComments.gif"; //$NON-NLS-1$

	public static final String ANONYMOUS_COMMENT = "icons/showAnonymousComments.gif"; //$NON-NLS-1$

	public static final String DELETE_QUICKFIX = "icons/delete.gif"; //$NON-NLS-1$

	public static final String EDIT_QUICKFIX = "icons/edit.gif"; //$NON-NLS-1$

	public static final String DOWN_ARROW = "icons/compare_next_file.gif"; //$NON-NLS-1$

	public static final String UP_ARROW = "icons/compare_previous_file.gif"; //$NON-NLS-1$

	public static final String REPLY = "icons/reply.png"; //$NON-NLS-1$

	public static final String COMMENT_FILTER = "icons/showComments.gif"; //$NON-NLS-1$

	public static final String TOGGLE_FILEPATH = "icons/toggleFilePathpane.gif"; //$NON-NLS-1$

	public static final String CHECKED_IMAGE = "icons/greenCheck.png"; //$NON-NLS-1$

	public static final String SHOW_REVIEW_EDITOR_IMAGE = "icons/EGerritcolor16.png"; //$NON-NLS-1$

	public static final String WEB_UI_IMAGE = "icons/internal_browser.png"; //$NON-NLS-1$

	public static final String NEXT_COMMENT_ANNOTATION_ICON_FILE = "icons/nextComment_menu.gif"; //$NON-NLS-1$

	public static final String PREVIOUS_COMMENT_ANNOTATION_ICON_FILE = "icons/previousComment_menu.gif"; //$NON-NLS-1$

	/**
	 * The default constructor. Do not allow to build an object of this class
	 */
	private EGerritImages() {
	}

	static {
		fgImageRegistry.put(AUTHOR_COMMENT, EGerritUIPlugin.getImageDescriptor(AUTHOR_COMMENT));
		fgImageRegistry.put(ANONYMOUS_COMMENT, EGerritUIPlugin.getImageDescriptor(ANONYMOUS_COMMENT));
		fgImageRegistry.put(DELETE_QUICKFIX, EGerritUIPlugin.getImageDescriptor(DELETE_QUICKFIX));
		fgImageRegistry.put(EDIT_QUICKFIX, EGerritUIPlugin.getImageDescriptor(EDIT_QUICKFIX));
		fgImageRegistry.put(REPLY, EGerritUIPlugin.getImageDescriptor(REPLY));
		fgImageRegistry.put(DOWN_ARROW, EGerritUIPlugin.getImageDescriptor(DOWN_ARROW));
		fgImageRegistry.put(UP_ARROW, EGerritUIPlugin.getImageDescriptor(UP_ARROW));
		fgImageRegistry.put(COMMENT_FILTER, EGerritUIPlugin.getImageDescriptor(COMMENT_FILTER));
		fgImageRegistry.put(TOGGLE_FILEPATH, EGerritUIPlugin.getImageDescriptor(TOGGLE_FILEPATH));
		fgImageRegistry.put(CHECKED_IMAGE, EGerritUIPlugin.getImageDescriptor(CHECKED_IMAGE));
		fgImageRegistry.put(SHOW_REVIEW_EDITOR_IMAGE, EGerritUIPlugin.getImageDescriptor(SHOW_REVIEW_EDITOR_IMAGE));
		fgImageRegistry.put(WEB_UI_IMAGE, EGerritUIPlugin.getImageDescriptor(WEB_UI_IMAGE));
		fgImageRegistry.put(NEXT_COMMENT_ANNOTATION_ICON_FILE,
				EGerritUIPlugin.getImageDescriptor(NEXT_COMMENT_ANNOTATION_ICON_FILE));
		fgImageRegistry.put(PREVIOUS_COMMENT_ANNOTATION_ICON_FILE,
				EGerritUIPlugin.getImageDescriptor(PREVIOUS_COMMENT_ANNOTATION_ICON_FILE));
	}

	/**
	 * Returns the image descriptor for the given key in this registry. Might be called in a non-UI thread.
	 *
	 * @param key
	 *            the image's key
	 * @return the image descriptor for the given key
	 */
	public static ImageDescriptor getDescriptor(String key) {
		return fgImageRegistry.getDescriptor(key);
	}

	/**
	 * Returns the image managed under the given key in this registry.
	 *
	 * @param key
	 *            the image's key
	 * @return the image managed under the given key
	 */
	public static Image get(String key) {
		return fgImageRegistry.get(key);
	}
}
