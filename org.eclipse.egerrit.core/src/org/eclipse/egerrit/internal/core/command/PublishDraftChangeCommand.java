/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ericsson - Initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.core.command;

import org.apache.http.client.methods.HttpPost;
import org.eclipse.egerrit.internal.core.GerritRepository;
import org.osgi.framework.Version;

/**
 * The command POST /changes/{change-id}/publish
 * <p>
 * http://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#publish-draft-change
 * <p>
 * Draft changes were removed in Gerrit 2.16. On Gerrit 3.x and later, the equivalent operation is
 * to mark the work-in-progress change as ready for review: POST /changes/{change-id}/ready
 *
 * @since 1.0
 */
public class PublishDraftChangeCommand extends BaseCommandChange<String> {

	/**
	 * The constructor
	 *
	 * @param gerritRepository
	 *            the gerrit repository
	 * @param changeId
	 *            the change-id
	 */
	public PublishDraftChangeCommand(GerritRepository gerritRepository, String changeId) {
		super(gerritRepository, AuthentificationRequired.YES, HttpPost.class, String.class, changeId);
		Version version = gerritRepository.getVersion();
		if (version != null && version.getMajor() >= 3) {
			//Draft changes do not exist anymore, publish the work-in-progress change
			setPathFormat("/changes/{change-id}/ready"); //$NON-NLS-1$
		} else {
			setPathFormat("/changes/{change-id}/publish"); //$NON-NLS-1$
		}
	}

}
