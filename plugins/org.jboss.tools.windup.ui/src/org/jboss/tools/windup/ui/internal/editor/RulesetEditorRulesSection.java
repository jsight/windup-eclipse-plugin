/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.windup.ui.internal.editor;

import static org.jboss.tools.windup.model.domain.WindupConstants.ACTIVE_ELEMENT;
import static org.jboss.tools.windup.model.domain.WindupConstants.CONFIG_CREATED;
import static org.jboss.tools.windup.model.domain.WindupConstants.CONFIG_DELETED;
import static org.jboss.tools.windup.ui.internal.Messages.rulesSectionTitle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ContributionManager;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.menus.IMenuService;
import org.eclipse.wst.sse.core.internal.format.IStructuredFormatProcessor;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.contentmodel.CMElementDeclaration;
import org.eclipse.wst.xml.core.internal.contentmodel.CMNode;
import org.eclipse.wst.xml.core.internal.contentmodel.modelquery.ModelQuery;
import org.eclipse.wst.xml.core.internal.contentmodel.modelquery.ModelQueryAction;
import org.eclipse.wst.xml.core.internal.contentmodel.util.CMDescriptionBuilder;
import org.eclipse.wst.xml.core.internal.contentmodel.util.DOMContentBuilder;
import org.eclipse.wst.xml.core.internal.contentmodel.util.DOMContentBuilderImpl;
import org.eclipse.wst.xml.core.internal.contentmodel.util.DOMNamespaceHelper;
import org.eclipse.wst.xml.core.internal.modelquery.ModelQueryUtil;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.format.FormatProcessorXML;
import org.eclipse.wst.xml.ui.internal.XMLUIMessages;
import org.eclipse.wst.xml.ui.internal.actions.BaseNodeActionManager.MyMenuManager;
import org.eclipse.wst.xml.ui.internal.actions.MenuBuilder;
import org.eclipse.wst.xml.ui.internal.actions.NodeAction;
import org.eclipse.wst.xml.ui.internal.editor.CMImageUtil;
import org.jboss.tools.windup.model.domain.ModelService;
import org.jboss.tools.windup.model.domain.WorkspaceResourceUtils;
import org.jboss.tools.windup.ui.WindupUIPlugin;
import org.jboss.tools.windup.ui.internal.Messages;
import org.jboss.tools.windup.ui.internal.editor.RulesetWidgetFactory.RulesetConstants;
import org.jboss.tools.windup.ui.internal.rules.xml.XMLRulesetModelUtil;
import org.jboss.tools.windup.ui.internal.services.RulesetDOMService;
import org.jboss.tools.windup.windup.ConfigurationElement;
import org.jboss.tools.windup.windup.CustomRuleProvider;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.Lists;

/**
 * The composite containing Windup's configurations.
 */
@SuppressWarnings("restriction")
public class RulesetEditorRulesSection {
	
	private static final String TOOLBAR_ID = "toolbar:org.jboss.tools.windup.toolbar.configurations.table"; //$NON-NLS-1$
	
	private static final String SELECTED_CONFIGURATION = "selectedConfiguration";
 
	@Inject private IEventBroker broker;
	@Inject private IMenuService menuService;
	@Inject private ModelService modelService;
	@Inject private FormToolkit toolkit;
	@Inject private IEclipsePreferences preferences;
	
	@Inject private RulesetDOMService domService;

	@Inject private RulesetWidgetRegistry widgetRegistry;
	
	protected MenuBuilder menuBuilder = new MenuBuilder();
	
	private ToolBarManager toolBarManager;
	private TreeViewer treeViewer;
	
	private Node selectedElement;
	
	private Button removeButton;
	private Button upButton;
	private Button downButton;
	
	public ISelectionProvider getSelectionProvider() {
		return treeViewer;
	}
	
	public TreeViewer getTableViewer() {
		return treeViewer;
	}
	
	public void selectAndReveal(Element element) {
		treeViewer.expandToLevel(element, TreeViewer.ALL_LEVELS);
		treeViewer.setSelection(new StructuredSelection(element), true);
	}
	
	public void setDocument(Document document) {
		if (document == treeViewer.getInput()) {
			treeViewer.refresh(document, true);
		}
		else {
			treeViewer.setInput(document);
		}
	}
	
	public void init(CustomRuleProvider ruleProvider) {
		IFile file = WorkspaceResourceUtils.getFile(ruleProvider.getLocationURI());
		List<Node> ruleNodes = XMLRulesetModelUtil.getRules(file);
		treeViewer.setInput(ruleNodes.toArray(new Node[ruleNodes.size()]));
	}
	
	@Inject
	@Optional
	private void updateDetails(@UIEventTopic(ACTIVE_ELEMENT) Element element) {
	}
	
	@PostConstruct
	private void create(Composite parent) {
		parent.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		
		Composite container = toolkit.createComposite(parent);
		GridLayoutFactory.fillDefaults().margins(0, 10).applyTo(container);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(container);
		
		Section section = toolkit.createSection(container, Section.TITLE_BAR);
		section.setText(rulesSectionTitle);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(section);
		
		Composite client = toolkit.createComposite(section);
		toolkit.paintBordersFor(client);
		GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(false).applyTo(client);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(client);
		section.setClient(client);
		
		createControls(client, section);
		
		focus();
	}
	
	private void createControls(Composite parent, Section section) {
		createToolbar(section);
		this.treeViewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		treeViewer.addSelectionChangedListener((e) -> {
			Object object = ((StructuredSelection)e.getSelection()).getFirstElement();
			this.selectedElement = (Element)object;
			broker.post(ACTIVE_ELEMENT, selectedElement);
			update();
			boolean enabled = selectedElement != null;
			removeButton.setEnabled(enabled);
			if (enabled) {
				enabled = RulesetConstants.RULE_NAME.equals(selectedElement.getNodeName()) ? true : false;
			}
			upButton.setEnabled(enabled);
			downButton.setEnabled(enabled);
		});
		
		GridDataFactory.fillDefaults().grab(true, true).applyTo(treeViewer.getTree());
		
		RulesSectionContentProvider provider = new RulesSectionContentProvider();
		
		treeViewer.setContentProvider(provider);
		treeViewer.setLabelProvider(provider);
		createViewerContextMenu();
		
		createButtons(parent);
	}
	
	private void createButtons(Composite parent) {
		Composite container = toolkit.createComposite(parent);
		toolkit.paintBordersFor(container);
		GridLayoutFactory.fillDefaults().applyTo(container);
		GridDataFactory.fillDefaults().grab(false, true).applyTo(container);
		
		Button addButton = createButton(container, Messages.RulesetEditor_AddRule);
		addButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Document document = (Document)treeViewer.getInput();
				Element rulesetElement = domService.findOrCreateRulesetElement(document);
				Element rulesElement = domService.findOrCreateRulesElement(rulesetElement);
				Element ruleElement = domService.createRuleElement(rulesElement);
				selectAndReveal(ruleElement);
			}
		});
		this.removeButton = createButton(container, Messages.RulesetEditor_remove);
		removeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				removeNodes(((IStructuredSelection)treeViewer.getSelection()).toList());
			}
		});
		
		createPlaceholder(container);
		createPlaceholder(container);
		createPlaceholder(container);
		
		this.upButton = createButton(container, Messages.RulesetEditor_Rules_up);
		this.downButton = createButton(container, Messages.RulesetEditor_Rules_down);
		
		upButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				StructuredSelection ss = (StructuredSelection)treeViewer.getSelection();
				if (!ss.isEmpty() && ss.size() == 1) {
					Node node = (Node)ss.getFirstElement();
					if (node instanceof Element && node.getParentNode() instanceof Element) {
						ISelection selection = treeViewer.getSelection();
						domService.insertBeforePreviousSibling(node);
						treeViewer.setSelection(selection);
					}
				}
			}
		});
		
		downButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				StructuredSelection ss = (StructuredSelection)treeViewer.getSelection();
				if (!ss.isEmpty() && ss.size() == 1) {
					Node node = (Node)ss.getFirstElement();
					if (node instanceof Element && node.getParentNode() instanceof Element) {
						ISelection selection = treeViewer.getSelection();
						domService.insertAfterNextSibling(node);
						treeViewer.setSelection(selection);
					}
				}
			}
		});
	}
	
	private void createPlaceholder(Composite parent) {
		Label label = toolkit.createLabel(parent, null);
		GridData gd = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		gd.horizontalSpan = 1;
		gd.widthHint = 0;
		gd.heightHint = 0;
		label.setLayoutData(gd);
	}
	
	private Button createButton(Composite parent, String label) {
		Button button = toolkit.createButton(parent, label, SWT.PUSH);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
		button.setLayoutData(gd);
		button.setFont(JFaceResources.getDialogFont());
		PixelConverter converter = new PixelConverter(button);
		int widthHint = converter.convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		gd.widthHint = Math.max(widthHint, button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		return button;
	}
	
	private void createToolbar(Section section) {
		this.toolBarManager = new ToolBarManager(SWT.FLAT);
		ToolBar toolBar = this.toolBarManager.createControl(section);
		section.setTextClient(toolBar);
		menuService.populateContributionManager((ContributionManager)toolBarManager, TOOLBAR_ID);
	}
	
	@Inject
	@Optional
	private void configCreated(@UIEventTopic(CONFIG_CREATED) ConfigurationElement configuration) {
		treeViewer.setSelection(new StructuredSelection(configuration), true);
	}
	
	@Inject
	@Optional
	private void configDeleted(@UIEventTopic(CONFIG_DELETED) ConfigurationElement configuration) {
	}
	
	@PreDestroy
	private void dispose() {
		if (selectedElement != null) {
			//preferences.put(SELECTED_CONFIGURATION, selectedNode.getNodeName());
		}
	}
	
	private void update() {
		broker.post(UIEvents.REQUEST_ENABLEMENT_UPDATE_TOPIC, UIEvents.ALL_ELEMENT_ID);
	}
	
	private void focus() {
		String previouslySelected = preferences.get(SELECTED_CONFIGURATION, null);
		if (previouslySelected != null) {
			ConfigurationElement configuration = modelService.findConfiguration(previouslySelected);
			if (configuration != null) {
				treeViewer.setSelection(new StructuredSelection(configuration), true);
			}
		}
		treeViewer.getTree().setFocus();
	}
	
	private void createViewerContextMenu() {
		MenuManager popupMenuManager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager mng) {
				fillContextMenu(mng);
			}
		};
		popupMenuManager.addMenuListener(listener);
		popupMenuManager.setRemoveAllWhenShown(true);
		Control control = treeViewer.getControl();
		Menu menu = popupMenuManager.createContextMenu(control);
		control.setMenu(menu);
	}
	
	private void fillContextMenu(IMenuManager manager) {
		ISelection selection = treeViewer.getSelection();
		IStructuredSelection ssel = (IStructuredSelection) selection;
		if (!ssel.isEmpty()) {
			if (ssel.toList().size() == 1) {
				Element element = (Element)ssel.getFirstElement();
				IStructuredModel model = ((IDOMDocument)treeViewer.getInput()).getModel();
				ModelQuery modelQuery = ModelQueryUtil.getModelQuery(model);
				IMenuManager addChildMenu = new MyMenuManager(Messages.rulesMenuNew);
				manager.add(addChildMenu);
				CMElementDeclaration ed = modelQuery.getCMElementDeclaration(element);
				if (ed != null) {
					List modelQueryActionList = new ArrayList();
					modelQuery.getInsertActions(element, ed, -1, ModelQuery.INCLUDE_CHILD_NODES,  ModelQuery.VALIDITY_STRICT, modelQueryActionList);
					addActionHelper(model, addChildMenu, modelQueryActionList);
				}
				/*if (object instanceof Element) {
					Element element = (Element) object;
					INodeWidget widget = widgetRegistry.getWidget(element);
					if (widget != null) {
						widget.fillContextMenu(manager);
						manager.add(new Separator());
					}
				}*/
			}
			Action deleteAction = new Action() {
				@Override
				public ImageDescriptor getImageDescriptor() {
					return PDEPluginImages.DESC_DELETE;
				}

				@Override
				public ImageDescriptor getDisabledImageDescriptor() {
					return PDEPluginImages.DESC_REMOVE_ATT_DISABLED;
				}

				@Override
				public void run() {
					removeNodes(ssel.toList());
				}
			};
			deleteAction.setText(Messages.RulesetEditor_RemoveElement);
			manager.add(deleteAction);
		}
		this.treeViewer.getControl().update();
	}
	
	protected void addActionHelper(IStructuredModel model, IMenuManager menu, List<ModelQueryAction> modelQueryActionList) {
		List<Action> actionList = Lists.newArrayList();
		for (ModelQueryAction action : modelQueryActionList) {
			if (action.getCMNode() != null) {
				int cmNodeType = action.getCMNode().getNodeType();
				if (action.getKind() == ModelQueryAction.INSERT) {
					switch (cmNodeType) {
						case CMNode.ELEMENT_DECLARATION : {
							actionList.add(createAddElementAction(model, action.getParent(), (CMElementDeclaration) action.getCMNode(), action.getStartIndex()));
							break;
						}
					}
				}
			}
		}
		menuBuilder.populateMenu(menu, actionList, false);
	}
	
	protected Action createAddElementAction(IStructuredModel model, Node parent, CMElementDeclaration ed, int index) {
		Action action = null;
		if (ed != null) {
			action = new AddNodeAction(model, ed, parent, index);
		}
		return action;
	}
	
	private void removeNodes(List<Element> elements) {
		if (elements.isEmpty()) {
			return;
		}
		
		IStructuredModel model = ((IDOMDocument)treeViewer.getInput()).getModel();
		
		try {
			model.aboutToChangeModel();

		
			Element firstElement = elements.get(0);
			Element parent = (Element)firstElement.getParentNode();
			
			Node nextSelection = domService.findNextSibling(elements.get(elements.size()-1), 1);
			if (nextSelection == null) {
				// no next node, use previous node
				nextSelection = domService.findPreviousSibling(firstElement);
			}
	
			if (nextSelection == null) {
				// next or previous null, use parent
				nextSelection = parent;
			}
			
			
			for (Element element : elements) {
				parent.removeChild(element);
			}
			
			if (nextSelection != null && !elements.contains(nextSelection)) {
				treeViewer.setSelection(new StructuredSelection(nextSelection));
			}
			
		}
		
		finally {
			model.changedModel();
		}
	}
	
	public class AddNodeAction extends NodeAction {
		protected CMNode cmnode;
		protected String description;
		protected int index;
		protected int nodeType;
		protected Node parent;
		protected String undoDescription;
		
		protected IStructuredModel model;
		protected RulesSectionContentProvider provider = new RulesSectionContentProvider();

		protected List<Node> result = Lists.newArrayList();
		
		public AddNodeAction(IStructuredModel model, CMNode cmnode, Node parent, int index) {
			this.model = model;
			this.cmnode = cmnode;
			this.parent = parent;
			this.index = index;

			String text = getLabel(parent, cmnode);
			setText(text);
			description = text;
			undoDescription = XMLUIMessages._UI_MENU_ADD + " " + text; //$NON-NLS-1$ 
			ImageDescriptor descriptor = CMImageUtil.getImageDescriptor(cmnode);
			if (descriptor == null) {
				//descriptor = imageDescriptorCache.getImageDescriptor(cmnode);
				descriptor = WindupUIPlugin.getImageDescriptor(WindupUIPlugin.IMG_XML_RULE);
			}
			setImageDescriptor(descriptor);
		}

		public AddNodeAction(IStructuredModel model, int nodeType, Node parent, int index) {
			this.model = model;
			this.nodeType = nodeType;
			this.index = index;
			this.parent = parent;
			switch (nodeType) {
				case Node.COMMENT_NODE : {
					description = XMLUIMessages._UI_MENU_COMMENT;
					undoDescription = XMLUIMessages._UI_MENU_ADD_COMMENT;
					break;
				}
				case Node.PROCESSING_INSTRUCTION_NODE : {
					description = XMLUIMessages._UI_MENU_PROCESSING_INSTRUCTION;
					undoDescription = XMLUIMessages._UI_MENU_ADD_PROCESSING_INSTRUCTION;
					break;
				}
				case Node.CDATA_SECTION_NODE : {
					description = XMLUIMessages._UI_MENU_CDATA_SECTION;
					undoDescription = XMLUIMessages._UI_MENU_ADD_CDATA_SECTION;
					break;
				}
				case Node.TEXT_NODE : {
					description = XMLUIMessages._UI_MENU_PCDATA;
					undoDescription = XMLUIMessages._UI_MENU_ADD_PCDATA;
					break;
				}
			}
			setText(description);
			//setImageDescriptor(imageDescriptorCache.getImageDescriptor(new Integer(nodeType)));
			setImageDescriptor(WindupUIPlugin.getImageDescriptor(WindupUIPlugin.IMG_XML_RULE));
		}
		
		public void beginNodeAction(NodeAction action) {
			model.beginRecording(action, action.getUndoDescription());
		}
		
		public void endNodeAction(NodeAction action) {
			model.endRecording(action);
		}
		
		public void insert(Node parent, CMNode cmnode, int index) {
			Document document = parent.getNodeType() == Node.DOCUMENT_NODE ? (Document) parent : parent.getOwnerDocument();
			DOMContentBuilder builder = createDOMContentBuilder(document);
			builder.setBuildPolicy(DOMContentBuilder.BUILD_ONLY_REQUIRED_CONTENT);
			builder.build(parent, cmnode);
			insertNodesAtIndex(parent, builder.getResult(), index);
		}
		
		public void insertNodesAtIndex(Node parent, List list, int index) {
			insertNodesAtIndex(parent, list, index, true);
		}
		
		protected boolean isWhitespaceTextNode(Node node) {
			return (node != null) && (node.getNodeType() == Node.TEXT_NODE) && (node.getNodeValue().trim().length() == 0);
		}
		
		public void insertNodesAtIndex(Node parent, List list, int index, boolean format) {
			NodeList nodeList = parent.getChildNodes();
			if (index == -1) {
				index = nodeList.getLength();
			}
			Node refChild = (index < nodeList.getLength()) ? nodeList.item(index) : null;

			// here we consider the case where the previous node is a 'white
			// space' Text node
			// we should really do the insert before this node
			//
			int prevIndex = index - 1;
			Node prevChild = (prevIndex < nodeList.getLength()) ? nodeList.item(prevIndex) : null;
			if (isWhitespaceTextNode(prevChild)) {
				refChild = prevChild;
			}

			for (Iterator i = list.iterator(); i.hasNext();) {
				Node newNode = (Node) i.next();

				if (newNode.getNodeType() == Node.ATTRIBUTE_NODE) {
					Element parentElement = (Element) parent;
					parentElement.setAttributeNode((Attr) newNode);
				}
				else {
					parent.insertBefore(newNode, refChild);
				}
			}

			boolean formatDeep = false;
			for (Iterator i = list.iterator(); i.hasNext();) {
				Node newNode = (Node) i.next();
				if (newNode.getNodeType() == Node.ELEMENT_NODE) {
					formatDeep = true;
				}

				if (format) {
					reformat(newNode, formatDeep);
				}
			}
			result.addAll(list);
		}
		
		public List<Node> getResult() {
			return result;
		}
		
		public void reformat(Node newElement, boolean deep) {
			try {
				// tell the model that we are about to make a big model change
				model.aboutToChangeModel();

				// format selected node
				IStructuredFormatProcessor formatProcessor = new FormatProcessorXML();
				formatProcessor.formatNode(newElement);
			}
			finally {
				// tell the model that we are done with the big model change
				model.changedModel();
			}
		}
		
		public DOMContentBuilder createDOMContentBuilder(Document document) {
			DOMContentBuilderImpl builder = new DOMContentBuilderImpl(document);
			return builder;
		}

		protected void addNodeForCMNode() {
			if (parent != null) {
				insert(parent, cmnode, index);
			}
		}

		protected void addNodeForNodeType() {
			Document document = parent.getNodeType() == Node.DOCUMENT_NODE ? (Document) parent : parent.getOwnerDocument();
			Node newChildNode = null;
			boolean format = true;
			switch (nodeType) {
				case Node.COMMENT_NODE : {
					newChildNode = document.createComment(XMLUIMessages._UI_COMMENT_VALUE);
					break;
				}
				case Node.PROCESSING_INSTRUCTION_NODE : {
					newChildNode = document.createProcessingInstruction(XMLUIMessages._UI_PI_TARGET_VALUE, XMLUIMessages._UI_PI_DATA_VALUE);
					break;
				}
				case Node.CDATA_SECTION_NODE : {
					newChildNode = document.createCDATASection(""); //$NON-NLS-1$
					break;
				}
				case Node.TEXT_NODE : {
					format = false;
					newChildNode = document.createTextNode(parent.getNodeName());
					break;
				}
			}

			if (newChildNode != null) {
				insertNodesAtIndex(parent, Lists.newArrayList(newChildNode), index, format);
			}
		}

		public String getUndoDescription() {
			return undoDescription;
		}

		public void run() {
			if (validateEdit(model, Display.getDefault().getActiveShell())) {
				beginNodeAction(this);
				if (cmnode != null) {
					addNodeForCMNode();
				}
				else {
					addNodeForNodeType();
				}
				endNodeAction(this);
			}
		}
		
		public String getLabel(Node parent, CMNode cmnode) {
			String result = "?" + cmnode + "?"; //$NON-NLS-1$ //$NON-NLS-2$
			if (cmnode != null) {
				// https://bugs.eclipse.org/bugs/show_bug.cgi?id=155800
				if (cmnode.getNodeType() == CMNode.ELEMENT_DECLARATION){
					result = DOMNamespaceHelper.computeName(cmnode, parent, null);
				}
				else{
					result = cmnode.getNodeName();
				}				
				if(result == null) {
					result = (String) cmnode.getProperty("description"); //$NON-NLS-1$
				}
				if (result == null || result.length() == 0) {
					if (cmnode.getNodeType() == CMNode.GROUP) {
						CMDescriptionBuilder descriptionBuilder = new CMDescriptionBuilder();
						result = descriptionBuilder.buildDescription(cmnode);
					}
				}
			}
			return result;
		}
	}
}
