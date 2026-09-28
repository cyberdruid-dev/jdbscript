package org.jdbscript.impl;

import org.jdbscript.IDBRecordTools;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.javassist.ClassScriptWrapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;

public class ScriptHandler<T extends IDBSchema> {
    private final static String DEFAULTS_METHOD_NAME = "defaults";

    private final JDBScript dbScript = new JDBScript();
    private final Map<String,Object> tableTools = new HashMap<>();
    private Class<T> schemaClass;

    private static class AddedRecord {
        JDBRecord record;
        Object recordProxy;
        Class<?> tableDescriptionClass;
        AddedRecord(Class<?> tableDescriptionClass, JDBRecord record, Object recordProxy) {
            this.record = record;
            this.tableDescriptionClass = tableDescriptionClass;
            this.recordProxy = recordProxy;
        }
    }

    private final List<AddedRecord> records = new ArrayList<>();

    private final InvocationHandler scriptHandler = (dbProxy, method, args) -> {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(dbProxy, method, args);
        }
        if (method.getDeclaringClass() == Object.class) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return "JDbSchemaProxy [" + schemaClass.getName() + "]";
            }
            if ("equals".equals(name)) {
                return dbProxy == args[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(dbProxy);
            }
            return method.invoke(this, args);
        }
        if (method.getName().equals("include")) {
            if (args[0] instanceof Class<?>) {
                ClassScriptWrapper<T> wrapper = new ClassScriptWrapper<>((Class<? extends T>) args[0], this.schemaClass);
                wrapper.getDbScript((T) dbProxy);
            } else if (args[0] instanceof Consumer<?>) {
                Consumer includedScript = (Consumer) args[0];
                includedScript.accept(ScriptHandler.this.getProxy());
            } else {
                throw new JDBScriptException("Don't know how to handle argument " + args[0]);
            }
            return null;
        }

        Class<?> type = method.getReturnType();
        if (IDBRecord.class.isAssignableFrom(type)) {
            String tableName = method.getName();
            JDBRecord record = new JDBRecord(tableName);
            dbScript.addRecord(record);
            Object recordProxy = newProxy(type, new TableRecordHandler(record));
            records.add(new AddedRecord(type, record, recordProxy));
            return recordProxy;
        }
        return null;
    };

    public void applyDefaults() {
        for(var entry: records) {
            applyDefaults(entry.recordProxy, entry.tableDescriptionClass, entry.record);
        }
    }

    private Optional<Method> findDefaultsMethod(Class<?> type){
        return Arrays.stream(type.getMethods())
                .filter(m->m.getName().equals(DEFAULTS_METHOD_NAME))
                .findFirst();
    }

    private void applyDefaults(Object recordProxy, Class<?> type, JDBRecord record) {
        findDefaultsMethod(type).ifPresent(m->{
                    try {
                        Object decorator = newProxy(type, new NotOverridingInvocationDecorator(record, recordProxy));
                        Optional<Object> tools = getTableTools(m, record, type);
                        if(tools.isEmpty()){
                            InvocationHandler.invokeDefault(decorator, m);
                        } else {
                            InvocationHandler.invokeDefault(decorator, m, tools.get());
                        }
                    } catch (Throwable e) {
                        String msg = "Fail to call %s() on %s".formatted(DEFAULTS_METHOD_NAME, type.getName());
                        throw new JDBScriptException(msg, e);
                    }
                });

    }

    private Optional<Object> getTableTools(Method m, JDBRecord record, Class<?> type)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {

        if(m.getParameterTypes().length > 1) {
            String msg = "Fail to call %s() on %s. Method should declared 0 or 1 parameters";
            msg = msg.formatted(DEFAULTS_METHOD_NAME, type.getName());
            throw new JDBScriptException(msg);
        }
        Optional<Object> result = Optional.empty();
        if(m.getParameterTypes().length == 1) {
            String tableName = record.getTableName();
            if(!tableTools.containsKey(tableName)) {
                Object tools = m.getParameterTypes()[0].getConstructor().newInstance();
                tableTools.put(tableName, tools);
            }
            Object tools = tableTools.get(tableName);
            if(tools instanceof IDBRecordTools) {
                ((IDBRecordTools)tools).setRecord(record);
            }
            result = Optional.of(tools);
        }
        return result;
    }

    private final T scriptProxy;

    public ScriptHandler(Class<T> dbSchemaClass) {
        this.schemaClass = dbSchemaClass;
        scriptProxy = newProxy(dbSchemaClass, scriptHandler);
    }

    public JDBScript getDbScript() {
        return dbScript;
    }


    public T getProxy() {
        return this.scriptProxy;
    }

    private <P> P newProxy(Class<P> clazz, InvocationHandler handler) {
        return (P) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class<?>[]{clazz}, handler);
    }

    private static class NotOverridingInvocationDecorator implements InvocationHandler {
        private final JDBRecord record;
        private final Object nextProxy;

        NotOverridingInvocationDecorator(JDBRecord record, Object nextProxy) {
            this.record = record;
            this.nextProxy = nextProxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                String name = method.getName();
                if ("toString".equals(name)) {
                    return "NotOverridingDecorator [" + nextProxy.toString() + "]";
                }
                if ("equals".equals(name)) {
                    return proxy == args[0];
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                return method.invoke(this, args);
            }

            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            if (!record.hasValueFor(method.getName())) {
                Object result = method.invoke(nextProxy, args);
                if (result == nextProxy) {
                    return proxy;
                }
                return result;
            }
            return proxy;
        }
    }

}
