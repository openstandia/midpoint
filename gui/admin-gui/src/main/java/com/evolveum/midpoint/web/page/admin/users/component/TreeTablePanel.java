/*
 * Copyright (c) 2010-2016 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.page.admin.users.component;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.util.exception.*;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.PolicyViolationException;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrgFilter;
import com.evolveum.midpoint.prism.query.OrgFilter.Scope;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.data.column.ColumnMenuAction;
import com.evolveum.midpoint.web.component.dialog.ConfirmationPanel;
import com.evolveum.midpoint.web.component.menu.cog.InlineMenuItem;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.orgs.OrgTreeAssignablePanel;
import com.evolveum.midpoint.web.page.admin.orgs.OrgTreePanel;
import com.evolveum.midpoint.web.page.admin.resources.PageResource;
import com.evolveum.midpoint.web.page.admin.roles.PageRole;
import com.evolveum.midpoint.web.page.admin.server.PageTaskEdit;
import com.evolveum.midpoint.web.page.admin.services.PageService;
import com.evolveum.midpoint.web.page.admin.users.PageOrgTree;
import com.evolveum.midpoint.web.page.admin.users.PageOrgUnit;
import com.evolveum.midpoint.web.page.admin.users.PageUser;
import com.evolveum.midpoint.web.util.OnePageParameterEncoder;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

/**
 * Used as a main component of the Org tree page.
 * 
 * todo create function computeHeight() in midpoint.js, update height properly
 * when in "mobile" mode... [lazyman] todo implement midpoint theme for tree
 * [lazyman]
 *
 * @author lazyman
 * @author katkav
 */
public class TreeTablePanel extends BasePanel<String> {

	private static final long serialVersionUID = 1L;
	private PageBase parentPage;

	@Override
	public PageBase getPageBase() {
		return parentPage;
	}

	protected static final String DOT_CLASS = TreeTablePanel.class.getName() + ".";
	protected static final String OPERATION_DELETE_OBJECTS = DOT_CLASS + "deleteObjects";
	protected static final String OPERATION_DELETE_OBJECT = DOT_CLASS + "deleteObject";
	protected static final String OPERATION_CHECK_PARENTS = DOT_CLASS + "checkParents";
	protected static final String OPERATION_MOVE_OBJECTS = DOT_CLASS + "moveObjects";
	protected static final String OPERATION_MOVE_OBJECT = DOT_CLASS + "moveObject";
	protected static final String OPERATION_UPDATE_OBJECTS = DOT_CLASS + "updateObjects";
	protected static final String OPERATION_UPDATE_OBJECT = DOT_CLASS + "updateObject";
	protected static final String OPERATION_RECOMPUTE = DOT_CLASS + "recompute";
	protected static final String OPERATION_SEARCH_MANAGERS = DOT_CLASS + "searchManagers";
	protected static final String OPERATION_COUNT_CHILDREN = DOT_CLASS + "countChildren";

	private static final String ID_TREE_PANEL = "treePanel";
	private static final String ID_MEMBER_PANEL = "memberPanel";

	private static final Trace LOGGER = TraceManager.getTrace(TreeTablePanel.class);

	public TreeTablePanel(String id, IModel<String> rootOid, PageBase parentPage) {
		super(id, rootOid);
		this.parentPage = parentPage;
		setParent(parentPage);
		initLayout();
	}

	protected void initLayout() {

		OrgTreePanel treePanel = new OrgTreePanel(ID_TREE_PANEL, getModel(), false) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void selectTreeItemPerformed(SelectableBean<OrgType> selected,
					AjaxRequestTarget target) {
				TreeTablePanel.this.selectTreeItemPerformed(selected, target);
			}

			protected List<InlineMenuItem> createTreeMenu() {
				return TreeTablePanel.this.createTreeMenu();
			}

			@Override
			protected List<InlineMenuItem> createTreeChildrenMenu() {
				return TreeTablePanel.this.createTreeChildrenMenu();
			}

		};
		treePanel.setOutputMarkupId(true);
		add(treePanel);
		add(createMemberPanel(treePanel.getSelected().getValue()));
		setOutputMarkupId(true);
	}

	private OrgMemberPanel createMemberPanel(OrgType org) {
		OrgMemberPanel memberPanel = new OrgMemberPanel(ID_MEMBER_PANEL, new Model<OrgType>(org), parentPage);
		memberPanel.setOutputMarkupId(true);
		return memberPanel;
	}

	private OrgTreePanel getTreePanel() {
		return (OrgTreePanel) get(ID_TREE_PANEL);
	}

	private List<InlineMenuItem> createTreeMenu() {
		List<InlineMenuItem> items = new ArrayList<>();
		return items;
	}

	private List<InlineMenuItem> createTreeChildrenMenu() {
		List<InlineMenuItem> items = new ArrayList<>();

		InlineMenuItem item = new InlineMenuItem(createStringResource("TreeTablePanel.move"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						moveRootPerformed(getRowModel().getObject(), target);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("TreeTablePanel.makeRoot"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						makeRootPerformed(getRowModel().getObject(), target);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("TreeTablePanel.delete"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						deleteRootPerformed(getRowModel().getObject(), target);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("TreeTablePanel.recompute"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						recomputeRootPerformed(getRowModel().getObject(), target);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("TreeTablePanel.edit"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						editRootPerformed(getRowModel().getObject(), target);
					}
				});
		items.add(item);

		item = new InlineMenuItem(createStringResource("TreeTablePanel.createChild"),
				new ColumnMenuAction<SelectableBean<OrgType>>() {
					private static final long serialVersionUID = 1L;

					@Override
					public void onClick(AjaxRequestTarget target) {
						try {
							initObjectForAdd(
									ObjectTypeUtil.createObjectRef(getRowModel().getObject().getValue()),
									OrgType.COMPLEX_TYPE, null, target);
						} catch (SchemaException e) {
							throw new SystemException(e.getMessage(), e);
						}
					}
				});
		items.add(item);

		return items;
	}

	protected static Map<Class, Class> objectDetailsMap;

	static {
		objectDetailsMap = new HashMap<>();
		objectDetailsMap.put(UserType.class, PageUser.class);
		objectDetailsMap.put(OrgType.class, PageOrgUnit.class);
		objectDetailsMap.put(RoleType.class, PageRole.class);
		objectDetailsMap.put(ServiceType.class, PageService.class);
		objectDetailsMap.put(ResourceType.class, PageResource.class);
		objectDetailsMap.put(TaskType.class, PageTaskEdit.class);
	}

	private void initObjectForAdd(ObjectReferenceType parentOrgRef, QName type, QName relation,
			AjaxRequestTarget target) throws SchemaException {
		TreeTablePanel.this.getPageBase().hideMainPopup(target);
		PrismContext prismContext = TreeTablePanel.this.getPageBase().getPrismContext();
		PrismObjectDefinition def = prismContext.getSchemaRegistry().findObjectDefinitionByType(type);
		PrismObject obj = def.instantiate();

		ObjectType objType = (ObjectType) obj.asObjectable();
		if (FocusType.class.isAssignableFrom(obj.getCompileTimeClass())) {
			AssignmentType assignment = new AssignmentType();
			assignment.setTargetRef(parentOrgRef);
			((FocusType) objType).getAssignment().add(assignment);
		} else {
			if (parentOrgRef == null) {
				ObjectType org = getTreePanel().getSelected().getValue();
				parentOrgRef = ObjectTypeUtil.createObjectRef(org);
				parentOrgRef.setRelation(relation);
			}

			objType.getParentOrgRef().add(parentOrgRef);
		}

		Class newObjectPageClass = objectDetailsMap.get(obj.getCompileTimeClass());

		Constructor constructor = null;
		try {
			constructor = newObjectPageClass.getConstructor(PrismObject.class);

		} catch (NoSuchMethodException | SecurityException e) {
			throw new SystemException("Unable to locate constructor (PrismObject) in " + newObjectPageClass
					+ ": " + e.getMessage(), e);
		}

		PageBase page;
		try {
			page = (PageBase) constructor.newInstance(obj);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new SystemException("Error instantiating " + newObjectPageClass + ": " + e.getMessage(), e);
		}

		setResponsePage(page);

	}

	private void selectTreeItemPerformed(SelectableBean<OrgType> selected, AjaxRequestTarget target) {
		getTreePanel().setSelected(selected);
		target.add(addOrReplace(createMemberPanel(selected.getValue())));
	}

	private void moveRootPerformed(SelectableBean<OrgType> root, AjaxRequestTarget target) {
		if (root == null) {
			root = getTreePanel().getRootFromProvider();
		}

		final SelectableBean<OrgType> orgToMove = root;

		OrgTreeAssignablePanel orgAssignablePanel = new OrgTreeAssignablePanel(
				parentPage.getMainPopupBodyId(), false, parentPage) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onItemSelect(SelectableBean<OrgType> selected, AjaxRequestTarget target) {
				moveConfirmPerformed(orgToMove, selected, target);
			}
		};

		parentPage.showMainPopup(orgAssignablePanel, target);

	}

	private void moveConfirmPerformed(SelectableBean<OrgType> orgToMove, SelectableBean<OrgType> selected,
			AjaxRequestTarget target) {
		getPageBase().hideMainPopup(target);

		Task task = getPageBase().createSimpleTask(OPERATION_MOVE_OBJECT);
		OperationResult result = new OperationResult(OPERATION_MOVE_OBJECT);

		OrgType toMove = orgToMove.getValue();
		ObjectDelta<OrgType> moveOrgDelta = ObjectDelta.createEmptyModifyDelta(OrgType.class, toMove.getOid(),
				getPageBase().getPrismContext());

		try {
			for (OrgType parentOrg : toMove.getParentOrg()) {
				AssignmentType oldRoot = new AssignmentType();
				oldRoot.setTargetRef(ObjectTypeUtil.createObjectRef(parentOrg));

				moveOrgDelta.addModification(ContainerDelta.createModificationDelete(OrgType.F_ASSIGNMENT,
						OrgType.class, getPageBase().getPrismContext(), oldRoot.asPrismContainerValue()));
				// moveOrgDelta.addModification(ReferenceDelta.createModificationDelete(OrgType.F_PARENT_ORG_REF,
				// toMove.asPrismObject().getDefinition(),
				// ObjectTypeUtil.createObjectRef(parentOrg).asReferenceValue()));
			}

			AssignmentType newRoot = new AssignmentType();
			newRoot.setTargetRef(ObjectTypeUtil.createObjectRef(selected.getValue()));
			moveOrgDelta.addModification(ContainerDelta.createModificationAdd(OrgType.F_ASSIGNMENT,
					OrgType.class, getPageBase().getPrismContext(), newRoot.asPrismContainerValue()));
			// moveOrgDelta.addModification(ReferenceDelta.createModificationAdd(OrgType.F_PARENT_ORG_REF,
			// toMove.asPrismObject().getDefinition(),
			// ObjectTypeUtil.createObjectRef(selected.getValue()).asReferenceValue()));

			getPageBase().getPrismContext().adopt(moveOrgDelta);
			getPageBase().getModelService()
					.executeChanges(WebComponentUtil.createDeltaCollection(moveOrgDelta), null, task, result);
			result.computeStatus();
		} catch (ObjectAlreadyExistsException | ObjectNotFoundException | SchemaException
				| ExpressionEvaluationException | CommunicationException | ConfigurationException
				| PolicyViolationException | SecurityViolationException e) {
			result.recordFatalError("Failed to move organization unit " + toMove, e);
			LoggingUtils.logUnexpectedException(LOGGER, "Failed to move organization unit" + toMove, e);
		}

		parentPage.showResult(result);
		target.add(parentPage.getFeedbackPanel());
		setResponsePage(PageOrgTree.class);

	}

	private void makeRootPerformed(SelectableBean<OrgType> newRoot, AjaxRequestTarget target) {
		Task task = getPageBase().createSimpleTask(OPERATION_MOVE_OBJECT);
		OperationResult result = new OperationResult(OPERATION_MOVE_OBJECT);

		OrgType toMove = newRoot.getValue();
		ObjectDelta<OrgType> moveOrgDelta = ObjectDelta.createEmptyModifyDelta(OrgType.class, toMove.getOid(),
				getPageBase().getPrismContext());

		try {
			for (ObjectReferenceType parentOrg : toMove.getParentOrgRef()) {
				AssignmentType oldRoot = new AssignmentType();
				oldRoot.setTargetRef(parentOrg);

				moveOrgDelta.addModification(ContainerDelta.createModificationDelete(OrgType.F_ASSIGNMENT,
						OrgType.class, getPageBase().getPrismContext(), oldRoot.asPrismContainerValue()));
			}

			getPageBase().getPrismContext().adopt(moveOrgDelta);
			getPageBase().getModelService()
					.executeChanges(WebComponentUtil.createDeltaCollection(moveOrgDelta), null, task, result);
			result.computeStatus();
		} catch (ObjectAlreadyExistsException | ObjectNotFoundException | SchemaException
				| ExpressionEvaluationException | CommunicationException | ConfigurationException
				| PolicyViolationException | SecurityViolationException e) {
			result.recordFatalError("Failed to move organization unit " + toMove, e);
			LoggingUtils.logUnexpectedException(LOGGER, "Failed to move organization unit" + toMove, e);
		}

		parentPage.showResult(result);
		target.add(parentPage.getFeedbackPanel());
		// target.add(getTreePanel());
		setResponsePage(PageOrgTree.class);
	}

	private void recomputeRootPerformed(SelectableBean<OrgType> root, AjaxRequestTarget target) {
		if (root == null) {
			root = getTreePanel().getRootFromProvider();
		}

		recomputePerformed(root, target);
	}

	private void recomputePerformed(SelectableBean<OrgType> orgToRecompute, AjaxRequestTarget target) {

		Task task = getPageBase().createSimpleTask(OPERATION_RECOMPUTE);
		OperationResult result = new OperationResult(OPERATION_RECOMPUTE);

		try {
			ObjectDelta emptyDelta = ObjectDelta.createEmptyModifyDelta(OrgType.class,
					orgToRecompute.getValue().getOid(), getPageBase().getPrismContext());
			ModelExecuteOptions options = new ModelExecuteOptions();
			options.setReconcile(true);
			getPageBase().getModelService().executeChanges(WebComponentUtil.createDeltaCollection(emptyDelta),
					options, task, result);

			result.recordSuccess();
		} catch (Exception e) {
			result.recordFatalError(getString("TreeTablePanel.message.recomputeError"), e);
			LoggingUtils.logUnexpectedException(LOGGER, getString("TreeTablePanel.message.recomputeError"), e);
		}

		getPageBase().showResult(result);
		target.add(getPageBase().getFeedbackPanel());
		getTreePanel().refreshTabbedPanel(target);
	}

	private void deleteRootPerformed(final SelectableBean<OrgType> orgToDelete, AjaxRequestTarget target) {

		ConfirmationPanel confirmationPanel = new ConfirmationPanel(getPageBase().getMainPopupBodyId(),
				new AbstractReadOnlyModel<String>() {

					private static final long serialVersionUID = 1L;

					@Override
					public String getObject() {
						if (hasChildren(orgToDelete)) {
							return createStringResource("TreeTablePanel.message.warn.deleteTreeObjectConfirm",
									WebComponentUtil.getEffectiveName(orgToDelete.getValue(),
											OrgType.F_DISPLAY_NAME)).getObject();
						}
						return createStringResource("TreeTablePanel.message.deleteTreeObjectConfirm",
								WebComponentUtil.getEffectiveName(orgToDelete.getValue(),
										OrgType.F_DISPLAY_NAME)).getObject();
					}
				}) {
			private static final long serialVersionUID = 1L;

			@Override
			public void yesPerformed(AjaxRequestTarget target) {
					deleteRootConfirmedPerformed(orgToDelete, target);
			}
		};

		confirmationPanel.setOutputMarkupId(true);
		getPageBase().showMainPopup(confirmationPanel, target);
	}

	private boolean hasChildren(SelectableBean<OrgType> orgToDelete) {
		OrgFilter childrenFilter = OrgFilter.createOrg(orgToDelete.getValue().getOid(), Scope.SUBTREE);
		Task task = getPageBase().createSimpleTask(OPERATION_COUNT_CHILDREN);
		OperationResult result = new OperationResult(OPERATION_COUNT_CHILDREN);
		try {
			int count = getPageBase().getModelService().countObjects(ObjectType.class,
					ObjectQuery.createObjectQuery(childrenFilter), null, task, result);
			return (count > 0);
		} catch (SchemaException | ObjectNotFoundException | SecurityViolationException
				| ConfigurationException | CommunicationException e) {
			LoggingUtils.logUnexpectedException(LOGGER, e.getMessage(), e);
			result.recordFatalError("Could not count members for org " + orgToDelete.getValue(), e);
			return false;
		}
	}


	private void deleteRootConfirmedPerformed(SelectableBean<OrgType> orgToDelete, AjaxRequestTarget target) {
		getPageBase().hideMainPopup(target);
		OperationResult result = new OperationResult(OPERATION_DELETE_OBJECT);

		PageBase page = getPageBase();

		if (orgToDelete == null) {
			orgToDelete = getTreePanel().getRootFromProvider();
		}
		String oidToDelete = orgToDelete.getValue().getOid();
		WebModelServiceUtils.deleteObject(OrgType.class, oidToDelete, result, page);

		result.computeStatusIfUnknown();
		page.showResult(result);

		// The following code determines if we deleted a root; if so, we refresh and reload whole page
		// TODO consider if we couldn't skip this parent checking and simply refresh whole page
		boolean isRoot = true;
		for (ObjectReferenceType parentRef : orgToDelete.getValue().getParentOrgRef()) {
			Task task = getPageBase().createSimpleTask(OPERATION_CHECK_PARENTS);
			try {
				getPageBase().getModelService().getObject(OrgType.class, parentRef.getOid(), null, task, task.getResult());
				isRoot = false;
				break;
			} catch (CommonException|RuntimeException e) {
				LoggingUtils.logExceptionAsWarning(LOGGER, "Exception while checking existence of org's {} parent: {}", e, oidToDelete, parentRef.getOid());
			}
		}

		if (isRoot) {
			// TODO is this ok? (target.add(getPage()) is not sufficient) [pmed]
			throw new RestartResponseException(getPage().getClass());
		} else {
			getTreePanel().refreshTabbedPanel(target);
		}
	}

	private void editRootPerformed(SelectableBean<OrgType> root, AjaxRequestTarget target) {
		if (root == null) {
			root = getTreePanel().getRootFromProvider();
		}
		PageParameters parameters = new PageParameters();
		parameters.add(OnePageParameterEncoder.PARAMETER, root.getValue().getOid());
		setResponsePage(PageOrgUnit.class, parameters);
	}

}
