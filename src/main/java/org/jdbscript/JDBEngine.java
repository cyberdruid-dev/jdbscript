package org.jdbscript;

import org.jdbscript.errors.JDBErrors;
import org.jdbscript.impl.*;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.JDBCacheManager;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.jdbscript.impl.conversion.JDBTypeConverter;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.*;

/**
 * Standard implementation of {@link IJDBEngine} providing fluent database seeding,
 * script execution, and table cleanup for a specified schema interface.
 *
 * @param <T> the schema interface type extending {@link IDBSchema}
 */
public class JDBEngine<T extends IDBSchema> implements IJDBEngine<T>{
    private static final Logger log = LoggerFactory.getLogger(JDBEngine.class);

    private final JDBTypeConverter converter = new JDBTypeConverter();
    private final Class<T> dbSchemaClass;
    private final Supplier<DataSource> dataSourceSupplier;
    private final List<String> cleanupOrder;
    private final CacheStrategy cacheStrategy;
    private DataSource dataSource;
    private final IScriptExecutor executor;
    private SchemaValidator schemaValidator;
    private IJDBCache cache = new NoCache();
    private final ValidationStrategy unmappedTableStrategy;
    private final Set<String> suppressedTables;
    private final boolean suppressDefaultUnmappedTables;

    private JDBEngine(Builder<T> builder) {
        this.dbSchemaClass = checkNotNull(builder.dbSchemaClass, DB_SCHEMA_IS_NULL);
        this.dataSourceSupplier = checkNotNull(builder.dataSourceSupplier, DATASOURCE_SUPPLIER_IS_NULL);
        this.cleanupOrder = builder.cleanupOrder != null ? List.copyOf(builder.cleanupOrder) : null;
        this.cacheStrategy = builder.cacheStrategy;
        this.executor = builder.executor != null? builder.executor : new SqlScriptExecutor();
        this.unmappedTableStrategy = builder.unmappedTableStrategy;
        this.suppressedTables = Set.copyOf(builder.suppressedTables);
        this.suppressDefaultUnmappedTables = builder.suppressDefaultUnmappedTables;
        if (builder.converters != null) {
            this.converter.setConverters(builder.converters);
        }
    }

    /**
     * Creates a new builder for {@code JDBEngine}.
     *
     * @param dbSchemaClass the schema interface class modeling the database tables
     * @param <T>           the schema interface type
     * @return a new builder instance
     */
    public static <T extends IDBSchema> Builder<T> builder(Class<T> dbSchemaClass) {
        return new Builder<>(dbSchemaClass);
    }

    private static Supplier<DataSource> toSupplier(DataSource dataSource) {
        checkNotNull(dataSource, JDBErrors.DATASOURCE_IS_NULL);
        return ()->dataSource;
    }

    @Override
    public void resetDB(Class<? extends T> scriptClass) {
        cleanupDB();
        insertDB(scriptClass);
    }

    @Override
    public void insertDB(Class<? extends T> scriptClass) {
        log.debug("insertDB({})", scriptClass.getName());
        insertDB(db->{
            db.include(scriptClass);
        });
    }

    @Override
    public void resetDB(Consumer<T> db) {
        log.debug("resetDb(consumer={})", db);
        cleanupDB();
        insertDB(db);
    }

    @Override
    public void insertDB(Consumer<T> db) {
        log.debug("insertDB(consumer={})", db);
        validateSchema();
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        db.accept(handler.getProxy());
        handler.applyDefaults();
        JDBScript script = handler.getDbScript();
        converter.convertTypes(script);
        sortScript(script);
        getExecutor().insert(script);
    }

    private synchronized IJDBCache getCache() {
        if (cache instanceof NoCache && cacheStrategy != CacheStrategy.NONE) {
            cache = JDBCacheManager.getInstance().getCache(cacheStrategy, getDataSource());
        }
        return cache;
    }

    /**
     * Clears the metadata cache.
     */
    public void clearCache() {
        getCache().clear();
    }

    private void validateSchema() {
        ensureInitialized();
        schemaValidator.validate();
    }

    private synchronized void ensureInitialized() {
        if (schemaValidator == null) {
            this.executor.setDataSource(getDataSource());
            this.executor.setCache(getCache());
            this.schemaValidator = new SchemaValidator(dbSchemaClass, executor.getMetadataProvider(),
                    unmappedTableStrategy, suppressedTables, suppressDefaultUnmappedTables);
        }
    }

    private synchronized DataSource getDataSource() {
        if(dataSource == null){
            dataSource = checkNotNull(dataSourceSupplier.get(), DATASOURCE_IS_NULL) ;
        }
        return dataSource;
    }

    @Override
    public void cleanupDB() {
        getTableNames();
        getExecutor().cleanupTables(getTableNames());
    }

    @Override
    public void assertDBHasNot(Consumer<T> dbAsserts) {
        log.debug("assertDBHasNot(consumer={})", dbAsserts);
        validateSchema();
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        dbAsserts.accept(handler.getProxy());
        JDBScript script = handler.getDbScript();
        converter.convertTypes(script);
        getExecutor().assertRowsNotExist(script);
    }

    private IMetadataProvider getMetadataProvider() {
        return getExecutor().getMetadataProvider();
    }

    @Override
    public void assertDBHas(Consumer<T> dbAsserts) {
        log.debug("assertDBHas(consumer={})", dbAsserts);
        validateSchema();
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        dbAsserts.accept(handler.getProxy());
        JDBScript script = handler.getDbScript();
        converter.convertTypes(script);
        getExecutor().assertRowsExist(script);
    }

    private void sortScript(JDBScript script) {
        List<JDBRecord> records = script.getRecords();
        if (records.isEmpty()) {
            return;
        }

        records.sort(Comparator.comparing(JDBRecord::getTableName, getMetadataProvider().getParentChildTableComparator()));
    }

    private List<String> getTableNames() {
        if (cleanupOrder != null) {
            return cleanupOrder;
        }
        Set<String> interfaceTables = SchemaValidator.findRecordMethods(dbSchemaClass).stream()
                .map(m -> m.getName().toUpperCase())
                .collect(Collectors.toSet());

        List<String> sorted = new ArrayList<>(getExecutor().getMetadataProvider().getSortedTables());
        Collections.reverse(sorted);

        return sorted.stream()
                .filter(t -> interfaceTables.contains(t.toUpperCase()))
                .map(t -> {
                    // Try to restore original casing from interface if possible, 
                    // or just return the DB name. The DB name is fine.
                    return t;
                })
                .toList();
    }

    private IScriptExecutor getExecutor() {
        ensureInitialized();
        return executor;
    }

    /**
     * Builder for {@link JDBEngine}.
     *
     * @param <T> the schema interface type
     */
    public static class Builder<T extends IDBSchema> {
        private final Class<T> dbSchemaClass;
        private Supplier<DataSource> dataSourceSupplier;
        private IScriptExecutor executor;
        private Collection<IJDBTypeConverter> converters;
        private List<String> cleanupOrder;
        private CacheStrategy cacheStrategy = CacheStrategy.INSTANCE;
        private ValidationStrategy unmappedTableStrategy = ValidationStrategy.LOG_WARN;
        private final Set<String> suppressedTables = new HashSet<>();
        private boolean suppressDefaultUnmappedTables = true;

        private Builder(Class<T> dbSchemaClass) {
            this.dbSchemaClass = dbSchemaClass;
        }

        public Builder<T> dataSource(DataSource dataSource) {
            checkIsNull(this.dataSourceSupplier, DATASOURCE_ALREADY_SET);
            this.dataSourceSupplier = toSupplier(dataSource);
            return this;
        }

        public Builder<T> dataSource(Supplier<DataSource> dataSourceSupplier) {
            checkIsNull(this.dataSourceSupplier, DATASOURCE_ALREADY_SET);
            this.dataSourceSupplier = dataSourceSupplier;
            return this;
        }

        public Builder<T> executor(IScriptExecutor executor) {
            checkNotNull(executor, EXECUTOR_IS_NULL);
            checkIsNull(this.executor, EXECUTOR_ALREADY_SET);
            this.executor = executor;
            return this;
        }

        public Builder<T> converters(IJDBTypeConverter... converters) {
            this.converters = converters == null ? null : Arrays.asList(converters);
            return this;
        }

        /**
         * Configures the order in which tables should be cleaned up.
         *
         * @param tableNames list of table names in cleanup order
         * @return this builder
         */
        public Builder<T> cleanupOrder(List<String> tableNames) {
            this.cleanupOrder = tableNames;
            return this;
        }

        /**
         * Configures the caching strategy for database metadata.
         * Default is {@link CacheStrategy#INSTANCE}.
         *
         * @param cacheStrategy the caching strategy to use
         * @return this builder
         */
        public Builder<T> cacheStrategy(CacheStrategy cacheStrategy) {
            this.cacheStrategy = cacheStrategy;
            return this;
        }

        /**
         * Sets the strategy to use when a table is found in the database but is not mapped in the schema interface.
         * Default is {@link ValidationStrategy#LOG_WARN}.
         *
         * @param strategy the validation strategy
         * @return this builder
         */
        public Builder<T> unmappedTableStrategy(ValidationStrategy strategy) {
            this.unmappedTableStrategy = strategy;
            return this;
        }

        /**
         * Adds one or more table names to be suppressed from unmapped table validation.
         * This method is cumulative.
         *
         * @param tableNames the names of the tables to suppress
         * @return this builder
         */
        public Builder<T> suppressUnmappedTable(String... tableNames) {
            if (tableNames != null) {
                for (String tableName : tableNames) {
                    this.suppressedTables.add(tableName.toUpperCase());
                }
            }
            return this;
        }

        /**
         * Configures whether standard migration tables (Flyway and Liquibase) should be suppressed
         * from unmapped table validation.
         *
         * @param suppress true to suppress default migration tables, false otherwise
         * @return this builder
         */
        public Builder<T> suppressDefaultUnmappedTables(boolean suppress) {
            this.suppressDefaultUnmappedTables = suppress;
            return this;
        }

        public JDBEngine<T> build() {
            return new JDBEngine<>(this);
        }
    }
}