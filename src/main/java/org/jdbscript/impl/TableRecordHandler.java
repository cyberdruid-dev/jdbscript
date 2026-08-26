package org.jdbscript.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

class TableRecordHandler implements InvocationHandler {
    private static final Logger log = LoggerFactory.getLogger(TableRecordHandler.class);
    private final JDbRecord record;

    TableRecordHandler(JDbRecord record) {
        this.record = record;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if(Objects.equals(method.getName(), "defaults")) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        String column = method.getName();
        Object value = args[0];
        if(value == null) {
            TypedNull typedNull = new TypedNull(method.getParameterTypes()[0]);
            record.setColumnValue(column, typedNull);
        } else {
            record.setColumnValue(column, args[0]);
        }
        return proxy;
    }
}
