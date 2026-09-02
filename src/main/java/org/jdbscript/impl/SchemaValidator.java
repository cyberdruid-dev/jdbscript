package org.jdbscript.impl;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.ValidationStrategy;
import org.jdbscript.errors.JdbsErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.DB_SCHEMA_IS_NULL;
import static org.jdbscript.errors.JdbsErrors.METADATA_PROVIDER_IS_NULL;

/**
 * Internal component for validating that the database tables match the schema interface.
 */
public class SchemaValidator {
    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    private static final Set<String> DEFAULT_SUPPRESSED_TABLES = Set.of(
            "FLYWAY_SCHEMA_HISTORY",
            "SCHEMA_VERSION",
            "DATABASECHANGELOG",
            "DATABASECHANGELOGLOCK"
    );

    private final Class<? extends IDbSchema> dbSchemaClass;
    private final IMetadataProvider provider;
    private final ValidationStrategy unmappedTableStrategy;
    private final Set<String> suppressedTables;
    private final boolean suppressDefaultUnmappedTables;
    private boolean schemaValidated = false;

    /**
     * Creates a new schema validator.
     *
     * @param dbSchemaClass the schema interface class
     * @param provider the metadata provider to fetch DB tables
     * @param unmappedTableStrategy strategy for unmapped tables
     * @param suppressedTables set of suppressed table names (upper case)
     * @param suppressDefaultUnmappedTables whether to suppress default migration tables
     */
    public SchemaValidator(Class<? extends IDbSchema> dbSchemaClass,
                           IMetadataProvider provider,
                           ValidationStrategy unmappedTableStrategy,
                           Set<String> suppressedTables,
                           boolean suppressDefaultUnmappedTables) {
        this.dbSchemaClass = checkNotNull(dbSchemaClass, DB_SCHEMA_IS_NULL);
        this.provider = checkNotNull(provider, METADATA_PROVIDER_IS_NULL);
        this.unmappedTableStrategy = unmappedTableStrategy;
        this.suppressedTables = suppressedTables;
        this.suppressDefaultUnmappedTables = suppressDefaultUnmappedTables;
    }

    /**
     * Validates the database tables against the provided schema interface.
     */
    public synchronized void validate() {
        if (schemaValidated) {
            return;
        }

        List<String> dbTables = provider.getAllTables();
        Set<String> interfaceTables = findRecordMethods(dbSchemaClass).stream()
                .map(m -> m.getName().toUpperCase())
                .collect(Collectors.toSet());

        Set<String> dbTableSet = dbTables.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        for (String dbTable : dbTables) {
            String dbTableUpper = dbTable.toUpperCase();
            if (!interfaceTables.contains(dbTableUpper)) {
                if (isSuppressed(dbTableUpper)) {
                    continue;
                }
                String msg = "Table '%s' found in DB but missing from schema interface %s".formatted(dbTable, dbSchemaClass.getSimpleName());
                switch (unmappedTableStrategy) {
                    case LOG_WARN -> log.warn(msg);
                    case LOG_ERROR -> log.error(msg);
                    case FAIL -> throw JdbsErrors.UNMAPPED_TABLE_IN_DB.get(dbTable, dbSchemaClass.getSimpleName());
                }
            }
        }

        for (String interfaceTable : interfaceTables) {
            if (!dbTableSet.contains(interfaceTable)) {
                throw JdbsErrors.MISSING_TABLE_IN_DB.get(interfaceTable, dbSchemaClass.getSimpleName());
            }
        }
        schemaValidated = true;
    }

    private boolean isSuppressed(String tableNameUpper) {
        if (suppressedTables.contains(tableNameUpper)) {
            return true;
        }
        if (suppressDefaultUnmappedTables && DEFAULT_SUPPRESSED_TABLES.contains(tableNameUpper)) {
            return true;
        }
        return false;
    }

    /**
     * Finds all methods in the schema interface that return an {@link IDBRecord}.
     *
     * @param clazz the schema interface class
     * @return a list of record methods
     */
    public static List<Method> findRecordMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Method m : clazz.getMethods()) {
            if (m.isDefault() || m.getDeclaringClass() == Object.class) {
                continue;
            }
            Class<?> returnType = m.getReturnType();
            if (IDBRecord.class.isAssignableFrom(returnType)) {
                methods.add(m);
            }
        }
        return methods;
    }
}
