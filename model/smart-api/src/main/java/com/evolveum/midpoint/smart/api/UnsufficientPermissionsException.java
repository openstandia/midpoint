/*
 * Copyright (C) 2026 Evolveum and contributors
 *
 * Licensed under the EUPL-1.2 or later.
 *
 */

package com.evolveum.midpoint.smart.api;

import com.evolveum.midpoint.util.exception.CommonException;

public class UnsufficientPermissionsException extends CommonException {

    public UnsufficientPermissionsException(String message) {
        super(message);
    }

    @Override
    public String getErrorTypeMessage() {
        return "Unsufficient permissions";
    }

}
