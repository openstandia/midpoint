/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * Licensed under the EUPL-1.2 or later.
 */

package com.evolveum.midpoint.authentication.api;

import com.evolveum.midpoint.authentication.api.config.MidpointAuthentication;
import com.evolveum.midpoint.authentication.api.config.ModuleAuthentication;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Define authentication module created by module configuration, with all filters and configuration
 *
 * @author skublik
 */

public interface AuthModule<MA extends ModuleAuthentication> {

    /**
     * @return module authentication (result after authentication process)
     */
    MA getBaseModuleAuthentication();

    String getModuleIdentifier();

    /**
     * @return order of authentication module
     */
    Integer getOrder();

    List<AuthenticationProvider> getAuthenticationProviders();

    SecurityFilterChain getSecurityFilterChain();

    /**
     * Check if the module authentication can be skipped with success.
     * This is used for example in case of OTP module, when user is not enrolled to OTP, then the module can be skipped
     * with success and the authentication process can continue to the next module.
     *
     * @param ma
     * @return true if {@link ModuleAuthentication#setState(AuthenticationModuleState)} can be set to {@link AuthenticationModuleState#SUCCESSFULLY}.
     */
    default boolean canSkipWithSuccess(MidpointAuthentication ma) {
        return false;
    }
}
