/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action;

import java.io.File;

public interface BasicExportOptions {

    public static final String P_REPLACE_OID_MAPPING_FILE_LONG = "--replaceOid";

    File getOutput();

    boolean isOverwrite();

    boolean isZip();

    default void setReplaceOid(File replaceOid) {
        // No op
    }

    default File getReplaceOid() {
        return null;
    }
}
