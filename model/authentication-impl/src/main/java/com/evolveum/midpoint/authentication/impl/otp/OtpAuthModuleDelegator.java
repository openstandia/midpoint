/*
 * Copyright (c) 2010-2026 Evolveum and contributors
 *
 * Licensed under the EUPL-1.2 or later.
 */

package com.evolveum.midpoint.authentication.impl.otp;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

import com.evolveum.midpoint.authentication.api.AuthModule;
import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Custom {@link AuthModule} implementation that decides whether an OTP authentication is needed for current
 * user or not, based on the module strategy and user enrollment.
 */
public class OtpAuthModuleDelegator implements AuthModule<OtpModuleAuthentication> {

    private final AuthModule<OtpModuleAuthentication> module;

    private final OtpAuthenticationModuleType moduleType;

    public OtpAuthModuleDelegator(AuthModule<OtpModuleAuthentication> module, OtpAuthenticationModuleType moduleType) {
        this.module = module;
        this.moduleType = moduleType;
    }

    @Override
    public OtpModuleAuthentication getBaseModuleAuthentication() {
        return module.getBaseModuleAuthentication();
    }

    @Override
    public String getModuleIdentifier() {
        return module.getModuleIdentifier();
    }

    @Override
    public Integer getOrder() {
        return module.getOrder();
    }

    @Override
    public List<AuthenticationProvider> getAuthenticationProviders() {
        return module.getAuthenticationProviders();
    }

    @Override
    public SecurityFilterChain getSecurityFilterChain() {
        return module.getSecurityFilterChain();
    }

    @Override
    public boolean canSkipWithSuccess(MidpointAuthentication ma) {
        OtpModuleStrategyType strategy = moduleType.getStrategy();
        if (strategy != OtpModuleStrategyType.ENROLLED) {
            // show OTP authentication page to everyone
            return false;
        }

        if (!(ma.getPrincipal() instanceof MidPointPrincipal principal)) {
            return false;
        }

        // show OTP authentication page only to users who have OTP enrolled
        FocusType focus = principal.getFocus();
        if (focus == null) {
            return false;
        }

        PrismContainer<OtpCredentialType> totp = focus.asPrismObject().findContainer(
                ItemPath.create(FocusType.F_CREDENTIALS, CredentialsType.F_OTPS, OtpCredentialsType.F_TOTP));

        return totp == null;
    }
}
