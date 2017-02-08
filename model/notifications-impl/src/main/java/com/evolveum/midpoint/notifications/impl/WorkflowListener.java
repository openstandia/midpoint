/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.notifications.impl;

import com.evolveum.midpoint.notifications.api.NotificationManager;
import com.evolveum.midpoint.notifications.api.events.WorkItemEvent;
import com.evolveum.midpoint.notifications.api.events.WorkflowEvent;
import com.evolveum.midpoint.notifications.api.events.WorkflowProcessEvent;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.LightweightIdentifierGenerator;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.api.ProcessListener;
import com.evolveum.midpoint.wf.api.WorkItemListener;
import com.evolveum.midpoint.wf.api.WorkflowManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.WfContextType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemNotificationActionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Listener that accepts events generated by workflow module. These events are related to processes and work items.
 *
 * @author mederly
 */
@Component
public class WorkflowListener implements ProcessListener, WorkItemListener {

    private static final Trace LOGGER = TraceManager.getTrace(WorkflowListener.class);

    //private static final String DOT_CLASS = WorkflowListener.class.getName() + ".";

    @Autowired private NotificationManager notificationManager;
    @Autowired private NotificationFunctionsImpl functions;
    @Autowired private LightweightIdentifierGenerator identifierGenerator;

    // WorkflowManager is not required, because e.g. within model-test and model-intest we have no workflows.
    // However, during normal operation, it is expected to be available.

    @Autowired(required = false) private WorkflowManager workflowManager;

    @PostConstruct
    public void init() {
        if (workflowManager != null) {
            workflowManager.registerProcessListener(this);
            workflowManager.registerWorkItemListener(this);
        } else {
            LOGGER.warn("WorkflowManager not present, notifications for workflows will not be enabled.");
        }
    }

    @Override
    public void onProcessInstanceStart(Task wfTask, OperationResult result) {
        WorkflowProcessEvent event = new WorkflowProcessEvent(identifierGenerator, ChangeType.ADD, wfTask);
        initializeWorkflowEvent(event, wfTask);
        processEvent(event, result);
    }

	@Override
	public void onProcessInstanceEnd(Task wfTask, OperationResult result) {
		WorkflowProcessEvent event = new WorkflowProcessEvent(identifierGenerator, ChangeType.DELETE, wfTask);
		initializeWorkflowEvent(event, wfTask);
		processEvent(event, result);
	}

	private void initializeWorkflowEvent(WorkflowEvent event, Task wfTask) {
		WfContextType wfc = wfTask.getWorkflowContext();
		event.setRequester(new SimpleObjectRefImpl(functions, wfc.getRequesterRef()));
		if (wfc.getObjectRef() != null) {
			event.setRequestee(new SimpleObjectRefImpl(functions, wfc.getObjectRef()));
		}
		// TODO what if requestee is yet to be created?
	}



    @Override
    public void onWorkItemCreation(WorkItemType workItem, Task wfTask, OperationResult result) {
		SimpleObjectRefImpl assignee = workItem.getOriginalAssigneeRef() != null ?
				new SimpleObjectRefImpl(functions, workItem.getOriginalAssigneeRef()) : null;
		WorkItemEvent event = new WorkItemEvent(identifierGenerator, ChangeType.ADD, workItem, assignee, wfTask.getWorkflowContext());
		initializeWorkflowEvent(event, wfTask);
        processEvent(event, result);
    }

    @Override
    public void onWorkItemCompletion(WorkItemType workItem, Task wfTask, OperationResult result) {
		SimpleObjectRefImpl assignee = workItem.getOriginalAssigneeRef() != null ?
				new SimpleObjectRefImpl(functions, workItem.getOriginalAssigneeRef()) : null;
		WorkItemEvent event = new WorkItemEvent(identifierGenerator, ChangeType.DELETE, workItem, assignee, wfTask.getWorkflowContext());
		initializeWorkflowEvent(event, wfTask);
		processEvent(event, result);
    }

	@Override
	public void onWorkItemNotificationAction(WorkItemType workItem, WorkItemNotificationActionType notificationAction,
			Task wfTask, OperationResult result) {
//		WorkflowEventCreator workflowEventCreator = notificationManager.getWorkflowEventCreator(wfTask);
//		WorkItemEvent event = workflowEventCreator.createWorkItemEventForNotificationAction(workItem, notificationAction, wfTask, result);
//		processEvent(event);
		throw new UnsupportedOperationException();
	}

	private void processEvent(WorkflowEvent event, OperationResult result) {
        try {
            notificationManager.processEvent(event);
        } catch (RuntimeException e) {
            result.recordFatalError("An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
            LoggingUtils.logUnexpectedException(LOGGER, "An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
        }

        // todo work correctly with operationResult (in whole notification module)
        if (result.isUnknown()) {
            result.computeStatus();
        }
        result.recordSuccessIfUnknown();
    }

    private void processEvent(WorkflowEvent event) {
        try {
            notificationManager.processEvent(event);
        } catch (RuntimeException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "An unexpected exception occurred when preparing and sending notifications: " + e.getMessage(), e);
        }
    }
}
