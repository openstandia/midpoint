/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens;

import com.evolveum.midpoint.model.api.context.AssignmentPath;
import com.evolveum.midpoint.model.api.context.EvaluatedConstruction;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.ResourceShadowDiscriminator;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentHolderType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowKindType;

/**
 * @author mederly
 */
public class EvaluatedConstructionImpl implements EvaluatedConstruction {

    final private PrismObject<ResourceType> resource;
    final private ShadowKindType kind;
    final private String intent;
    final private String tag;
    final private boolean directlyAssigned;
    final private AssignmentPath assignmentPath;
    final private boolean weak;

    public <AH extends AssignmentHolderType> EvaluatedConstructionImpl(Construction<AH> construction, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException {
        this(construction, null, task, result);
    }

    public <AH extends AssignmentHolderType> EvaluatedConstructionImpl(Construction<AH> construction, String tag, Task task, OperationResult result) throws SchemaException, ObjectNotFoundException {
        resource = construction.getResource(task, result).asPrismObject();
        kind = construction.getKind();
        intent = construction.getIntent();
        this.tag = tag;
        assignmentPath = construction.getAssignmentPath();
        directlyAssigned = assignmentPath == null || assignmentPath.size() == 1;
        weak = construction.isWeak();
    }

    @Override
    public PrismObject<ResourceType> getResource() {
        return resource;
    }

    @Override
    public ShadowKindType getKind() {
        return kind;
    }

    @Override
    public String getIntent() {
        return intent;
    }

    /**
     * Returns the tag value for this evaluated construction.
     * Tags are used to distinguish multiple accounts of the same resource+kind+intent combination.
     * 
     * @return tag value, or null for single-account constructions
     */
    public String getTag() {
        return tag;
    }

    /**
     * Creates and returns the ResourceShadowDiscriminator for this evaluated construction.
     * Each EvaluatedConstructionImpl has a unique discriminator based on its tag value.
     * 
     * @return ResourceShadowDiscriminator with unique tag
     */
    public ResourceShadowDiscriminator getResourceShadowDiscriminator() {
        return new ResourceShadowDiscriminator(resource.getOid(), kind, intent, tag, false);
    }

    @Override
    public boolean isDirectlyAssigned() {
        return directlyAssigned;
    }

    @Override
    public AssignmentPath getAssignmentPath() {
        return assignmentPath;
    }

    @Override
    public boolean isWeak() {
        return weak;
    }

    @Override
    public String debugDump(int indent) {
        StringBuilder sb = new StringBuilder();
        DebugUtil.debugDumpLabelLn(sb, "EvaluatedConstruction", indent);
        DebugUtil.debugDumpWithLabelLn(sb, "resource", resource, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "kind", kind.value(), indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "intent", intent, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "tag", tag, indent + 1);
        DebugUtil.debugDumpWithLabelLn(sb, "directlyAssigned", directlyAssigned, indent + 1);
        DebugUtil.debugDumpWithLabel(sb, "weak", weak, indent + 1);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "EvaluatedConstruction(" +
                "resource=" + resource +
                ", kind=" + kind +
                ", intent='" + intent + '\'' +
                ", tag='" + tag + '\'' +
                ')';
    }
}
