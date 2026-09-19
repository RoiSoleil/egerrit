/*******************************************************************************
 * Copyright (c) 2015 Ericsson AB.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jacques Bouthillier - initial API and implementation
 *******************************************************************************/

package org.eclipse.egerrit.internal.ui.tabs;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.property.Properties;
import org.eclipse.core.databinding.property.value.IValueProperty;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.ChangeMessageInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.ui.table.UIFilesTable;
import org.eclipse.egerrit.internal.ui.table.UIHistoryTable;
import org.eclipse.egerrit.internal.ui.table.provider.HistoryTableLabelProvider;
import org.eclipse.egerrit.internal.ui.utils.DataConverter;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.databinding.FeaturePath;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

/**
 * This class is used in the editor to handle the Gerrit history view <a>http://git.eclipse.org/r</a>
 *
 * @since 1.0
 */
public class HistoryTabView {

	private static final String HISTORY_FILES = "egerrit.HistoryFiles"; //$NON-NLS-1$

	private TableViewer tableHistoryViewer;

	private TextViewerWithLinks msgTextData;

	private GerritClient fGerritClient;

	private ChangeInfo fChangeInfo;

	private UIFilesTable tableUIFiles;

	private DataBindingContext dataBindingContext = new DataBindingContext();

	private ObservableCollector observableCollector = null;

	/**
	 * The constructor.
	 *
	 * @param fChangeInfo
	 * @param fGerritClient
	 */
	public HistoryTabView(GerritClient gerritClient, ChangeInfo changeInfo) {
		this.fGerritClient = gerritClient;
		this.fChangeInfo = changeInfo;
	}

	/**
	 * @param tabFolder
	 *            List<ChangeMessageInfo>
	 */
	public void create(TabFolder tabFolder) {

		createControls(tabFolder);
	}

	private void createControls(TabFolder tabFolder) {
		TabItem tbtmHistory = new TabItem(tabFolder, SWT.NONE);
		tbtmHistory.setText(Messages.HistoryTabView_0);

		SashForm sashForm = new SashForm(tabFolder, SWT.VERTICAL);
		tbtmHistory.setControl(sashForm);

		UIHistoryTable tableUIHistory = new UIHistoryTable(fGerritClient);
		tableUIHistory.createTableViewerSection(sashForm);

		tableHistoryViewer = tableUIHistory.getViewer();
		SashForm sashFormHorizon = new SashForm(sashForm, SWT.HORIZONTAL);

		msgTextData = new TextViewerWithLinks(sashFormHorizon, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL);
		msgTextData.setEditable(false);
		tableUIFiles = new UIFilesTable(fGerritClient, fChangeInfo, HISTORY_FILES);
		tableUIFiles.createTableViewerSection(sashFormHorizon);

		//Set the % of display data.40% table and 60% for the comment message
		sashFormHorizon.setWeights(new int[] { 40, 60 });

		msgTextData.configure(new SourceViewerConfiguration() {
			@Override
			public int getHyperlinkStateMask(ISourceViewer sourceViewer) {
				return SWT.NONE;
			}

			@Override
			public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
				return new IHyperlinkDetector[] { new HyperLinkDetector(fGerritClient, fChangeInfo) };
			}

			@Override
			public IHyperlinkPresenter getHyperlinkPresenter(final ISourceViewer sourceViewer) {
				return new HyperLinkPresenter(new RGB(0, 0, 255), sourceViewer, fGerritClient, fChangeInfo);
			}
		});

		//Set the % of display data.40% table and 60% for the comment message
		sashForm.setWeights(new int[] { 40, 60 });
		bind(fChangeInfo);
	}

	private void bind(ChangeInfo changeInfo) {
		ObservableListContentProvider contentProvider = new ObservableListContentProvider();
		tableHistoryViewer.setContentProvider(contentProvider);

		final FeaturePath authorName = FeaturePath.fromList(ModelPackage.Literals.CHANGE_MESSAGE_INFO__AUTHOR,
				ModelPackage.Literals.ACCOUNT_INFO__NAME);
		final IObservableMap[] watchedProperties = Properties.observeEach(contentProvider.getKnownElements(),
				new IValueProperty[] { EMFProperties.value(ModelPackage.Literals.CHANGE_MESSAGE_INFO__DATE),
						EMFProperties.value(authorName),
						EMFProperties.value(ModelPackage.Literals.CHANGE_MESSAGE_INFO__MESSAGE) });
		tableHistoryViewer.setLabelProvider(new HistoryTableLabelProvider(watchedProperties, fGerritClient));
		tableHistoryViewer
				.setInput(EMFProperties.list(ModelPackage.Literals.CHANGE_INFO__MESSAGES).observe(changeInfo));

		//Hook the selection listener to display the details in the bottom section
		IObservableValue selection = ViewerProperties.singleSelection().observe(tableHistoryViewer);
		IObservableValue textViewerDocument = BeanProperties.value(TextViewerWithLinks.class, "document") //$NON-NLS-1$
				.observe(Realm.getDefault(), msgTextData);
		UpdateValueStrategy textToDocumentStrategy = new UpdateValueStrategy();
		textToDocumentStrategy.setConverter(DataConverter.fromStringToDocument(fGerritClient));
		dataBindingContext.bindValue(textViewerDocument, selection, null, textToDocumentStrategy);

		//Automatically update the user selected revision as the user changes the selection
		IObservableValue settableUserRevision = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION).observe(changeInfo);
		dataBindingContext.bindValue(settableUserRevision, selection, null, new UpdateValueStrategy() {
			@Override
			public Object convert(Object value) {
				if (value == null) {
					return null;
				}
				//-> from the message info to the revision
				ChangeMessageInfo message = (ChangeMessageInfo) value;
				ChangeInfo containingChange = (ChangeInfo) message.eContainer();
				return containingChange.getRevisionByNumber(message.get_revision_number());
			}
		});
		observableCollector = new ObservableCollector(dataBindingContext);
	}

	public void dispose() {
		observableCollector.dispose();
		dataBindingContext.dispose();
		tableUIFiles.dispose();
	}
}
