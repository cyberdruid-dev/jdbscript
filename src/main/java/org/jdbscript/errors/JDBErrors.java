package org.jdbscript.errors;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

/**
 * Predefined error types and standard messages for JDBScript validation and runtime failures.
 */
public enum JDBErrors implements Supplier<JDBScriptException> {
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
    /** A converter passed to Builder.converter(...) cannot be null. */
    CONVERTER_IS_NULL(JDBScriptException.class,"converter can not be null."  ),
    /** The unmapped-table validation strategy cannot be null. */
    UNMAPPED_TABLE_STRATEGY_IS_NULL(JDBScriptException.class,"unmapped table strategy can not be null."  ),
    /** A feature passed to Builder.feature(...) cannot be null. */
    FEATURE_IS_NULL(JDBScriptException.class,"feature can not be null."  ),
    /** A callback passed to Builder.onDataChange(...) cannot be null. */
    DATA_CHANGE_LISTENER_IS_NULL(JDBScriptException.class,"data change listener can not be null."  ),
    /** JDBMigrationEngine.builder(from, to)'s pre-migration schema class cannot be null. */
    FROM_SCHEMA_IS_NULL(JDBScriptException.class,"the pre-migration (from) schema class can not be null."  ),
    /** JDBMigrationEngine.builder(from, to)'s post-migration schema class cannot be null. */
    TO_SCHEMA_IS_NULL(JDBScriptException.class,"the post-migration (to) schema class can not be null."  ),
    /** A migrator passed to JDBMigrationEngine.Builder#migrator(...) cannot be null. */
    MIGRATOR_IS_NULL(JDBScriptException.class,"migrator can not be null."  ),
    /** Table defined in interface but missing from DB. */
    MISSING_TABLE_IN_DB(JDBScriptException.class, "Table '%s' defined in interface %s but missing from DB"),
    /** Table found in DB but missing from schema interface. */
    UNMAPPED_TABLE_IN_DB(JDBScriptException.class, "Table '%s' found in DB but missing from schema interface %s"),
    /** Script classes must have a no-args constructor and no other constructors. */
    SCRIPT_CONSTRUCTOR_HAS_PARAMETERS(JDBScriptException.class, "Script class '%s' should not have constructors with parameters."),
    /** A table declared in the schema interface is missing from Builder.tableDependencyOrder(...). */
    TABLE_MISSING_FROM_DEPENDENCY_ORDER(JDBScriptException.class,
            "Table(s) declared in schema interface but missing from tableDependencyOrder(...): %s"),
    /** assertDBHas/assertDBHasNot called with a record that has no columns set. */
    EMPTY_ASSERTION_RECORD(JDBScriptException.class,
            "assertDBHas/assertDBHasNot requires at least one column to be set to match against, "
                    + "but table '%s' had none set."),
    /** updateDB found no row with the record's primary key. */
    UPDATE_ROW_NOT_FOUND(JDBScriptException.class,
            "updateDB found no row in table '%s' with primary key %s."),
    /** updateDB can only find a row by its primary key. */
    UPDATE_TABLE_HAS_NO_PRIMARY_KEY(JDBScriptException.class,
            "updateDB finds the row to update by its primary key, but table '%s' has no primary key."),
    /** updateDB needs every primary key column set, or it could update several rows. */
    UPDATE_PRIMARY_KEY_NOT_SET(JDBScriptException.class,
            "updateDB needs every primary key column set to find the row in table '%s'; missing: %s."),
    /** updateDB record sets only primary key columns. */
    UPDATE_NOTHING_TO_SET(JDBScriptException.class,
            "updateDB has nothing to update in table '%s': only primary key columns were set.");

    private final Class<? extends JDBScriptException> exception;
    private final String message;

    JDBErrors(Class<? extends JDBScriptException> exceptionClass, String message) {
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
