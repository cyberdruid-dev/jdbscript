package org.jdbscript.errors;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

public enum JdbsErrors implements Supplier<JDBScriptException> {
    EXECUTOR_IS_NULL(JDBScriptException.class, "executor class can not be null."),
    DATASOURCE_IS_NULL(JDBScriptException.class, "datasource can not be null."),
    DATASOURCE_IS_NOT_CONFIGURED(JDBScriptException.class, "datasource is not configured."),
    DATASOURCE_ALREADY_SET(JDBScriptException.class, "datasource already set."),
    EXECUTOR_ALREADY_SET(JDBScriptException.class, "executor already set."),
    INNER_CLASS_SHOULD_BE_STATIC(JDBScriptException.class,"Inner script class should be static." ),
    DB_SCHEMA_IS_NULL(JDBScriptException.class,"database schema can not be null."  ),
    DATASOURCE_SUPPLIER_IS_NULL(JDBScriptException.class,"datasource supplier can not be null."  );

    private final Class<? extends JDBScriptException> exception;
    private final String message;

    JdbsErrors(Class<? extends JDBScriptException> exceptionClass, String message) {
        this.exception = exceptionClass;
        this.message = message;
    }

    @Override
    public JDBScriptException get() {
        try {
            return exception.getConstructor(String.class)
                    .newInstance(message);
        } catch (NoSuchMethodException
                 | InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException e) {
            String msg = "%s: %s expected to have constructor(String).";
            msg = String.format(msg, this, exception.getSimpleName());
            throw new JDBScriptException(msg, e);
        }
    }
}
