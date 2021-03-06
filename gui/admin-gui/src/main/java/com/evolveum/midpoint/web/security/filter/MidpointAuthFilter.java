/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.security.filter;

import com.evolveum.midpoint.model.api.authentication.*;
import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SecurityPolicyUtil;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.security.MidpointAuthenticationManager;
import com.evolveum.midpoint.web.security.factory.channel.AuthChannelRegistryImpl;
import com.evolveum.midpoint.web.security.module.ModuleWebSecurityConfig;
import com.evolveum.midpoint.web.security.factory.module.AuthModuleRegistryImpl;
import com.evolveum.midpoint.web.security.util.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/**
 * @author skublik
 */

public class MidpointAuthFilter extends GenericFilterBean {

    private static final Trace LOGGER = TraceManager.getTrace(MidpointAuthFilter.class);
    private final Map<Class<?>, Object> sharedObjects;

    @Autowired private ObjectPostProcessor<Object> objectObjectPostProcessor;
    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private AuthModuleRegistryImpl authModuleRegistry;
    @Autowired private AuthChannelRegistryImpl authChannelRegistry;
    @Autowired private MidpointAuthenticationManager authenticationManager;
    @Autowired private PrismContext prismContext;
    @Autowired private TaskManager taskManager;

    private AuthenticationsPolicyType authenticationPolicy;
    private PreLogoutFilter preLogoutFilter = new PreLogoutFilter();

    public MidpointAuthFilter(Map<Class<? extends Object>, Object> sharedObjects) {
        this.sharedObjects = sharedObjects;
    }

    public PreLogoutFilter getPreLogoutFilter() {
        return preLogoutFilter;
    }

    public void createFilterForAuthenticatedRequest() {
        ModuleWebSecurityConfig module = objectObjectPostProcessor.postProcess(new ModuleWebSecurityConfig(null));
        module.setObjectPostProcessor(objectObjectPostProcessor);
    }

    public AuthenticationsPolicyType getDefaultAuthenticationPolicy() throws SchemaException {
        if (authenticationPolicy == null) {
            authenticationPolicy = SecurityPolicyUtil.createDefaultAuthenticationPolicy(prismContext.getSchemaRegistry());
        }
        return authenticationPolicy;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        doFilterInternal(request, response, chain);
    }

    private void doFilterInternal(ServletRequest request, ServletResponse response,
                                  FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        //request for permit all page (for example errors and login pages)
        if (SecurityUtils.isPermitAll(httpRequest) && !SecurityUtils.isLoginPage(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        MidpointAuthentication mpAuthentication = (MidpointAuthentication) SecurityContextHolder.getContext().getAuthentication();

        AuthenticationsPolicyType authenticationsPolicy;
        CredentialsPolicyType credentialsPolicy = null;
        PrismObject<SecurityPolicyType> authPolicy = null;
        try {
            authPolicy = getSecurityPolicy();
            authenticationsPolicy = getAuthenticationPolicy(authPolicy);
            if (authPolicy != null) {
                credentialsPolicy = authPolicy.asObjectable().getCredentials();
            }
        } catch (SchemaException e) {
            LOGGER.error("Couldn't load Authentication policy", e);
            try {
                authenticationsPolicy = getDefaultAuthenticationPolicy();
            } catch (SchemaException schemaException) {
                LOGGER.error("Couldn't get default authentication policy");
                throw new IllegalArgumentException("Couldn't get default authentication policy", e);
            }
        }

        //is path for which is ignored authentication
        if (SecurityUtils.isIgnoredLocalPath(authenticationsPolicy, httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        AuthenticationSequenceType sequence = getAuthenticationSequence(mpAuthentication, httpRequest, authenticationsPolicy);
        if (sequence == null) {
            throw new IllegalArgumentException("Couldn't find sequence for URI '" + httpRequest.getRequestURI() + "' in authentication of Security Policy with oid " + authPolicy.getOid());
        }

        //change generic logout path to logout path for actual module
        getPreLogoutFilter().doFilter(request, response);

        AuthenticationChannel authenticationChannel = SecurityUtils.buildAuthChannel(authChannelRegistry, sequence);

        List<AuthModule> authModules = createAuthenticationModuleBySequence(mpAuthentication, sequence, httpRequest, authenticationsPolicy.getModules()
                ,authenticationChannel, credentialsPolicy);

        //authenticated request
        if (mpAuthentication != null && mpAuthentication.isAuthenticated() && sequence.equals(mpAuthentication.getSequence())) {
            processingOfAuthenticatedRequest(mpAuthentication, httpRequest, response, chain);
            return;
        }

        //couldn't find authentication modules
        if (authModules == null || authModules.size() == 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(UrlUtils.buildRequestUrl(httpRequest)
                        +  "has no filters");
            }
            throw new AuthenticationServiceException("Couldn't find filters for sequence " + sequence.getName());
        }

        int indexOfProcessingModule = getIndexOfActualProcessingModule(mpAuthentication, httpRequest);

        resolveErrorWithMoreModules(mpAuthentication, httpRequest);

        if (needRestartAuthFlow(indexOfProcessingModule)) {
            indexOfProcessingModule = restartAuthFlow(mpAuthentication, httpRequest, sequence, authModules);
            mpAuthentication = (MidpointAuthentication) SecurityContextHolder.getContext().getAuthentication();
        }

        if (mpAuthentication.getAuthenticationChannel() == null) {
            mpAuthentication.setAuthenticationChannel(authenticationChannel);
        }

        MidpointAuthFilter.VirtualFilterChain vfc = new MidpointAuthFilter.VirtualFilterChain(httpRequest, chain, authModules.get(indexOfProcessingModule).getSecurityFilterChain().getFilters());
        vfc.doFilter(httpRequest, response);
    }

    private boolean needRestartAuthFlow(int indexOfProcessingModule) {
        // if index == -1 indicate restart authentication flow
        return indexOfProcessingModule == -1;
    }

    private int restartAuthFlow(MidpointAuthentication mpAuthentication, HttpServletRequest httpRequest, AuthenticationSequenceType sequence, List<AuthModule> authModules) {
        SecurityContextHolder.getContext().setAuthentication(null);
        SecurityContextHolder.getContext().setAuthentication(new MidpointAuthentication(sequence));
        mpAuthentication = (MidpointAuthentication) SecurityContextHolder.getContext().getAuthentication();
        mpAuthentication.setAuthModules(authModules);
        mpAuthentication.setSessionId(httpRequest.getSession(false) != null ? httpRequest.getSession(false).getId() : RandomStringUtils.random(30, true, true).toUpperCase());
        mpAuthentication.addAuthentications(authModules.get(0).getBaseModuleAuthentication());
        return mpAuthentication.resolveParallelModules(httpRequest, 0);
    }

    private void resolveErrorWithMoreModules(MidpointAuthentication mpAuthentication, HttpServletRequest httpRequest) {
        //authentication flow fail and exist more as one authentication module write error
        if (mpAuthentication != null && mpAuthentication.isAuthenticationFailed() && mpAuthentication.getAuthModules().size() > 1) {

            Exception actualException = (Exception) httpRequest.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            String actualMessage;
            String restartFlowMessage = "web.security.flexAuth.restart.flow";
            if (actualException != null && StringUtils.isNotBlank(actualException.getMessage())) {
                actualMessage = actualException.getMessage() + ";" + restartFlowMessage;
            } else {
                actualMessage = restartFlowMessage;
            }
            AuthenticationException exception = new AuthenticationServiceException(actualMessage);
            SecurityUtils.saveException(httpRequest, exception);
        }
    }

    private int getIndexOfActualProcessingModule(MidpointAuthentication mpAuthentication, HttpServletRequest request) {
        int indexOfProcessingModule = -1;
        // if exist authentication (authentication flow is processed) find actual processing module
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            indexOfProcessingModule = mpAuthentication.getIndexOfProcessingModule(true);
            indexOfProcessingModule = mpAuthentication.resolveParallelModules(request, indexOfProcessingModule);
        }
        return indexOfProcessingModule;
    }

    private List<AuthModule> createAuthenticationModuleBySequence(MidpointAuthentication mpAuthentication, AuthenticationSequenceType sequence,
            HttpServletRequest httpRequest, AuthenticationModulesType modules, AuthenticationChannel authenticationChannel, CredentialsPolicyType credentialsPolicy) {
        List<AuthModule> authModules;
        //change sequence of authentication during another sequence
        if (mpAuthentication == null || !sequence.equals(mpAuthentication.getSequence())) {
            SecurityContextHolder.getContext().setAuthentication(null);
            authenticationManager.getProviders().clear();
            authModules = SecurityUtils.buildModuleFilters(authModuleRegistry, sequence, httpRequest, modules,
                    credentialsPolicy, sharedObjects, authenticationChannel);
        } else {
            authModules = mpAuthentication.getAuthModules();
        }
        return authModules;
    }

    private AuthenticationSequenceType getAuthenticationSequence(MidpointAuthentication mpAuthentication, HttpServletRequest httpRequest, AuthenticationsPolicyType authenticationsPolicy) {
        AuthenticationSequenceType sequence;
        // permitAll pages (login, select ID for saml ...) during processing of modules
        if (mpAuthentication != null && SecurityUtils.isLoginPage(httpRequest)) {
            sequence = mpAuthentication.getSequence();
        } else {
            sequence = SecurityUtils.getSequenceByPath(httpRequest, authenticationsPolicy, taskManager.getLocalNodeGroups());
        }

        // use same sequence if focus is authenticated and channel id of new sequence is same
        if (mpAuthentication != null && !mpAuthentication.getSequence().equals(sequence) && mpAuthentication.isAuthenticated()
                && (((sequence != null && sequence.getChannel() != null && mpAuthentication.getAuthenticationChannel().matchChannel(sequence)))
                || mpAuthentication.getAuthenticationChannel().getChannelId().equals(SecurityUtils.findChannelByRequest(httpRequest)))) {
            //change logout path to new sequence
            if (SecurityUtils.isBasePathForSequence(httpRequest, sequence)) {
                mpAuthentication.getAuthenticationChannel().setPathAfterLogout(httpRequest.getServletPath());
                ModuleAuthentication authenticatedModule = SecurityUtils.getAuthenticatedModule();
                authenticatedModule.setInternalLogout(true);
            }
            sequence = mpAuthentication.getSequence();

        }
        return sequence;
    }

    private AuthenticationsPolicyType getAuthenticationPolicy(PrismObject<SecurityPolicyType> authPolicy) throws SchemaException {
        //security policy without authentication
        AuthenticationsPolicyType authenticationsPolicy;
        if (authPolicy == null || authPolicy.asObjectable().getAuthentication() == null
                || authPolicy.asObjectable().getAuthentication().getSequence() == null
                || authPolicy.asObjectable().getAuthentication().getSequence().isEmpty()) {
            authenticationsPolicy = getDefaultAuthenticationPolicy();
        } else {
            authenticationsPolicy = authPolicy.asObjectable().getAuthentication();
        }
        return authenticationsPolicy;
    }

    private PrismObject<SecurityPolicyType> getSecurityPolicy() throws SchemaException {
        return systemObjectCache.getSecurityPolicy(new OperationResult("load security policy"));
    }

    private void processingOfAuthenticatedRequest(MidpointAuthentication mpAuthentication, ServletRequest httpRequest, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        for (ModuleAuthentication moduleAuthentication : mpAuthentication.getAuthentications()) {
            if (StateOfModule.SUCCESSFULLY.equals(moduleAuthentication.getState())) {
                int i = mpAuthentication.getIndexOfModule(moduleAuthentication);
                MidpointAuthFilter.VirtualFilterChain vfc = new MidpointAuthFilter.VirtualFilterChain(httpRequest, chain,
                        mpAuthentication.getAuthModules().get(i).getSecurityFilterChain().getFilters());
                vfc.doFilter(httpRequest, response);
            }
        }
    }

    private static class VirtualFilterChain implements FilterChain {
        private final FilterChain originalChain;
        private final List<Filter> additionalFilters;
        private final int size;
        private int currentPosition = 0;

        private VirtualFilterChain(ServletRequest firewalledRequest,
                                   FilterChain chain, List<Filter> additionalFilters) {
            this.originalChain = chain;
            this.additionalFilters = additionalFilters;
            this.size = additionalFilters.size();
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            if (currentPosition == size) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(UrlUtils.buildRequestUrl((HttpServletRequest) request)
                            + " reached end of additional filter chain; proceeding with original chain, if url is permit all");
                }

//                MidpointAuthentication mpAuthentication = (MidpointAuthentication) SecurityContextHolder.getContext().getAuthentication();
//                //authentication pages (login, select ID for saml ...) during processing of modules
//                if (AuthUtil.isPermitAll((HttpServletRequest) request) && mpAuthentication != null && mpAuthentication.isProcessing()) {
//                    originalChain.doFilter(request, response);
//                    return;
//                }
                originalChain.doFilter(request, response);
            }
            else {
                currentPosition++;

                Filter nextFilter = additionalFilters.get(currentPosition - 1);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(UrlUtils.buildRequestUrl((HttpServletRequest) request)
                            + " at position " + currentPosition + " of " + size
                            + " in additional filter chain; firing Filter: '"
                            + nextFilter.getClass().getSimpleName() + "'");
                }
                nextFilter.doFilter(request, response, this);
            }
        }
    }

    public interface FilterChainValidator {
        void validate(MidpointAuthFilter filterChainProxy);
    }

    private static class NullFilterChainValidator implements MidpointAuthFilter.FilterChainValidator {
        @Override
        public void validate(MidpointAuthFilter filterChainProxy) {
        }
    }

}

