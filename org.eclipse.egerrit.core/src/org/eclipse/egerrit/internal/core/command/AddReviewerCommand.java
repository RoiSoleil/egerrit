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

package org.eclipse.egerrit.internal.core.command;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.eclipse.egerrit.internal.core.GerritRepository;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.AddReviewerInput;
import org.eclipse.egerrit.internal.core.rest.AddReviewerResult;

/**
 * The command: POST /changes/{change-id}/reviewers
 * <p>
 * http://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#add-reviewer
 *
 * @since 1.0
 */
public class AddReviewerCommand extends BaseCommandChangeWithInput<AddReviewerResult, AddReviewerInput> {

	/**
	 * Construct a add reviewer command
	 *
	 * @param gerritRepository
	 *            the gerrit repository
	 * @param id
	 *            the change-id
	 */
	public AddReviewerCommand(GerritRepository gerritRepository, String id) {
		super(gerritRepository, AuthentificationRequired.YES, HttpPost.class, AddReviewerResult.class, id);
		setPathFormat("/changes/{change-id}/reviewers"); //$NON-NLS-1$
	}

	@Override
	protected boolean handleHttpException(ClientProtocolException exception) throws EGerritException {
		if (exception instanceof HttpResponseException) {
			HttpResponseException httpException = (HttpResponseException) exception;
			//Gerrit 2.x returns 422 while Gerrit 3.x returns 400 when the reviewer cannot be found
			if (httpException.getStatusCode() == 422 || httpException.getStatusCode() == 400) {
				EGerritException gerritException = new EGerritException(exception.getLocalizedMessage());
				gerritException.setCode(EGerritException.SHOWABLE_MESSAGE);
				throw gerritException;
			}
		}
		return false;
	}
}
