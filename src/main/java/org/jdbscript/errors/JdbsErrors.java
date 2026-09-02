package org.jdbscript.errors;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

/**
 * Predefined error types and standard messages for JDBScript validation and runtime failures.
 */
public enum JdbsErrors implements Supplier<JDBScriptException> {
    /** The provided executor class/instance is null. */
    EXECUTOR_IS_NULL(JDBScriptException.class, "executor class can not be null."),
    /** The provided data source is null. */
    DATASOURCE_IS_NULL(JDBScriptException.class, "datasource can not be null."),
    /** The data source is not configured. */
    DATASOURCE_IS_NOT_CONFIGURED(JDBScriptException.class, "datasource is not configured."),
    /** The data source has already been configured on the engine. */
    DATASOURCE_ALREADY_SET(JDBScriptException.class, "datasource already set."),
    /** The executor has already been configured on the engine. */
    EXECUTOR_ALREADY_SET(JDBScriptException.class, "executor already set."),
    /** Inner script classes must be declared static. */
    INNER_CLASS_SHOULD_BE_STATIC(JDBScriptException.class,"Inner script class should be static." ),
    /** The database schema class cannot be null. */
    DB_SCHEMA_IS_NULL(JDBScriptException.class,"database schema can not be null."  ),
    /** The data source supplier cannot be null. */
    DATASOURCE_SUPPLIER_IS_NULL(JDBScriptException.class,"datasource supplier can not be null."  ),
    /** The metadata provider cannot be null. */
    METADATA_PROVIDER_IS_NULL(JDBScriptException.class,"metadata provider can not be null."  ),
    CACHE_STRATEGY_IS_NULL(JDBScriptException.class,"cache strategy can not be null."  ),
    /** Table defined in interface but missing from DB. */
    MISSING_TABLE_IN_DB(JDBScriptException.class, "Table '%s' defined in interface %s but missing from DB"),
    /** Table found in DB but missing from schema interface. */
    UNMAPPED_TABLE_IN_DB(JDBScriptException.class, "Table '%s' found in DB but missing from schema interface %s");

    private final Class<? extends JDBScriptException> exception;
    private final String message;

    JdbsErrors(Class<? extends JDBScriptException> exceptionClass, String message) {
        this.exception = exceptionClass;
        this.message = message;
    }

    /**
     * Creates and returns a new {@link JDBScriptException} instance configured with this error's message.
     *
     * @return the newly created {@link JDBScriptException}
     */
    @Override
    public JDBScriptException get() {
        return get((Object[]) null);
    }

    /**
     * Creates and returns a new {@link JDBScriptException} instance configured with this error's message
     * formatted with the provided arguments.
     *
     * @param args the arguments to format the message with
     * @return the newly created {@link JDBScriptException}
     */
    public JDBScriptException get(Object... args) {
        try {
            String formattedMessage = (args == null || args.length == 0) ? message : String.format(message, args);
            return exception.getConstructor(String.class)
                    .newInstance(formattedMessage);
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
