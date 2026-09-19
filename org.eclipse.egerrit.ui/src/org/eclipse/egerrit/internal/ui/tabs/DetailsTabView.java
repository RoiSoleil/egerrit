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

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.core.databinding.observable.value.ComputedValue;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.property.Properties;
import org.eclipse.core.databinding.property.value.IValueProperty;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egerrit.internal.core.EGerritCorePlugin;
import org.eclipse.egerrit.internal.core.GerritClient;
import org.eclipse.egerrit.internal.core.command.AddReviewerCommand;
import org.eclipse.egerrit.internal.core.command.DeleteReviewerCommand;
import org.eclipse.egerrit.internal.core.command.DeleteTopicCommand;
import org.eclipse.egerrit.internal.core.command.SetTopicCommand;
import org.eclipse.egerrit.internal.core.command.SuggestReviewersCommand;
import org.eclipse.egerrit.internal.core.exception.EGerritException;
import org.eclipse.egerrit.internal.core.rest.AddReviewerInput;
import org.eclipse.egerrit.internal.core.rest.AddReviewerResult;
import org.eclipse.egerrit.internal.core.rest.TopicInput;
import org.eclipse.egerrit.internal.model.ChangeInfo;
import org.eclipse.egerrit.internal.model.LabelInfo;
import org.eclipse.egerrit.internal.model.ModelPackage;
import org.eclipse.egerrit.internal.model.RelatedChangeAndCommitInfo;
import org.eclipse.egerrit.internal.model.ReviewerInfo;
import org.eclipse.egerrit.internal.model.SuggestReviewerInfo;
import org.eclipse.egerrit.internal.model.provider.ModelItemProviderAdapterFactory;
import org.eclipse.egerrit.internal.ui.EGerritUIPlugin;
import org.eclipse.egerrit.internal.ui.editors.ChangeDetailEditor;
import org.eclipse.egerrit.internal.ui.editors.ModelLoader;
import org.eclipse.egerrit.internal.ui.editors.QueryHelpers;
import org.eclipse.egerrit.internal.ui.editors.model.ChangeDetailEditorInput;
import org.eclipse.egerrit.internal.ui.table.UIConflictsWithTable;
import org.eclipse.egerrit.internal.ui.table.UIRelatedChangesTable;
import org.eclipse.egerrit.internal.ui.table.UIReviewersTable;
import org.eclipse.egerrit.internal.ui.table.UISameTopicTable;
import org.eclipse.egerrit.internal.ui.table.provider.RelatedChangesTableLabelProvider;
import org.eclipse.egerrit.internal.ui.table.provider.ReviewersTableLabelProvider;
import org.eclipse.egerrit.internal.ui.utils.DataConverter;
import org.eclipse.egerrit.internal.ui.utils.EGerritConstants;
import org.eclipse.egerrit.internal.ui.utils.LinkDashboard;
import org.eclipse.egerrit.internal.ui.utils.Messages;
import org.eclipse.egerrit.internal.ui.utils.UIUtils;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.emf.databinding.EMFObservables;
import org.eclipse.emf.databinding.EMFProperties;
import org.eclipse.emf.databinding.FeaturePath;
import org.eclipse.emf.databinding.IEMFListProperty;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider;
import org.eclipse.jface.databinding.swt.ISWTObservable;
import org.eclipse.jface.databinding.swt.ISWTObservableValue;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.databinding.viewers.ViewerSupport;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;

/**
 * This class is used in the editor to handle the Gerrit message view
 *
 * @since 1.0
 */
public class DetailsTabView {

	private static final String TITLE = Messages.SummaryTabView_0;

	private final SimpleDateFormat formatTimeOut = new SimpleDateFormat("MMM d, yyyy  hh:mm a"); //$NON-NLS-1$

	private Link genProjectData;

	private Link genBranchData;

	private Text genTopicData;

	private Label genStrategyData;

	private Label genMessageData;

	private Label genUpdatedData;

	private TableViewer tableReviewersViewer;

	private Label genVoteData;

	private Text incBranchesData;

	private Text includedInTagsData;

	private TableViewer tableSameTopicViewer;

	private TableViewer tableRelatedChangesViewer;

	private TableViewer tableConflictsWithViewer;

	private ChangeInfo fChangeInfo;

	private GerritClient fGerritClient;

	private Label lblStrategy;

	private ModelLoader loader;

	private DataBindingContext bindingContext = new DataBindingContext();

	private ObservableCollector observableCollector;

	private Job activeCompletionJob;

	private Button fButtonPlus;

	private String[] dynamicReviewersColumn;

	/**
	 * Class that provides suggestion for completion for adding a reviewer.
	 */
	private class AddReviewerContentProposal {

		private class ReviewerContentProposalAdapter extends ContentProposalAdapter {
			public ReviewerContentProposalAdapter(Text control, IContentProposalProvider proposalProvider) {
				super(control, new TextContentAdapter(), proposalProvider, null, null);
				setPropagateKeys(true);
				setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
			}

			public void refreshProposalPopup() {
				closeProposalPopup();
				openProposalPopup();
			}
		}

		private SimpleContentProposalProvider proposalProvider;

		private ReviewerContentProposalAdapter contentAdapter;

		public AddReviewerContentProposal(Text control) {
			proposalProvider = new SimpleContentProposalProvider(new String[0]);
			proposalProvider.setFiltering(false);
			contentAdapter = new ReviewerContentProposalAdapter(control, proposalProvider);
		}

		public void setProposals(String[] proposals) {
			proposalProvider.setProposals(proposals);
			contentAdapter.refreshProposalPopup();
		}
	}
	// ------------------------------------------------------------------------
	// Constructor and life cycle
	// ------------------------------------------------------------------------

	/**
	 * The constructor.
	 *
	 * @param gerritClient
	 * @param changeInfo
	 */
	public DetailsTabView(GerritClient gerritClient, ChangeInfo changeInfo) {
		fChangeInfo = changeInfo;
		fGerritClient = gerritClient;
	}

	/**
	 * @param tabFolder
	 */
	public void create(TabFolder tabFolder) {
		createContols(tabFolder);
		loader = ModelLoader.initialize(fGerritClient, fChangeInfo);
		loader.loadDetailedInformation();
	}

	private void createContols(final TabFolder tabFolder) {
		final int heightFirstRow = UIUtils.computeFontSize(tabFolder).y * 8;
		final int heightLists = 150;

		TabItem tabSummary = new TabItem(tabFolder, SWT.NONE);
		tabSummary.setText(Messages.SummaryTabView_1);

		SashForm sashFormGlobal = new SashForm(tabFolder, SWT.VERTICAL);
		tabSummary.setControl(sashFormGlobal);

		SashForm sashFormHorizonTop = new SashForm(sashFormGlobal, SWT.HORIZONTAL);

		Composite general = summaryGeneral(sashFormHorizonTop);
		GridData generalGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
		generalGridData.minimumHeight = heightFirstRow * 4;
		general.setLayoutData(generalGridData);

		Composite reviewers = summaryReviewers(sashFormHorizonTop);
		GridData reviewersGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
		reviewersGridData.minimumHeight = heightFirstRow * 7;
		reviewersGridData.heightHint = heightFirstRow * 7;
		reviewers.setLayoutData(reviewersGridData);

		//Set the % of display data.40% table and 60% for the reviewers
		sashFormHorizonTop.setWeights(new int[] { 40, 60 });

		SashForm sashFormHorizonMiddle = new SashForm(sashFormGlobal, SWT.HORIZONTAL);
		GridData sashMiddleGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 6, 1);
		sashMiddleGridData.minimumHeight = heightFirstRow * 6;
		sashFormHorizonMiddle.setLayoutData(sashMiddleGridData);

		Composite includedIn = summaryIncluded(sashFormHorizonMiddle);
		includedIn.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));

		Composite sameTopic = summarySameTopic(sashFormHorizonMiddle);
		GridData sameTopicGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		sameTopicGridData.heightHint = heightLists;
		sameTopic.setLayoutData(sameTopicGridData);

		SashForm sashFormHorizonBottom = new SashForm(sashFormGlobal, SWT.HORIZONTAL);

		Composite relatedChanges = summaryRelatedChanges(sashFormHorizonBottom);
		relatedChanges.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

		Composite conflicts = summaryConflicts(sashFormHorizonBottom);
		GridData conflictsGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
		conflictsGridData.heightHint = heightLists;
		conflicts.setLayoutData(conflictsGridData);

		//Set the % of display top.40% middle and bottom 30%
		sashFormGlobal.setWeights(new int[] { 40, 30, 30 });

	}

	private Composite summaryGeneral(Composite parent) {
		Group composite = new Group(parent, SWT.NONE);
		composite.setText(Messages.SummaryTabView_2);
		composite.setLayout(new GridLayout(3, false));
		composite.setLayoutData(new GridData(SWT.FILL));

		//Project line
		Label lblProject = new Label(composite, SWT.NONE);
		lblProject.setText(Messages.SummaryTabView_3);
		lblProject.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		genProjectData = new Link(composite, SWT.NONE);
		genProjectData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		genProjectData.setToolTipText(Messages.SummaryTabView_26);
		genProjectData.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				LinkDashboard linkDash = new LinkDashboard(fGerritClient);
				linkDash.invokeRefreshDashboardCommand(EGerritConstants.PROJECT, e.text);
			}
		});

		//Branch line
		Label lblBranch = new Label(composite, SWT.NONE);
		lblBranch.setText(Messages.SummaryTabView_4);
		lblBranch.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		genBranchData = new Link(composite, SWT.NONE);
		genBranchData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		genBranchData.setToolTipText(Messages.SummaryTabView_25);
		genBranchData.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				LinkDashboard linkDash = new LinkDashboard(fGerritClient);
				Map<String, String> parameters = new LinkedHashMap<>();
				parameters.put(EGerritConstants.BRANCH, e.text);
				parameters.put(EGerritConstants.PROJECT, UIUtils.getLinkText(genProjectData.getText()));

				linkDash.invokeRefreshDashboardCommand(parameters);
			}
		});
		//Topic line
		Label lblTopic = new Label(composite, SWT.NONE);
		lblTopic.setText(Messages.SummaryTabView_5);
		lblTopic.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		genTopicData = new Text(composite, SWT.BORDER);
		genTopicData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		genTopicData.setEnabled(isEditingAllowed());

		Button btnSave = new Button(composite, SWT.NONE);
		btnSave.setText(Messages.SummaryTabView_6);
		btnSave.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		btnSave.addSelectionListener(buttonSaveListener(genTopicData));
		btnSave.setEnabled(isEditingAllowed());

		//Strategy line
		lblStrategy = new Label(composite, SWT.NONE);
		lblStrategy.setText(Messages.SummaryTabView_7);
		GridData lblStrategyGridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		lblStrategy.setLayoutData(lblStrategyGridData);

		genStrategyData = new Label(composite, SWT.NONE);
		GridData gdGenStrategyData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
		genStrategyData.setLayoutData(gdGenStrategyData);

		Color red = parent.getDisplay().getSystemColor(SWT.COLOR_RED);

		genMessageData = new Label(composite, SWT.NONE);
		GridData gdGenMessageData = new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1);
		genMessageData.setLayoutData(gdGenMessageData);
		genMessageData.setForeground(red);

		//Updated line
		Label lblUpdated = new Label(composite, SWT.NONE);
		lblUpdated.setText(Messages.SummaryTabView_8);
		lblUpdated.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		genUpdatedData = new Label(composite, SWT.NONE);
		genUpdatedData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		//Set the binding for this section
		sumGenDataBindings();
		observableCollector = new ObservableCollector(bindingContext);
		return composite;
	}

	private Composite summaryReviewers(Composite group) {
		Group grpReviewers = new Group(group, SWT.NONE);
		grpReviewers.setText(Messages.SummaryTabView_9);
		grpReviewers.setLayout(new GridLayout(3, false));

		//Vote summary line
		Label lblVoteSummary = new Label(grpReviewers, SWT.NONE);
		lblVoteSummary.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		lblVoteSummary.setText(Messages.SummaryTabView_10);

		genVoteData = new Label(grpReviewers, SWT.LEFT);
		genVoteData.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));

		//Table of reviewers
		createReviewersTable(grpReviewers);

		//Add
		Label lblUserName = new Label(grpReviewers, SWT.NONE);
		lblUserName.setText(Messages.SummaryTabView_11);
		lblUserName.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		final Text userName = new Text(grpReviewers, SWT.BORDER);
		final AddReviewerContentProposal reviewerProposal = new AddReviewerContentProposal(userName);

		userName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		userName.setToolTipText(Messages.SummaryTabView_12);
		userName.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent event) {
				if (shouldIgnoreKeyForCompletion(event.keyCode)) {
					return;
				}

				if (handleKeyReleased(userName.getText(), reviewerProposal) && event.keyCode == SWT.CR) {
					fButtonPlus.notifyListeners(SWT.Selection, new Event());
				}
			}

			@Override
			public void keyPressed(KeyEvent e) {
				// ignore
			}
		});

		fButtonPlus = new Button(grpReviewers, SWT.NONE);
		fButtonPlus.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
		fButtonPlus.setText("Add"); //$NON-NLS-1$
		fButtonPlus.addSelectionListener(buttonPlusListener(fButtonPlus, userName));

		//Set the binding for this section
		sumReviewerDataBindings();
		return grpReviewers;
	}

	/**
	 * Create the reviewers table with the dynamic columns
	 *
	 * @param grpReviewers
	 */
	private void createReviewersTable(Group grpReviewers) {
		//Create the dynamic column
		HashMap<String, Integer> hashMap = extractAllVotingColumn();
		SortedMap<String, Integer> dynamiColumnLabels = new TreeMap<>(hashMap);
		fillDynamicColumnArray(dynamiColumnLabels);

		//Table of reviewers
		UIReviewersTable uiReviewersTable = new UIReviewersTable();
		uiReviewersTable.createTableViewerSection(grpReviewers, dynamicReviewersColumn);

		tableReviewersViewer = uiReviewersTable.getViewer();
		tableReviewersViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		if (!fGerritClient.getRepository().getServerInfo().isAnonymous()) {
			tableReviewersViewer.getTable().addMouseListener(deleteReviewerListener());
		}
	}

	/**
	 * Fill an array with the column name for all Dynamic column
	 *
	 * @param dynamiColumnLabels
	 */
	private void fillDynamicColumnArray(SortedMap<String, Integer> dynamiColumnLabels) {
		dynamicReviewersColumn = dynamiColumnLabels.keySet().toArray(new String[dynamiColumnLabels.keySet().size()]);
	}

	private boolean handleKeyReleased(String queryText, AddReviewerContentProposal reviewerProposal) {
		// Don't trigger the query unless we have at least 3 characters.
		// The query will always return empty if there is not at least 3 characters
		if (queryText.length() < 3) {
			if (queryText.isEmpty()) {
				reviewerProposal.setProposals(new String[0]);
				// If we had started a completion job, we no longer need it
				if (activeCompletionJob != null) {
					activeCompletionJob.cancel();
					activeCompletionJob = null;
				}
			}
			return false;
		}

		runCompletionJob(queryText, reviewerProposal);
		return true;
	}

	private void runCompletionJob(String queryText, AddReviewerContentProposal reviewerProposal) {
		// We are starting a new completion job, so let's cancel any previous one
		if (activeCompletionJob != null) {
			activeCompletionJob.cancel();
		}

		// Start a job for the possibly long call so as to not block the UI thread
		activeCompletionJob = new Job(Messages.SummaryTabView_20) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				// Query the Gerrit server for matching reviewers
				SuggestReviewersCommand command = fGerritClient.suggestReviewers(fChangeInfo.getId());
				command.setMaxNumberOfResults(10);
				command.setQuery(queryText);

				SuggestReviewerInfo[] res = null;
				try {
					res = command.call();
				} catch (EGerritException e) {
					EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + e.getMessage());
				}

				if (monitor.isCanceled() || res == null) {
					return Status.OK_STATUS;
				}

				List<String> proposals = new ArrayList<>(res.length);
				for (SuggestReviewerInfo info : res) {
					String idString;
					if (info.getAccount() != null) {
						idString = info.getAccount().getName() + " <" + info.getAccount().getEmail() //$NON-NLS-1$
								+ ">"; //$NON-NLS-1$
					} else if (info.getGroup() != null) {
						idString = info.getGroup().getId();
					} else {
						// No valid reviewer info
						continue;
					}
					proposals.add(idString);
				}

				Display.getDefault().asyncExec(
						() -> reviewerProposal.setProposals(proposals.toArray(new String[proposals.size()])));

				return Status.OK_STATUS;
			}
		};
		activeCompletionJob.schedule();
	}

	/**
	 * Returns true if the specified key can be ignored
	 */
	private boolean shouldIgnoreKeyForCompletion(int keyCode) {
		switch (keyCode) {
		case SWT.ARROW_UP:
		case SWT.ARROW_DOWN:
		case SWT.ARROW_LEFT:
		case SWT.ARROW_RIGHT:
		case SWT.PAGE_UP:
		case SWT.PAGE_DOWN:
		case SWT.HOME:
		case SWT.END:
		case SWT.INSERT:
		case SWT.F1:
		case SWT.F2:
		case SWT.F3:
		case SWT.F4:
		case SWT.F5:
		case SWT.F6:
		case SWT.F7:
		case SWT.F8:
		case SWT.F9:
		case SWT.F10:
		case SWT.F11:
		case SWT.F12:
		case SWT.F13:
		case SWT.F14:
		case SWT.F15:
		case SWT.F16:
		case SWT.F17:
		case SWT.F18:
		case SWT.F19:
		case SWT.F20:
		case SWT.KEYPAD:
		case SWT.HELP:
		case SWT.CAPS_LOCK:
		case SWT.NUM_LOCK:
		case SWT.SCROLL_LOCK:
		case SWT.PAUSE:
		case SWT.BREAK:
		case SWT.PRINT_SCREEN:
			return true;
		default:
			return false;
		}
	}

	private Composite summaryIncluded(Composite group) {
		Group grpIncludedIn = new Group(group, SWT.NONE);
		grpIncludedIn.setText(Messages.SummaryTabView_13);
		grpIncludedIn.setLayout(new GridLayout(2, false));

		//Branches line
		Label lblBranches = new Label(grpIncludedIn, SWT.RIGHT);
		lblBranches.setText(Messages.SummaryTabView_14);
		lblBranches.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		incBranchesData = new Text(grpIncludedIn, SWT.BORDER);
		incBranchesData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		//Tags line
		Label lblTags = new Label(grpIncludedIn, SWT.RIGHT);
		lblTags.setText(Messages.SummaryTabView_15);
		lblTags.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));

		includedInTagsData = new Text(grpIncludedIn, SWT.BORDER);
		includedInTagsData.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		//Set the binding for this section
		sumIncludedDataBindings();
		return grpIncludedIn;
	}

	private Composite summarySameTopic(Composite group) {
		Group grpSameTopic = new Group(group, SWT.NONE);
		grpSameTopic.setText(Messages.SummaryTabView_16);
		grpSameTopic.setLayout(new FillLayout());

		UISameTopicTable tableUISameTopic = new UISameTopicTable(grpSameTopic);
		tableUISameTopic.createTableViewerSection();

		tableSameTopicViewer = tableUISameTopic.getViewer();
		tableSameTopicViewer.addDoubleClickListener(doubleClickSelectionChangeListener());

		//Set the binding for this section
		sumSameTopicDataBindings();
		return grpSameTopic;
	}

	private Composite summaryRelatedChanges(Composite group) {
		Group grpRelatedChanges = new Group(group, SWT.NONE);
		grpRelatedChanges.setText(Messages.SummaryTabView_17);
		grpRelatedChanges.setLayout(new FillLayout());

		UIRelatedChangesTable tableUIRelatedChanges = new UIRelatedChangesTable(grpRelatedChanges);
		tableUIRelatedChanges.createTableViewerSection();

		tableRelatedChangesViewer = tableUIRelatedChanges.getViewer();
		tableRelatedChangesViewer.addDoubleClickListener(doubleClickSelectionChangeListener());

		//Set the binding for this section
		sumRelatedChangesDataBindings();
		return grpRelatedChanges;
	}

	private Composite summaryConflicts(Composite group) {
		Group grpConflictsWith = new Group(group, SWT.NONE);
		grpConflictsWith.setText(Messages.SummaryTabView_18);
		grpConflictsWith.setLayout(new FillLayout());

		UIConflictsWithTable tableUIConflictsWith = new UIConflictsWithTable(grpConflictsWith);
		tableUIConflictsWith.createTableViewerSection();

		tableConflictsWithViewer = tableUIConflictsWith.getViewer();
		tableConflictsWithViewer.addDoubleClickListener(doubleClickSelectionChangeListener());

		//Set the binding for this section
		sumConflictWithDataBindings();
		return grpConflictsWith;
	}

	/**
	 * @return ISelectionChangedListener
	 */
	private IDoubleClickListener doubleClickSelectionChangeListener() {
		return event -> {

			StructuredSelection structuredSelection = (StructuredSelection) event.getSelection();
			Object element = structuredSelection.getFirstElement();
			String changeId = null;
			String subject = null;
			if (element instanceof ChangeInfo) {
				ChangeInfo changeInfo = (ChangeInfo) element;
				//Note: Need to use the id instead of the changeInfo.getChange_id() for cases where
				//we can have the same changeId on two different branches
				//See review 78088
				changeId = changeInfo.getId();
				if (changeId == null) {
					subject = changeInfo.getSubject();
				}
			} else if (element instanceof RelatedChangeAndCommitInfo) {
				RelatedChangeAndCommitInfo relChangeInfo = (RelatedChangeAndCommitInfo) element;
				//Note: Need to use the number instead of the changeId for cases where
				//we we can have the same changeId on two separate branches and this generates
				//an error since we received two reviews
				changeId = relChangeInfo.get_change_number();
				if (changeId == null) {
					subject = relChangeInfo.getCommit().getSubject();
				}
			}
			//Open a new ChangeDetailEditor
			createDetailEditor(changeId, subject);
		};
	}

	/**
	 * @param changeId
	 *            String
	 * @param subject
	 *            String
	 */
	private void createDetailEditor(String changeId, String subject) {
		ChangeInfo changeInfo = null;
		if (changeId != null) {
			changeInfo = QueryHelpers.lookupPartialChangeInfoFromChangeId(fGerritClient, changeId,
					new NullProgressMonitor());
		} else if (subject != null) {
			ChangeInfo[] arrayChangeInfo = null;
			try {
				arrayChangeInfo = QueryHelpers.lookupPartialChangeInfoFromSubject(fGerritClient, subject,
						new NullProgressMonitor());
				if (arrayChangeInfo.length >= 1) {
					changeInfo = arrayChangeInfo[0]; //Keep the first one, we should only have one anyway
				}
			} catch (MalformedURLException e) {
				return;
			}
		}

		IWorkbench workbench = EGerritUIPlugin.getDefault().getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		IWorkbenchPage page = null;
		if (window != null) {
			page = workbench.getActiveWorkbenchWindow().getActivePage();
		}

		if (page != null && changeInfo != null) {
			try {
				page.openEditor(new ChangeDetailEditorInput(fGerritClient, changeInfo), ChangeDetailEditor.EDITOR_ID);
			} catch (PartInitException e) {
				EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + e.getMessage());
			}
		}
	}

	/**
	 * This method is the listener to add a reviewer or a group of reviewers
	 *
	 * @param buttonPlus
	 * @return SelectionAdapter
	 */
	private SelectionAdapter buttonPlusListener(final Button buttonPlus, final Text textWidget) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);

				String reviewer = textWidget.getText().trim();

				if (!reviewer.isEmpty()) {
					AddReviewerCommand addReviewerCmd = fGerritClient.addReviewer(fChangeInfo.getId());
					AddReviewerInput addReviewerInput = new AddReviewerInput();
					addReviewerInput.setReviewer(reviewer);

					addReviewerCmd.setCommandInput(addReviewerInput);

					AddReviewerResult reviewerCmdResult = null;
					reviewerCmdResult = addReviewerRequest(addReviewerCmd, addReviewerInput);
					if (reviewerCmdResult != null && reviewerCmdResult.getConfirm()) {
						//There is an error, just need to know if we should re-send or not
						if (!MessageDialog.openConfirm(buttonPlus.getParent().getShell(), Messages.SummaryTabView_19,
								reviewerCmdResult.getError())) {
							return;
						}
						//Call again but with the flag confirm
						addReviewerInput.setConfirmed(true);
						addReviewerRequest(addReviewerCmd, addReviewerInput);
					}

					loader.reload(true);
					textWidget.setText(""); //$NON-NLS-1$
				}
			}

			/**
			 * @param addReviewerCmd
			 * @param input
			 * @return
			 */
			private AddReviewerResult addReviewerRequest(AddReviewerCommand addReviewerCmd, AddReviewerInput input) {
				AddReviewerResult reviewerCmdResult = null;
				try {
					reviewerCmdResult = addReviewerCmd.call();
				} catch (EGerritException e3) {
					handleAddReviewerException(input, e3);
				}
				return reviewerCmdResult;
			}

			/**
			 * @param input
			 * @param e3
			 */
			private void handleAddReviewerException(AddReviewerInput input, EGerritException e3) {
				if (e3.getCode() == EGerritException.SHOWABLE_MESSAGE) {
					String message = input.getReviewer() + Messages.SummaryTabView_21;
					UIUtils.displayInformation(TITLE, message);
				} else {
					EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + e3.getMessage());
				}
			}
		};
	}

	/**
	 * This method is the listener to delete a reviewer
	 *
	 * @return MouseAdapter
	 */
	private MouseAdapter deleteReviewerListener() {
		return new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				ViewerCell viewerCell = tableReviewersViewer.getCell(new Point(e.x, e.y));
				if (viewerCell != null && viewerCell.getColumnIndex() == 0) {
					//Selected the first column, so we can send the delete option
					//Otherwise, do not delete
					selectReviewerToDelete();
				}
			}

			/**
			 *
			 */
			private void selectReviewerToDelete() {
				ISelection selection = tableReviewersViewer.getSelection();
				if (selection instanceof IStructuredSelection) {

					IStructuredSelection structuredSelection = (IStructuredSelection) selection;

					Object element = structuredSelection.getFirstElement();

					if (element instanceof ReviewerInfo) {

						deleteReviewerCommand((ReviewerInfo) element);
					}
				}
			}

			/**
			 * @param element
			 */
			private void deleteReviewerCommand(ReviewerInfo reviewerInfo) {

				//Verify is the current use can be deleted or not
				if (reviewerInfo.isDeleteable()) {
					//Add a safety dialog to confirm the deletion

					if (!MessageDialog.openConfirm(tableReviewersViewer.getTable().getShell(),
							Messages.SummaryTabView_22,
							Messages.SummaryTabView_23 + reviewerInfo.getName() + Messages.SummaryTabView_24)) {
						return;
					}

					execDeleteReviewerCommand(reviewerInfo);
				}
			}

			/**
			 * @param reviewerInfo
			 */
			private void execDeleteReviewerCommand(ReviewerInfo reviewerInfo) {
				DeleteReviewerCommand deleteReviewerCmd = fGerritClient.deleteReviewer(fChangeInfo.getId(),
						String.valueOf(reviewerInfo.get_account_id()));

				try {
					deleteReviewerCmd.call();
					fChangeInfo.getComputedReviewers().remove(reviewerInfo);
					loader.reload(true);
				} catch (EGerritException e3) {
					EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + e3.getMessage());
				}
			}
		};
	}

	/**
	 * This method is the listener to save a topic
	 *
	 * @param genTopicData2
	 * @return SelectionAdapter
	 */
	private SelectionAdapter buttonSaveListener(final Text topicData) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				super.widgetSelected(e);

				String topic = topicData.getText().trim();
				setTopic(topic);
				loader.loadBasicInformation(false);
			}
		};
	}

	private void setTopic(String topic) {
		if (topic == null) {
			return;
		}

		if (!"".equals(topic.trim())) { //$NON-NLS-1$
			SetTopicCommand command = fGerritClient.setTopic(fChangeInfo.getChange_id());
			TopicInput topicInput = new TopicInput();
			topicInput.setTopic(topic);
			command.setCommandInput(topicInput);
			try {
				command.call();
			} catch (EGerritException ex) {
				EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + ex.getMessage());
			}
		} else {
			DeleteTopicCommand command = fGerritClient.deleteTopic(fChangeInfo.getChange_id());
			try {
				command.call();
			} catch (EGerritException ex) {
				EGerritCorePlugin.logError(fGerritClient.getRepository().formatGerritVersion() + ex.getMessage());
			}
		}

	}

	/************************************************************* */
	/*                                                             */
	/* Section adjust the DATA binding                             */
	/*                                                             */
	/************************************************************* */
	private DataBindingContext sumGenDataBindings() {
		//Show project info
		IObservableValue<String> projectbytesFChangeInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__PROJECT).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(genProjectData), projectbytesFChangeInfoObserveValue,
				null, new UpdateValueStrategy().setConverter(DataConverter.linkText()));
		//Show branch
		IObservableValue<String> branchFChangeInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__BRANCH).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(genBranchData), branchFChangeInfoObserveValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.linkText()));
		//Show topic
		IObservableValue<String> topicFChangeInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__TOPIC).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(genTopicData), topicFChangeInfoObserveValue, null,
				null);

		//Show updated status
		IObservableValue<String> updatedFChangeInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__UPDATED).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(genUpdatedData), updatedFChangeInfoObserveValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.gerritTimeConverter(formatTimeOut)));

		//Show mergeableinfo
		IObservableValue<String> mergeSubmitValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__MERGEABLE_INFO).observe(fChangeInfo);

		bindingContext.bindValue(WidgetProperties.text().observe(genStrategyData), mergeSubmitValue, null,
				new UpdateValueStrategy().setConverter(DataConverter.submitTypeConverter()));

		//Show mergeable status
		IObservableValue<String> mergeableMergeableInfoObserveValue = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__MERGEABLE).observe(fChangeInfo);
		bindingContext.bindValue(WidgetProperties.text().observe(genMessageData), mergeableMergeableInfoObserveValue,
				null, new UpdateValueStrategy().setConverter(DataConverter.cannotMergeConverter()));

		//Hide the "Strategy: ...." line if the review has been merged.
		UpdateValueStrategy hideWidgetsStrategy = new UpdateValueStrategy() {
			@Override
			protected IStatus doSet(IObservableValue observableValue, Object value) {
				Boolean visible = Boolean.TRUE;
				Widget w = ((ISWTObservable) observableValue).getWidget();
				if (!w.isDisposed() && w instanceof Control) {
					if ("MERGED".equals(value) //$NON-NLS-1$
							|| "ABANDONED".equals(value)) { //$NON-NLS-1$
						visible = Boolean.FALSE;

						((GridData) ((Control) w).getLayoutData()).exclude = !((Control) w).isVisible();
					} else {
						((GridData) ((Control) w).getLayoutData()).exclude = false;
					}
					((Control) w).getParent().layout(true);
				}
				return super.doSet(observableValue, visible);
			}
		};
		IObservableValue<String> observeChangeInfoStatus = EMFProperties
				.value(ModelPackage.Literals.CHANGE_INFO__STATUS).observe(fChangeInfo);
		IObservableValue observeGenDataVisibility = WidgetProperties.visible().observe(genMessageData);
		bindingContext.bindValue(observeGenDataVisibility, observeChangeInfoStatus, null, hideWidgetsStrategy);
		IObservableValue observeGenStrategyDataVisibility = WidgetProperties.visible().observe(genStrategyData);
		bindingContext.bindValue(observeGenStrategyDataVisibility, observeChangeInfoStatus, null, hideWidgetsStrategy);
		IObservableValue observeStrategyLabelVisibility = WidgetProperties.visible().observe(lblStrategy);
		bindingContext.bindValue(observeStrategyLabelVisibility, observeChangeInfoStatus, null, hideWidgetsStrategy);

		return bindingContext;
	}

	private void sumReviewerDataBindings() {
		ObservableListContentProvider contentProvider = new ObservableListContentProvider();
		tableReviewersViewer.setContentProvider(contentProvider);
		final IObservableMap[] watchedProperties = Properties.observeEach(contentProvider.getKnownElements(),
				new IValueProperty[] { Properties.selfValue("x"), //$NON-NLS-1$
						EMFProperties.value(ModelPackage.Literals.REVIEWER_INFO__NAME),
						EMFProperties.value(ModelPackage.Literals.REVIEWER_INFO__EMAIL),
						EMFProperties.value(ModelPackage.Literals.REVIEWER_INFO__DELETEABLE) });
		tableReviewersViewer
				.setLabelProvider(new ReviewersTableLabelProvider(watchedProperties, dynamicReviewersColumn));

		tableReviewersViewer.setInput(
				EMFProperties.list(ModelPackage.Literals.CHANGE_INFO__COMPUTED_REVIEWERS).observe(fChangeInfo));

		//Adjust the reviewers vote
		final IObservableList<ReviewerInfo> observedList = EMFObservables.observeList(fChangeInfo,
				ModelPackage.Literals.CHANGE_INFO__COMPUTED_REVIEWERS);

		ComputedValue<String> cv = new ComputedValue<String>() {
			@Override
			protected String calculate() {
				HashMap<String, Integer> hashMap = extractAllVotingColumn();

				//Now sort and Display the vote:
				return formatReviewersVote(hashMap);
			}

			/**
			 * @param vote
			 * @param hashMap
			 * @return String
			 */
			private String formatReviewersVote(HashMap<String, Integer> hashMap) {
				SortedMap<String, Integer> sortedMap = new TreeMap<>(hashMap);
				StringBuilder sb = new StringBuilder();
				if (!sortedMap.isEmpty()) {
					Iterator<Entry<String, Integer>> iterSorted = sortedMap.entrySet().iterator();
					while (iterSorted.hasNext()) {
						String vote = ""; //$NON-NLS-1$
						Entry<String, Integer> entry = iterSorted.next();
						if (entry.getValue() > 0) {
							vote = "+"; //$NON-NLS-1$
						}
						vote = vote.concat(Integer.toString(entry.getValue()));
						sb.append(entry.getKey());
						sb.append(": "); //$NON-NLS-1$
						sb.append(vote);
						sb.append("  "); //$NON-NLS-1$
					}
				}
				return sb.toString().trim();
			}
		};

		ISWTObservableValue o = WidgetProperties.text().observe(genVoteData);
		bindingContext.bindValue(o, cv, null, null);
	}

	/**
	 * @return HashMap
	 */
	private HashMap<String, Integer> extractAllVotingColumn() {
		EMap<String, LabelInfo> allVotes = fChangeInfo.getLabels();
		HashMap<String, Integer> mostRevelantVotesPerLabel = new HashMap<>();
		if (allVotes == null) {
			return mostRevelantVotesPerLabel;
		}

		for (String aLabel : allVotes.keySet()) {
			mostRevelantVotesPerLabel.put(aLabel, fChangeInfo.getMostRelevantVote(aLabel).getValue());
		}
		return mostRevelantVotesPerLabel;
	}

	private void sumIncludedDataBindings() {
		hookBranches();
		hookTags();
	}

	private void hookTags() {
		//Hook the tags
		FeaturePath fp = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__INCLUDED_IN,
				ModelPackage.Literals.INCLUDED_IN_INFO__TAGS);
		IEMFListProperty includedTags = EMFProperties.list(fp);
		final IObservableList<ReviewerInfo> observedTags = includedTags.observe(fChangeInfo);

		ComputedValue<String> cv = new ComputedValue<String>() {
			@Override
			protected String calculate() {
				Iterator<ReviewerInfo> it = observedTags.iterator();
				String result = ""; //$NON-NLS-1$
				StringBuilder sb = new StringBuilder();
				while (it.hasNext()) {
					Object object = it.next();
					sb.append(object.toString());
					sb.append(", "); //$NON-NLS-1$
				}
				result = sb.toString();
				if (!result.isEmpty()) {
					int last = result.lastIndexOf(',');
					result = result.substring(0, last);
				}
				return result;
			}

		};
		ISWTObservableValue o = WidgetProperties.text().observe(includedInTagsData);
		bindingContext.bindValue(o, cv, null, null);

	}

	private void hookBranches() {
		//Hook the branches
		FeaturePath fp = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__INCLUDED_IN,
				ModelPackage.Literals.INCLUDED_IN_INFO__BRANCHES);
		IEMFListProperty includedInBranches = EMFProperties.list(fp);
		final IObservableList<ReviewerInfo> observedBranches = includedInBranches.observe(fChangeInfo);

		ComputedValue<String> cv = new ComputedValue<String>() {
			@Override
			protected String calculate() {
				Iterator<ReviewerInfo> it = observedBranches.iterator();
				String result = ""; //$NON-NLS-1$
				StringBuilder sb = new StringBuilder();
				while (it.hasNext()) {
					Object object = it.next();
					sb.append(object.toString());
					sb.append(", "); //$NON-NLS-1$
				}
				result = sb.toString();
				if (!result.isEmpty()) {
					int last = result.lastIndexOf(',');
					result = result.substring(0, last);
				}
				return result;
			}

		};
		ISWTObservableValue o = WidgetProperties.text().observe(incBranchesData);
		bindingContext.bindValue(o, cv, null, null);
	}

	private void sumSameTopicDataBindings() {
		ComposedAdapterFactory composedAdapterFactory = new ComposedAdapterFactory(
				ComposedAdapterFactory.Descriptor.Registry.INSTANCE);
		composedAdapterFactory.addAdapterFactory(new ModelItemProviderAdapterFactory());

		tableSameTopicViewer.setContentProvider(new AdapterFactoryContentProvider(composedAdapterFactory));
		tableSameTopicViewer.setLabelProvider(new AdapterFactoryLabelProvider(composedAdapterFactory));

		IObservableList<ReviewerInfo> observedList = EMFObservables.observeList(fChangeInfo,
				ModelPackage.Literals.CHANGE_INFO__SAME_TOPIC);
		ViewerSupport.bind(tableSameTopicViewer, observedList,
				new IValueProperty[] { EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__NUMBER),
						EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__SUBJECT) });
	}

	private void sumRelatedChangesDataBindings() {
		ObservableListContentProvider contentProvider = new ObservableListContentProvider();
		tableRelatedChangesViewer.setContentProvider(contentProvider);

		final IObservableMap[] watchedProperties = Properties.observeEach(contentProvider.getKnownElements(),
				new IValueProperty[] {
						EMFProperties.value(ModelPackage.Literals.RELATED_CHANGE_AND_COMMIT_INFO__COMMIT),
						EMFProperties.value(ModelPackage.Literals.RELATED_CHANGE_AND_COMMIT_INFO__CHANGE_NUMBER),
						EMFProperties.value(ModelPackage.Literals.RELATED_CHANGE_AND_COMMIT_INFO__CHANGE_ID),
						EMFProperties.value(ModelPackage.Literals.RELATED_CHANGE_AND_COMMIT_INFO__STATUS) });
		tableRelatedChangesViewer.setLabelProvider(new RelatedChangesTableLabelProvider(watchedProperties));
		final FeaturePath fpath = FeaturePath.fromList(ModelPackage.Literals.CHANGE_INFO__RELATED_CHANGES,
				ModelPackage.Literals.RELATED_CHANGES_INFO__CHANGES);

		tableRelatedChangesViewer.setInput(EMFProperties.list(fpath).observe(fChangeInfo));
	}

	private void sumConflictWithDataBindings() {
		IObservableList<ReviewerInfo> observedList = EMFObservables.observeList(fChangeInfo,
				ModelPackage.Literals.CHANGE_INFO__CONFLICTS_WITH);
		ViewerSupport.bind(tableConflictsWithViewer, observedList,
				new IValueProperty[] { EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__NUMBER),
						EMFProperties.value(ModelPackage.Literals.CHANGE_INFO__SUBJECT) });
	}

	private boolean isEditingAllowed() {
		return !fGerritClient.getRepository().getServerInfo().isAnonymous();
	}

	public void dispose() {
		observableCollector.dispose();
		bindingContext.dispose();
		loader.dispose();
	}

}
