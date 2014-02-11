/*
 * Copyright (c) 2010-2014 Evolveum
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
package com.evolveum.midpoint.model.common.expression;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;

import org.w3c.dom.Element;

import javax.xml.namespace.QName;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author Radovan Semancik
 */
public class ExpressionVariables implements DebugDumpable {

    private Map<QName, Object> variables = new HashMap<QName, Object>();

    private static final Trace LOGGER = TraceManager.getTrace(ExpressionVariables.class);

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) the operation fail because
     * it cannot decide which version to use.
     */
    public void addVariableDefinitions(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		if (odo.getObjectDelta() != null) {
        			throw new IllegalArgumentException("Cannot use variables with deltas in addVariableDefinitions, use addVariableDefinitionsOld or addVariableDefinitionsNew");
        		}
        		value = odo.getOldObject();
        	}
            variables.put(entry.getKey(), value);
        }
    }
    
    public void addVariableDefinitions(ExpressionVariables extraVariables) {
    	addVariableDefinitions(extraVariables.getMap());
    }

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) it takes the "old" version
     * of the object.
     */
    public void addVariableDefinitionsOld(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		value = odo.getOldObject();
        	} else if (value instanceof ItemDeltaItem<?>) {
        		ItemDeltaItem<?> idi = (ItemDeltaItem<?>)value;
        		value = idi.getItemOld();
        	}
            variables.put(entry.getKey(), value);
        }
    }
    
    public void addVariableDefinitionsOld(ExpressionVariables extraVariables) {
    	addVariableDefinitionsOld(extraVariables.getMap());
    }

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) it takes the "new" version
     * of the object.
     */
    public void addVariableDefinitionsNew(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		value = odo.getNewObject();
        	} else if (value instanceof ItemDeltaItem<?>) {
        		ItemDeltaItem<?> idi = (ItemDeltaItem<?>)value;
        		value = idi.getItemNew();
        	}
            variables.put(entry.getKey(), value);
        }
    }
    
    public void addVariableDefinitionsNew(ExpressionVariables extraVariables) {
    	addVariableDefinitionsNew(extraVariables.getMap());
    }
    
    public void setRootNode(ObjectReferenceType objectRef) {
        addVariableDefinition(null, (Object) objectRef);
    }

    public void addVariableDefinition(QName name, Object value) {
        if (variables.containsKey(name)) {
            LOGGER.warn("Duplicate definition of variable {}", name);
            return;
        }
        variables.put(name, value);
    }
    
    public boolean hasVariableDefinition(QName name) {
    	return variables.containsKey(name);
    }
    
    public Object get(QName name) {
    	return variables.get(name);
    }
    
    public Set<Entry<QName,Object>> entrySet() {
    	return variables.entrySet();
    }

    public int size() {
		return variables.size();
	}

	public boolean isEmpty() {
		return variables.isEmpty();
	}

	public boolean containsKey(Object key) {
		return variables.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return variables.containsValue(value);
	}

	public Set<QName> keySet() {
		return variables.keySet();
	}

	public String formatVariables() {
        StringBuilder sb = new StringBuilder();
        Iterator<Entry<QName, Object>> i = variables.entrySet().iterator();
        while (i.hasNext()) {
            Entry<QName, Object> entry = i.next();
            SchemaDebugUtil.indentDebugDump(sb, 1);
            sb.append(SchemaDebugUtil.prettyPrint(entry.getKey())).append(": ");
            Object value = entry.getValue();
            if (value instanceof DebugDumpable) {
            	sb.append("\n");
            	sb.append(((DebugDumpable)value).debugDump(2));
            } else if (value instanceof Element) {
            	sb.append("\n");
            	sb.append(DOMUtil.serializeDOMToString(((Element)value)));
            } else {
            	sb.append(SchemaDebugUtil.prettyPrint(value));
            }
            if (i.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Expects QName-value pairs.
     * 
     * E.g.
     * create(var1qname, var1value, var2qname, var2value, ...)
     * 
     * Mostly for testing. Use at your own risk.
     */
    public static ExpressionVariables create(Object... parameters) {
    	ExpressionVariables vars = new ExpressionVariables();
    	for (int i = 0; i < parameters.length; i += 2) {
    		vars.addVariableDefinition((QName)parameters[i], parameters[i+1]);
    	}
    	return vars;
    }

    public Map<QName, Object> getMap() {
    	return variables;
    }
    
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((variables == null) ? 0 : variables.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExpressionVariables other = (ExpressionVariables) obj;
		if (variables == null) {
			if (other.variables != null)
				return false;
		} else if (!variables.equals(other.variables))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "variables(" + variables + ")";
	}

	@Override
	public String debugDump() {
		return debugDump(0);
	}

	@Override
	public String debugDump(int indent) {
		StringBuilder sb = new StringBuilder();
		DebugUtil.debugDumpMapMultiLine(sb, variables, 1);
		return sb.toString();
	}
    
}
