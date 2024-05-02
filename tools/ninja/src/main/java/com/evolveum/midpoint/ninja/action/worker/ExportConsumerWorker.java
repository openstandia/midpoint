/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action.worker;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import com.evolveum.midpoint.ninja.action.ExportOptions;
import com.evolveum.midpoint.ninja.impl.Log;
import com.evolveum.midpoint.ninja.impl.NinjaContext;
import com.evolveum.midpoint.ninja.util.NinjaUtils;
import com.evolveum.midpoint.ninja.util.OperationStatus;
import com.evolveum.midpoint.prism.PrismSerializer;
import com.evolveum.midpoint.prism.SerializationOptions;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import org.apache.commons.io.FileUtils;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ExportConsumerWorker extends AbstractWriterConsumerWorker<ExportOptions, ObjectType> {

    private PrismSerializer<String> serializer;
    private Map<String, UUID> replaceOidMap;

    public ExportConsumerWorker(NinjaContext context,
            ExportOptions options, BlockingQueue<ObjectType> queue, OperationStatus operation) {
        super(context, options, queue, operation);
    }

    @Override
    protected void init() {
        serializer = context.getPrismContext()
                .xmlSerializer()
                .options(SerializationOptions.createSerializeForExport().skipContainerIds(options.isSkipContainerIds()));

        File replace = options.getReplaceOid();
        if (replace != null && replace.isFile() && replace.canRead()) {
            try {
                List<String> line = FileUtils.readLines(replace, StandardCharsets.UTF_8);
                replaceOidMap = line.stream()
                        .map(l -> l.split(","))
                        .filter(v -> v.length == 2 && !v[0].isEmpty() && !v[1].isEmpty())
                        .collect(Collectors.toUnmodifiableMap(v -> v[0].trim(), v -> toUUID(v[1].trim())));
            } catch (RuntimeException e) {
                markDone();
                operation.finish();
                throw e;
            } catch (Exception e) {
                markDone();
                operation.finish();
                throw new RuntimeException(e);
            }
        }
    }

    private UUID toUUID(String s) {
        if (s.length() != 36) {
            throw new IllegalArgumentException("New oid must be UUID format: " + s);
        }
        try {
            UUID uuid = UUID.fromString(s);
            return uuid;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("New oid must be UUID format: " + s, e);
        }
    }

    @Override
    protected String getProlog() {
        return NinjaUtils.XML_OBJECTS_PREFIX;
    }

    @Override
    protected void write(Writer writer, ObjectType object) throws SchemaException, IOException {
        String xml = serializer.serialize(object.asPrismObject());
        if (replaceOidMap != null) {
            xml = replaceOid(replaceOidMap, xml);
        }
        writer.write(xml);
    }

    protected String replaceOid(Map<String, UUID> replaceMap, String xml) {
        Log log = context.getLog();

        for (Map.Entry<String, UUID> entry : replaceMap.entrySet()) {
            String oldValue = entry.getKey();
            String newValue = entry.getValue().toString();

            if (xml.contains(newValue)) {
                log.error("The new oid in replaceOid file has already been used in the repository: {}\n====\n{}\n====", newValue, xml);
                throw new IllegalArgumentException("The new oid in replaceOid file has already been used in the repository: " + newValue);
            }

            xml = xml.replace(oldValue, newValue);
        }
        return xml;
    }

    @Override
    protected String getEpilog() {
        return NinjaUtils.XML_OBJECTS_SUFFIX;
    }

}
