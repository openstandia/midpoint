/*
 * Copyright (C) 2025 Evolveum and contributors
 *
 * Licensed under the EUPL-1.2 or later.
 *
 */

package com.evolveum.midpoint.model.impl.correlator.tasks;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.evolveum.midpoint.model.api.correlation.CompleteCorrelationResult;
import com.evolveum.midpoint.model.api.correlation.CorrelationService;
import com.evolveum.midpoint.model.impl.AbstractEmptyInternalModelTest;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.processor.ResourceObjectTypeDefinition;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.Resource;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.DummyTestResource;
import com.evolveum.midpoint.test.util.MidPointTestConstants;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@ContextConfiguration(locations = { "classpath:ctx-model-test-main.xml" })
public class CorrelationServiceTest extends AbstractEmptyInternalModelTest {

    private static final File TEST_DIR = new File(MidPointTestConstants.TEST_RESOURCES_DIR,
            "correlator/correlation/task");
    private static final File ACCOUNT = new File(TEST_DIR, "account.csv");
    private static final File USERS = new File(TEST_DIR, "users.xml");

    @Autowired
    private CorrelationService correlationService;
    private DummyTestResource resource;

    @DataProvider
    public static Object[] mappingPhases() {
        return new Object[] {
                null, InboundMappingEvaluationPhaseType.BEFORE_CORRELATION
        };
    }

    @DataProvider
    public static Object[] defaultEvalPhases() {
        return new Object[] {
                null,
                new ResourceMappingsEvaluationConfigurationType()
                        .inbound(new InboundMappingsEvaluationConfigurationType()
                        .defaultEvaluationPhases(new DefaultInboundMappingEvaluationPhasesType()
                                .phase(InboundMappingEvaluationPhaseType.BEFORE_CORRELATION)))
        };
    }

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        this.resource = DummyTestResource.fromFile(TEST_DIR, "dummy-resource.xml",
                        "4a7f6b3e-64cc-4cd9-b5ba-64ecc47d7d10", "correlation").withAccountsFromCsv(ACCOUNT);
    }

    @BeforeMethod
    public void initDummyResource() throws Exception {
        this.resource.initWithOverwrite(this, getTestTask(), getTestOperationResult());
    }

    /**
     * Tests whether the correlation works also with mappings which obey default mappings phase configuration (such
     * which allows usage by correlators)
     */
    @Test(dataProvider = "defaultEvalPhases")
    void defaultMappingsPhaseIsSpecified_correlateShadowUsingCorrelationMapping_focusShouldBeInCandidateOwners(
            ResourceMappingsEvaluationConfigurationType defaultEvalPhaseConfig) throws IOException, CommonException {
        final Task task = getTestTask();
        final OperationResult result = getTestOperationResult();

        given("Default mappings evaluation phase is %s".formatted(describePhase(defaultEvalPhaseConfig)));
        configureDefaultEvalPhase(defaultEvalPhaseConfig, task, result);
        and("Resource contains account.");
        final Collection<ShadowType> allAccounts = this.resource.getAccounts(this, this::listAccounts)
                .shadows(task, result);

        and("Users matching correlation rule exists.");
        importObjectsFromFileNotRaw(USERS, task, result);

        when("Correlation with particular definition is run on the account's shadow.");
        final ShadowType shadow = allAccounts.iterator().next();
        final ResourceType resourceType = this.resource.get().asObjectable();
        final CorrelationDefinitionType correlationDefinition = resourceType.getSchemaHandling().getObjectType().get(0)
                .getCorrelation();
        final ResourceObjectTypeDefinition objectTypeDef = Objects.requireNonNull(Resource.of(resourceType)
                .getCompleteSchemaRequired().getObjectTypeDefinition(shadow.getKind(), shadow.getIntent()));

        final CompleteCorrelationResult correlationResult = this.correlationService.correlate(
                shadow, resourceType, objectTypeDef, correlationDefinition, task, result);

        then("User should be correlated as shadow's candidate owner.");
        final List<UserType> candidates = correlationResult.getAllCandidates(UserType.class);
        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).getName().getOrig(), "smith1");
    }

    /**
     * Tests whether the correlation work also with mappings which have explicitly configured mapping phase (such which
     * allows usage by correlators)
     */
    @Test(dataProvider = "mappingPhases")
    void explicitMappingPhaseInCorrelationMapping_correlateShadowUsingCorrelationMapping_candidateOwnerShouldBeFound(
            InboundMappingEvaluationPhaseType mappingPhase) throws IOException, CommonException {
        final Task task = getTestTask();
        final OperationResult result = getTestOperationResult();

        given("The givenName inbound mapping has evaluation phase %s".formatted(
                mappingPhase == null ? "not set" : mappingPhase));
        configureGivenNameMappingPhase(mappingPhase, task, result);
        and("Resource contains account.");
        final Collection<ShadowType> allAccounts = this.resource.getAccounts(this, this::listAccounts)
                .shadows(task, result);

        and("Users matching correlation rule exists.");
        importObjectsFromFileNotRaw(USERS, task, result);

        when("Correlation with givenName definition is run on the account's shadow.");
        final ShadowType shadow = allAccounts.iterator().next();
        final ResourceType resourceType = this.resource.get().asObjectable();
        final CorrelationDefinitionType correlationDefinition = resourceType.getSchemaHandling().getObjectType().get(0)
                .getCorrelation();
        final ResourceObjectTypeDefinition objectTypeDef = Objects.requireNonNull(Resource.of(resourceType)
                .getCompleteSchemaRequired().getObjectTypeDefinition(shadow.getKind(), shadow.getIntent()));

        final CompleteCorrelationResult correlationResult = this.correlationService.correlate(
                shadow, resourceType, objectTypeDef, correlationDefinition, task, result);

        then("User should be correlated as shadow's candidate owner.");
        final List<UserType> candidates = correlationResult.getAllCandidates(UserType.class);
        assertEquals(candidates.size(), 1);
        assertEquals(candidates.get(0).getName().getOrig(), "smith1");
    }

    private void configureDefaultEvalPhase(ResourceMappingsEvaluationConfigurationType defaultEvalPhase, Task task,
            OperationResult result)
            throws ObjectAlreadyExistsException, ObjectNotFoundException,
            SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException,
            PolicyViolationException, SecurityViolationException {
        if (defaultEvalPhase == null) {
            return;
        }
        final Long id = this.resource.get().asObjectable().getSchemaHandling().getObjectType().get(0).getId();
        executeChanges(this.prismContext.deltaFor(ResourceType.class)
                .item(ItemPath.create(ResourceType.F_SCHEMA_HANDLING,
                        SchemaHandlingType.F_OBJECT_TYPE, id,
                        ResourceObjectTypeDefinitionType.F_MAPPINGS_EVALUATION))
                .add(defaultEvalPhase)
                .asObjectDelta(this.resource.oid), null, task, result);
        this.resource.reload(result);
    }

    private void configureGivenNameMappingPhase(InboundMappingEvaluationPhaseType mappingPhase, Task task,
            OperationResult result)
            throws ObjectAlreadyExistsException, ObjectNotFoundException,
            SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException,
            PolicyViolationException, SecurityViolationException {
        if (mappingPhase == null) {
            return;
        }
        final ResourceObjectTypeDefinitionType objectType =
                this.resource.get().asObjectable().getSchemaHandling().getObjectType().get(0);
        final Long objectTypeId = objectType.getId();
        final ResourceAttributeDefinitionType givenNameAttr = objectType.getAttribute().stream()
                .filter(a -> "givenName".equals(a.getRef().getItemPath().firstNameOrFail().getLocalPart()))
                .findFirst()
                .orElseThrow();
        final Long attrId = givenNameAttr.getId();
        final Long inboundId = givenNameAttr.getInbound().get(0).getId();
        executeChanges(this.prismContext.deltaFor(ResourceType.class)
                .item(ItemPath.create(
                        ResourceType.F_SCHEMA_HANDLING,
                        SchemaHandlingType.F_OBJECT_TYPE, objectTypeId,
                        ResourceObjectTypeDefinitionType.F_ATTRIBUTE, attrId,
                        ResourceItemDefinitionType.F_INBOUND, inboundId,
                        InboundMappingType.F_EVALUATION_PHASES))
                .add(new InboundMappingEvaluationPhasesType().include(mappingPhase))
                .asObjectDelta(this.resource.oid), null, task, result);
        this.resource.reload(result);
    }

    private static String describePhase(ResourceMappingsEvaluationConfigurationType defaultEvalPhaseConfig) {
        return defaultEvalPhaseConfig == null
                ? "not defined"
                : defaultEvalPhaseConfig.getInbound().getDefaultEvaluationPhases().getPhase().toString();
    }

}
