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
import java.util.Collection;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.DeleteDraftRevisionCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ModelHelpers;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.model.RevisionInfo;
import org.eclipse.egerrit.internal.ui.editors.ModelLoader;
import org.eclipse.egerrit.internal.ui.tabs.ObservableCollector;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * @author lmcbout
 */
public class DeleteDraftRevisionProvider {

	private Button fDeleteDraftRevisionButton;

	private DataBindingContext bindingContext = new DataBindingContext();

	private ObservableCollector observableCollector;

	public void create(Composite parent, GerritClient gerritClient, ChangeInfo changeInfo) {
		fDeleteDraftRevisionButton = new Button(parent, SWT.BORDER);
		fDeleteDraftRevisionButton.setText(Messages.DeleteDraft_Text);
		GridData gdDeleteDraft = new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1);
		fDeleteDraftRevisionButton.setLayoutData(gdDeleteDraft);
		fDeleteDraftRevisionButton.setToolTipText(Messages.DeleteDraft_Tip);

		IObservableValue revisionInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION)
				.value(ModelPackage.Literals.REVISION_INFO__DELETEABLE)
				.observe(changeInfo);
		bindingContext.bindValue(WidgetProperties.enabled().observe(fDeleteDraftRevisionButton),
				revisionInfoObserveValue, null, null);

		fDeleteDraftRevisionButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!MessageDialog.openConfirm(fDeleteDraftRevisionButton.getParent().getShell(),
						Messages.DeleteDraft_Dialogue_Title, NLS.bind(Messages.DeleteDraft_Dialogue_Message,
								changeInfo.getUserSelectedRevision().get_number(), changeInfo.getSubject()))) {
					return;
				}
				DeleteDraftRevisionCommand deleteDraftChangeCmd = gerritClient.deleteDraftRevision(changeInfo.getId(),
						changeInfo.getUserSelectedRevision().getId());
				try {
					deleteDraftChangeCmd.call();
					if (changeInfo.getRevisions().size() == 1) {
						//We are deleting the last draft, close the editor
						IWorkbench workbench = PlatformUI.getWorkbench();
						final IWorkbenchPage activePage = workbench.getActiveWorkbenchWindow().getActivePage();

						IEditorPart editor = activePage.getActiveEditor();
						activePage.closeEditor(editor, false);
					} else {
						//Change the user selected revision to the most recent one
						Collection<RevisionInfo> revisions = new ArrayList<>(changeInfo.getRevisions().values());
						revisions.remove(changeInfo.getUserSelectedRevision());
						changeInfo.setUserSelectedRevision(
								changeInfo.getRevisionByNumber(ModelHelpers.getHighestRevisionNumber(revisions)));
						ModelLoader ml = ModelLoader.initialize(gerritClient, changeInfo);
						ml.reload(false);
						ml.dispose();
					}
				} catch (EGerritException e1) {
					EGerritCorePlugin.logError(gerritClient.getRepository().formatGerritVersion() + e1.getMessage());
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// ignore
			}
		});
		observableCollector = new ObservableCollector(bindingContext);
	}

	public void dispose() {
		observableCollector.dispose();
		bindingContext.dispose();
	}
}
