package org.jdbscript.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

class TableRecordHandler implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(TableRecordHandler.class);
    private final JDbRecord record;

    TableRecordHandler(JDbRecord record) {
        this.record = record;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        if (method.getDeclaringClass() == Object.class) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return record.toString();
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            return method.invoke(record, args);
        }

        if (args == null || args.length != 1) {
            throw new IllegalArgumentException("Method " + method.getName() + " is treated as a column setter but has " + (args == null ? 0 : args.length) + " arguments. Exactly 1 is expected.");
        }

        String column = method.getName();
        Object value = args[0];
        if (value == null) {
            TypedNull typedNull = new TypedNull(method.getParameterTypes()[0]);
            record.setColumnValue(column, typedNull);
        } else {
            record.setColumnValue(column, value);
        }
        return proxy;
    }
}
