package org.jdbscript.impl;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.ValidationStrategy;
import org.jdbscript.errors.JDBErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.DB_SCHEMA_IS_NULL;
import static org.jdbscript.errors.JDBErrors.METADATA_PROVIDER_IS_NULL;

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

    private final Class<? extends IDBSchema> dbSchemaClass;
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
    public SchemaValidator(Class<? extends IDBSchema> dbSchemaClass,
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
        List<String> interfaceTables = findRecordMethods(dbSchemaClass).stream()
                .map(Method::getName)
                .toList();
        Set<String> mappedDbTables = interfaceTables.stream()
                .map(provider::toDbTableName)
                .collect(Collectors.toSet());

        for (String dbTable : dbTables) {
            if (!mappedDbTables.contains(dbTable)) {
                if (isSuppressed(dbTable.toUpperCase())) {
                    continue;
                }
                String msg = "Table '%s' found in DB but missing from schema interface %s".formatted(dbTable, dbSchemaClass.getSimpleName());
                switch (unmappedTableStrategy) {
                    case IGNORE -> log.debug(msg);
                    case LOG_WARN -> log.warn(msg);
                    case LOG_ERROR -> log.error(msg);
                    case FAIL -> throw JDBErrors.UNMAPPED_TABLE_IN_DB.get(dbTable, dbSchemaClass.getSimpleName());
                }
            }
        }

        for (String interfaceTable : interfaceTables) {
            if (!dbTables.contains(provider.toDbTableName(interfaceTable))) {
                throw JDBErrors.MISSING_TABLE_IN_DB.get(interfaceTable.toUpperCase(), dbSchemaClass.getSimpleName());
            }
        }
        validateColumns();
        schemaValidated = true;
    }

    // One direction only: DB columns the record doesn't declare (audit columns, unused optional
    // ones) are normal. A table without any columns in the metadata is skipped: that means no
    // column metadata, not an empty table.
    private void validateColumns() {
        List<Method> tableMethods = findRecordMethods(dbSchemaClass);
        Map<String, List<String>> columnsByTable = provider.getColumns(tableMethods.stream()
                .map(m -> provider.toDbTableName(m.getName()))
                .distinct()
                .toList());
        List<String> problems = new ArrayList<>();
        for (Method tableMethod : tableMethods) {
            Class<?> recordClass = tableMethod.getReturnType();
            String dbTable = provider.toDbTableName(tableMethod.getName());
            List<String> dbColumns = columnsByTable.get(dbTable);
            if (dbColumns.isEmpty()) {
                continue;
            }
            for (String column : findColumnMethodNames(recordClass)) {
                if (dbColumns.stream().noneMatch(column::equalsIgnoreCase)) {
                    problems.add("  %s.%s(): no column '%s' in table %s (columns: %s)".formatted(
                            recordClass.getSimpleName(), column, column, dbTable, String.join(", ", dbColumns)));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw JDBErrors.SCHEMA_COLUMNS_MISSING_IN_DB.get(dbSchemaClass.getSimpleName(), String.join("\n", problems));
        }
    }

    private static Set<String> findColumnMethodNames(Class<?> recordClass) {
        return Arrays.stream(recordClass.getMethods())
                .filter(m -> !m.isDefault() && !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class)
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
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
