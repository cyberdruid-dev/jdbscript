package org.jdbscript.impl;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
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

    private final Class<? extends IDbSchema> dbSchemaClass;
    private final IMetadataProvider provider;
    private boolean schemaValidated = false;

    /**
     * Creates a new schema validator.
     *
     * @param dbSchemaClass the schema interface class
     * @param provider the metadata provider to fetch DB tables
     */
    public SchemaValidator(Class<? extends IDbSchema> dbSchemaClass, IMetadataProvider provider) {
        this.dbSchemaClass = checkNotNull(dbSchemaClass, DB_SCHEMA_IS_NULL);
        this.provider = checkNotNull(provider, METADATA_PROVIDER_IS_NULL);
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
            if (!interfaceTables.contains(dbTable.toUpperCase())) {
                log.warn("Table '{}' found in DB but missing from schema interface {}", dbTable, dbSchemaClass.getSimpleName());
            }
        }

        for (String interfaceTable : interfaceTables) {
            if (!dbTableSet.contains(interfaceTable)) {
                log.error("Table '{}' defined in interface {} but missing from DB", interfaceTable, dbSchemaClass.getSimpleName());
            }
        }
        schemaValidated = true;
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
            Class<?> returnType = m.getReturnType();
            if (IDBRecord.class.isAssignableFrom(returnType)) {
                methods.add(m);
            }
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            methods.addAll(findRecordMethods(iface));
        }
        return methods;
    }
}
