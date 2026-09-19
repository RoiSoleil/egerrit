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

package org.eclipse.egerrit.internal.ui.table.provider;

import java.util.ArrayList;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.Observables;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.tabs.ObservableCollector;
import org.eclipse.egerrit.internal.ui.utils.DataConverter;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.databinding.FeaturePath;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * This class handle the user selection of a patch set
 */
public class PatchSetHandlerProvider {

	private ChangeInfo fChangeInfo;

	private GerritClient fGerritClient;

	private Label patchsetlabel;

	private DataBindingContext bindingContext = new DataBindingContext();

	private ObservableCollector observableCollector;

	/**
	 * Create a button and add all handling for this patchset selection button
	 *
	 * @param parent
	 * @param changeInfo
	 * @param fGerritClient
	 * @return Button
	 */
	public Button create(Composite parent, ChangeInfo changeInfo, GerritClient gerritClient) {
		this.fChangeInfo = changeInfo;
		this.fGerritClient = gerritClient;
		patchsetlabel = new Label(parent, SWT.NONE);

		Button fButtonPatchSet = new Button(parent, SWT.DROP_DOWN | SWT.ARROW | SWT.DOWN);
		fButtonPatchSet.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		fButtonPatchSet.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MenuManager mgr = new MenuManager();
				fillMenuItemForChangeInfo(mgr);
				mgr.createContextMenu(parent).setVisible(true);
			}
		});
		patchsetSelectionBinding();
		return fButtonPatchSet;
	}

	private void fillMenuItemForChangeInfo(MenuManager menu) {
		ArrayList<RevisionInfo> revisions = new ArrayList<>(fChangeInfo.getRevisions().values());
		revisions.sort((o1, o2) -> o2.get_number() - o1.get_number());
		revisions.stream().forEach(rev -> menu.add(new SwitchCurrentPathsetAction(fChangeInfo, rev)));
	}

	/**
	 * Add binding to this patch set button
	 */
	private void patchsetSelectionBinding() {
		FeaturePath selectRevision = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION);
		IObservableValue observerValue = EMFProperties.value(selectRevision).observe(fChangeInfo);

		bindingContext.bindValue(Observables.observeDelayedValue(1000, WidgetProperties.text().observe(patchsetlabel)),
				Observables.observeDelayedValue(1000, observerValue), null,
				new UpdateValueStrategy().setConverter(DataConverter.patchSetSelected(fChangeInfo, fGerritClient)));

		//See when a REBASE occurs, the current revision is updated
		IObservableValue observerRevisionsValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__CURRENT_REVISION).observe(fChangeInfo);

		bindingContext.bindValue(WidgetProperties.text().observe(patchsetlabel), observerRevisionsValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.patchSetSelected(fChangeInfo, fGerritClient)));
		observableCollector = new ObservableCollector(bindingContext);
	}

	public void dispose() {
		observableCollector.dispose();
		bindingContext.dispose();
	}
}
