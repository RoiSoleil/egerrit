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

import java.text.SimpleDateFormat;
import java.util.Iterator;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.value.ComputedValue;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.ChangeCommitMsgCommand;
import org.eclipse.egerrit.internal.core.command.PublishChangeEditCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.ChangeEditMessageInput;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.CommitInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.ui.editors.ModelLoader;
import org.eclipse.egerrit.internal.ui.utils.DataConverter;
import org.eclipse.egerrit.internal.ui.utils.EGerritConstants;
import org.eclipse.egerrit.internal.ui.utils.LinkDashboard;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.eclipse.emf.databinding.EMFObservables;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.databinding.FeaturePath;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

/**
 * This class is used in the editor to handle the Gerrit message view
 *
 * @since 1.0
 */
public class MessageTabView {

	private Text msgTextData;

	private Link msgAuthorData;

	private Label msgDatePushData;

	private Link msgCommitterData;

	private Label msgDatecommitterData;

	private Link msgCommitidData;

	private Label msgParentIdData;

	private Link msgChangeIdData;

	private final SimpleDateFormat formatTimeOut = new SimpleDateFormat("MMM d, yyyy  hh:mm a"); //$NON-NLS-1$

	private GerritClient fGerritClient;

	private ChangeInfo fChangeInfo;

	private Boolean fShowExtraInfo = true;

	private DataBindingContext bindingContext = new DataBindingContext();

	private ObservableCollector observableCollector;

	// ------------------------------------------------------------------------
	// Constructor and life cycle
	// ------------------------------------------------------------------------

	/**
	 * The constructor.
	 *
	 * @param changeInfo
	 * @param gerritClient
	 */
	public MessageTabView(GerritClient gerritClient, ChangeInfo changeInfo) {
		fGerritClient = gerritClient;
		fChangeInfo = changeInfo;
	}

	/**
	 * @param GerritClient
	 * @param Composite
	 * @param ChangeInfo
	 */
	public void create(Composite tabFolder) {

		createControls(tabFolder);
	}

	private boolean isEditingAllowed() {
		return !fGerritClient.getRepository().getServerInfo().isAnonymous();
	}

	/**
	 * Create the top parent as a tab folder
	 *
	 * @param tabFolder
	 * @return Composite
	 */
	private Composite createAsTabfolder(Composite tabFolder) {
		final TabItem tabMessages = new TabItem((TabFolder) tabFolder, SWT.NONE);
		tabMessages.setText(Messages.MessageTabView_1);

		final Composite messagesComposite = new Composite(tabFolder, SWT.NONE);
		tabMessages.setControl(messagesComposite);
		messagesComposite.setLayout(new GridLayout(4, false));
		return messagesComposite;
	}

	/**
	 * Create the top parent as a composite
	 *
	 * @param tabFolder
	 * @return Composite
	 */
	private Composite createAsComposite(Composite parent) {

		final ScrolledComposite scrollComposite = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);

		final Composite messagesComposite = new Composite(scrollComposite, SWT.NONE);
		messagesComposite.setLayout(new GridLayout(4, false));

		scrollComposite.setContent(messagesComposite);
		scrollComposite.setExpandVertical(true);
		scrollComposite.setExpandHorizontal(true);
		scrollComposite.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				scrollComposite.setMinSize(messagesComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
			}
		});

		//Do not show the Extra data
		fShowExtraInfo = false;
		return messagesComposite;
	}

	private void createControls(Composite composite) {
		final Composite messagesComposite;

		//Define the proper instance of the composite, i.e. Tabfolder or anything else
		if (composite instanceof TabFolder) {
			messagesComposite = createAsTabfolder(composite);
		} else {
			messagesComposite = createAsComposite(composite);
		}

		msgTextData = new Text(messagesComposite, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		Point fontsize = UIUtils.computeFontSize(messagesComposite);
		gridData.minimumHeight = fontsize.y * 3;//Set a minimum height to 3 lines
		msgTextData.setLayoutData(gridData);
		msgTextData.setEditable(isEditingAllowed());

		final Button btnCancel = new Button(messagesComposite, SWT.NONE);
		GridData gdBtnCancel = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		btnCancel.setLayoutData(gdBtnCancel);
		btnCancel.setText(Messages.MessageTabView_2);
		btnCancel.setEnabled(false);

		final Button fBtnSave = new Button(messagesComposite, SWT.NONE);
		GridData gdBtnSave = new GridData(SWT.LEFT, SWT.TOP, false, false, 3, 1);
		fBtnSave.setLayoutData(gdBtnSave);
		fBtnSave.setText(Messages.MessageTabView_3);
		fBtnSave.setEnabled(false);

		if (isEditingAllowed()) {
			btnCancel.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					msgTextData.setText(fChangeInfo.getUserSelectedRevision().getCommit().getMessage());
					fBtnSave.setEnabled(false);
					btnCancel.setEnabled(false);
				}
			});
		}

		if (isEditingAllowed()) {
			ModifyListener textModifiedListener = event -> {
				if (fChangeInfo.getUserSelectedRevision() != null && fChangeInfo.getUserSelectedRevision()
						.getCommit()
						.getMessage()
						.compareTo(msgTextData.getText()) != 0) {
					fBtnSave.setEnabled(true);
					btnCancel.setEnabled(true);
				} else {
					fBtnSave.setEnabled(false);
					btnCancel.setEnabled(false);
				}
			};
			msgTextData.addModifyListener(textModifiedListener);
		}

		if (fShowExtraInfo) {
			//LINE 1 - Author: <authorname> date
			Label lblAuthor = new Label(messagesComposite, SWT.NONE);
			lblAuthor.setText(Messages.MessageTabView_4);
			lblAuthor.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

			msgAuthorData = new Link(messagesComposite, SWT.NONE);
			msgAuthorData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
			msgAuthorData.setToolTipText(Messages.MessageTabView_7);
			msgAuthorData.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					LinkDashboard linkDash = new LinkDashboard(fGerritClient);
					//Need to put the text in quotes since there might be some empty spaces or special char in the user name
					linkDash.invokeRefreshDashboardCommand(EGerritConstants.OWNER,
							"\"" + e.text + "\"" + " status:open"); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
				}
			});

			msgDatePushData = new Label(messagesComposite, SWT.NONE);
			msgDatePushData.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));

			//LINE 2 - Committer: <committername> date
			Label lblCommitter = new Label(messagesComposite, SWT.NONE);
			lblCommitter.setText(Messages.MessageTabView_5);
			lblCommitter.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

			msgCommitterData = new Link(messagesComposite, SWT.NONE);
			msgCommitterData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
			msgCommitterData.setToolTipText(Messages.MessageTabView_10);
			msgCommitterData.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					LinkDashboard linkDash = new LinkDashboard(fGerritClient);
					//Need to put the text in quotes since there might be some empty spaces or special char in the user name
					linkDash.invokeRefreshDashboardCommand(EGerritConstants.OWNER,
							"\"" + e.text + "\"" + " status:open"); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
				}
			});

			msgDatecommitterData = new Label(messagesComposite, SWT.NONE);
			GridData gdDateCommitter = new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1);
			msgDatecommitterData.setLayoutData(gdDateCommitter);

			//LINE 3 - Commit: <commitId>
			Label lblCommit = new Label(messagesComposite, SWT.NONE);
			lblCommit.setText(Messages.MessageTabView_6);
			lblCommit.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

			msgCommitidData = new Link(messagesComposite, SWT.NONE);
			msgCommitidData.setToolTipText(Messages.MessageTabView_11);
			msgCommitidData.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					LinkDashboard linkDash = new LinkDashboard(fGerritClient);
					linkDash.invokeRefreshDashboardCommand("", e.text); //$NON-NLS-1$
				}
			});

			msgCommitidData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));

			//LINE 4 - Parents: <parentId>
			Label lblParents = new Label(messagesComposite, SWT.NONE);
			lblParents.setText(Messages.MessageTabView_8);
			lblParents.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

			msgParentIdData = new Label(messagesComposite, SWT.NONE);
			msgParentIdData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));

			//LINE 5 - ChangeId: <id>
			Label lblChangeid = new Label(messagesComposite, SWT.NONE);
			lblChangeid.setText(Messages.MessageTabView_9);
			lblChangeid.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

			msgChangeIdData = new Link(messagesComposite, SWT.NONE);
			msgChangeIdData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
			msgChangeIdData.setToolTipText(Messages.MessageTabView_12);
			msgChangeIdData.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					LinkDashboard linkDash = new LinkDashboard(fGerritClient);
					linkDash.invokeRefreshDashboardCommand("", e.text); //$NON-NLS-1$
				}
			});
		}

		if (isEditingAllowed()) {
			fBtnSave.addSelectionListener(new SelectionAdapter() {
				/**
				 *
				 */
				@Override
				public void widgetSelected(SelectionEvent e) {
					super.widgetSelected(e);
					ChangeCommitMsgCommand editMessageCmd = fGerritClient.editMessage(fChangeInfo.getId());
					ChangeEditMessageInput changeEditMessageInput = new ChangeEditMessageInput();
					changeEditMessageInput.setMessage(msgTextData.getText());
					editMessageCmd.setCommandInput(changeEditMessageInput);
					PublishChangeEditCommand publishChangeEditCmd = fGerritClient
							.publishChangeEdit(fChangeInfo.getId());

					try {
						editMessageCmd.call();
						publishChangeEditCmd.call();
					} catch (EGerritException e3) {
						EGerritCorePlugin
								.logError(fGerritClient.getRepository().formatGerritVersion() + e3.getMessage());
					}
					fBtnSave.setEnabled(false);
					btnCancel.setEnabled(false);
					ModelLoader loader = ModelLoader.initialize(fGerritClient, fChangeInfo);
					loader.loadBasicInformation(false);
					loader.dispose();
				}
			});
		}

		if (fShowExtraInfo) {
			new Label(messagesComposite, SWT.NONE);
		}

		//Set the binding for this section
		msgTabDataBindings();
	}

	private void msgTabDataBindings() {
		bindCommitMessage();
		if (!fShowExtraInfo) {
			observableCollector = new ObservableCollector(bindingContext);
			return;// Do not bind any other functionality when not shown
		}

		bindComitAuthor();
		bindCommitDate();
		bindCommitterName();
		bindCommitterDate();
		bindCommitId();
		bindCommitParents();
		bindChangeId();
		observableCollector = new ObservableCollector(bindingContext);
	}

	/**
	 *
	 */
	private void bindChangeId() {
		//Show change id
		IObservableValue<String> msgChangeIdDataValue = EMFObservables.observeValue(fChangeInfo,
				ModelPackage.Literals.CHANGE_INFO__CHANGE_ID);
		bindingContext.bindValue(WidgetProperties.text().observe(msgChangeIdData), msgChangeIdDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.linkText()));
	}

	/**
	 *
	 */
	private void bindCommitParents() {
		//show commit parents
		FeaturePath commitParents = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION,
				ModelPackage.Literals.REVISION_INFO__COMMIT, ModelPackage.Literals.COMMIT_INFO__PARENTS);
		final IObservableList parents = EMFProperties.list(commitParents).observe(fChangeInfo);
		ComputedValue<String> parentCommitsAsString = new ComputedValue<String>() {
			@Override
			protected String calculate() {
				Iterator it = parents.iterator();
				StringBuilder result = new StringBuilder();
				while (it.hasNext()) {
					CommitInfo info = (CommitInfo) it.next();
					result.append(info.getCommit());
					result.append(" "); //$NON-NLS-1$
				}
				return result.toString().trim();
			}
		};
		bindingContext.bindValue(WidgetProperties.text().observe(msgParentIdData), parentCommitsAsString, null, null);
	}

	/**
	 *
	 */
	private void bindCommitId() {
		//show commit id
		final FeaturePath commitId = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION,
				ModelPackage.Literals.REVISION_INFO__COMMIT, ModelPackage.Literals.COMMIT_INFO__COMMIT);
		IObservableValue<String> msgCommitidDataValue = EMFProperties.value(commitId).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgCommitidData), msgCommitidDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.linkText()));
	}

	/**
	 *
	 */
	private void bindCommitterDate() {
		//Show committer date
		final FeaturePath commiterDate = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION,
				ModelPackage.Literals.REVISION_INFO__COMMIT, ModelPackage.Literals.COMMIT_INFO__COMMITTER,
				ModelPackage.Literals.GIT_PERSON_INFO__DATE);
		IObservableValue<String> msgCommitterDateDataValue = EMFProperties.value(commiterDate).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgDatecommitterData), msgCommitterDateDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.gerritTimeConverter(formatTimeOut)));
	}

	/**
	 *
	 */
	private void bindCommitterName() {
		//Show committer name
		final FeaturePath committerName = FeaturePath.fromList(
				ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION, ModelPackage.Literals.REVISION_INFO__COMMIT,
				ModelPackage.Literals.COMMIT_INFO__COMMITTER);
		IObservableValue<String> msgCommitterDataValue = EMFProperties.value(committerName).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgCommitterData), msgCommitterDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.gitPersonConverter()));
	}

	/**
	 *
	 */
	private void bindCommitDate() {
		final FeaturePath commitDate = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION,
				ModelPackage.Literals.REVISION_INFO__COMMIT, ModelPackage.Literals.COMMIT_INFO__AUTHOR,
				ModelPackage.Literals.GIT_PERSON_INFO__DATE);
		IObservableValue<String> msgAuthorDateDataValue = EMFProperties.value(commitDate).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgDatePushData), msgAuthorDateDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.gerritTimeConverter(formatTimeOut)));
	}

	/**
	 *
	 */
	private void bindComitAuthor() {
		//show commit author
		final FeaturePath authorName = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION,
				ModelPackage.Literals.REVISION_INFO__COMMIT, ModelPackage.Literals.COMMIT_INFO__AUTHOR);
		IObservableValue<String> msgAuthorDataValue = EMFProperties.value(authorName).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgAuthorData), msgAuthorDataValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.gitPersonConverter()));
	}

	/**
	 *
	 */
	private void bindCommitMessage() {
		//the commit message
		final FeaturePath commitMessage = FeaturePath.fromList(
				ModelPackage.Literals.CHANGE_INFO__USER_SELECTED_REVISION, ModelPackage.Literals.REVISION_INFO__COMMIT,
				ModelPackage.Literals.COMMIT_INFO__MESSAGE);
		IObservableValue<String> msgTextDataValue = EMFProperties.value(commitMessage).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(msgTextData), msgTextDataValue, null, null);

	}

	public void dispose() {
		observableCollector.dispose();
		bindingContext.dispose();
	}
}
