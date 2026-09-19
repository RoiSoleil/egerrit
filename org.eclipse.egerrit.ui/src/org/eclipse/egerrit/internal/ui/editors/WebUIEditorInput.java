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

package org.eclipse.egerrit.internal.ui.editors;

import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.ui.EGerritImages;
import org.eclipse.egerrit.internal.ui.editors.model.ChangeDetailEditorInput;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Editor input feeding a {@link WebUIEditor}.
 *
 * @since 1.4
 */
public class WebUIEditorInput extends ChangeDetailEditorInput {

	public WebUIEditorInput(GerritClient server, ChangeInfo change) {
		super(server, change);
	}

	@Override
	public String getName() {
		return getChange().get_number() + " - " + getChange().getSubject() + Messages.WebUIEditor_tabSuffix; //$NON-NLS-1$
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return EGerritImages.getDescriptor(EGerritImages.WEB_UI_IMAGE);
	}
}
