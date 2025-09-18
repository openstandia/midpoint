/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.lens;

import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceObjectMultiplicityType;

/**
 * Utility methods for working with refined definitions.
 *
 * @author Radovan Semancik
 */
public class RefinedDefinitionUtil {

    /**
     * Determines if the given multiplicity configuration indicates a multiaccount object type.
     * Multiaccount means that multiple accounts of the same resource+kind+intent combination
     * can be created, differentiated by tags.
     *
     * @param multiplicity the multiplicity configuration from resource object type
     * @return true if this is a multiaccount object type (maxOccurs is unbounded or -1)
     */
    public static boolean isMultiaccount(ResourceObjectMultiplicityType multiplicity) {
        if (multiplicity == null) {
            return false;
        }
        String maxOccurs = multiplicity.getMaxOccurs();
        return maxOccurs != null && ("unbounded".equals(maxOccurs) || "-1".equals(maxOccurs));
    }
}
