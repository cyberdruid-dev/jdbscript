package org.jdbscript;

import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.errors.JdbsErrors;
import org.jdbscript.impl.JDbScript;
import org.jdbscript.impl.ScriptHandler;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.jdbscript.impl.conversion.JDBTypeConverter;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.*;

/**
 * Standard implementation of {@link IJDBEngine} providing fluent database seeding,
 * script execution, and table cleanup for a specified schema interface.
 *
 * @param <T> the schema interface type extending {@link IDbSchema}
 */
public class JDBEngine<T extends IDbSchema> implements IJDBEngine<T>{
    private static final Logger log = LoggerFactory.getLogger(JDBEngine.class);

    private final JDBTypeConverter converter = new JDBTypeConverter();
    private final Class<T> dbSchemaClass;
    private final Supplier<DataSource> dataSourceSupplier;
    private final List<String> cleanupOrder;
    private DataSource dataSource;
    private DbmsType dbmsType;
    private IScriptExecutor executor;

    private JDBEngine(Builder<T> builder) {
        this.dbSchemaClass = checkNotNull(builder.dbSchemaClass, DB_SCHEMA_IS_NULL);
        this.dataSourceSupplier = checkNotNull(builder.dataSourceSupplier, DATASOURCE_SUPPLIER_IS_NULL);
        this.cleanupOrder = builder.cleanupOrder != null ? List.copyOf(builder.cleanupOrder) : null;
        if (builder.executor != null) {
            setExecutor(builder.executor);
        }
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
    public static <T extends IDbSchema> Builder<T> builder(Class<T> dbSchemaClass) {
        return new Builder<>(dbSchemaClass);
    }

    /**
     * Constructs a {@code JDBEngine} with a lazy {@link DataSource} supplier and schema interface.
     *
     * @param dataSourceSupplier supplier returning the target JDBC {@link DataSource}
     * @param dbSchemaClass      the schema interface class modeling the database tables
     * @deprecated use {@link #builder(Class)} instead
     */
    @Deprecated
    public JDBEngine(Supplier<DataSource> dataSourceSupplier, Class<T> dbSchemaClass) {
        this.dbSchemaClass = checkNotNull(dbSchemaClass, JdbsErrors.DB_SCHEMA_IS_NULL);
        this.dataSourceSupplier = checkNotNull(dataSourceSupplier, JdbsErrors.DATASOURCE_SUPPLIER_IS_NULL);
        this.cleanupOrder = null;
    }

    /**
     * Constructs a {@code JDBEngine} with a direct {@link DataSource} and schema interface.
     *
     * @param dataSource    the target JDBC {@link DataSource}
     * @param dbSchemaClass the schema interface class modeling the database tables
     * @deprecated use {@link #builder(Class)} instead
     */
    @Deprecated
    public JDBEngine(DataSource dataSource, Class<T> dbSchemaClass) {
        this(toSupplier(dataSource), dbSchemaClass);
    }

    private static Supplier<DataSource> toSupplier(DataSource dataSource) {
        checkNotNull(dataSource, JdbsErrors.DATASOURCE_IS_NULL);
        return ()->dataSource;
    }

    /**
     * Configures custom type converters used to transform record values before database insertion.
     *
     * @param converters collection of {@link IJDBTypeConverter} instances
     */
    public void setConverters(Collection<IJDBTypeConverter> converters) {
        converter.setConverters(converters);
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
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        db.accept(handler.getProxy());
        handler.applyDefaults();
        JDbScript script = handler.getDbScript();
        converter.convertTypes(script);
        getExecutor().insert(script);
    }

    /**
     * Configures a custom script executor for performing database operations.
     *
     * @param value the custom {@link IScriptExecutor} instance
     */
    public void setExecutor(IScriptExecutor value) {
        checkNotNull(value, EXECUTOR_IS_NULL);
        checkIsNull(this.executor, EXECUTOR_ALREADY_SET);
        this.executor = value;
        this.executor.setDataSource(getDataSource());
        this.executor.setDbmsType(getDbmsType());
    }

    private synchronized DataSource getDataSource() {
        if(dataSource == null){
            dataSource = checkNotNull(dataSourceSupplier.get(), DATASOURCE_IS_NULL) ;
        }
        return dataSource;
    }

    private synchronized DbmsType getDbmsType() {
        if(dbmsType == null){
            try (var cnn = getDataSource().getConnection()) {
                this.dbmsType = DbmsType.getTypeFromUrl(cnn.getMetaData().getURL());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return dbmsType;
    }

    @Override
    public void cleanupDB() {
        getTableNames();
        getExecutor().cleanupTables(getTableNames());
    }

    @Override
    public void assertDBHasNot(Consumer<T> dbAsserts) {
        log.debug("assertDBHasNot(consumer={})", dbAsserts);
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        dbAsserts.accept(handler.getProxy());
        JDbScript script = handler.getDbScript();
        converter.convertTypes(script);
        getExecutor().assertRowsNotExist(script);
    }

    @Override
    public void assertDBHas(Consumer<T> dbAsserts) {
        log.debug("assertDBHas(consumer={})", dbAsserts);
        ScriptHandler<T> handler = new ScriptHandler(dbSchemaClass);
        dbAsserts.accept(handler.getProxy());
        JDbScript script = handler.getDbScript();
        converter.convertTypes(script);
        getExecutor().assertRowsExist(script);
    }

    private IScriptExecutor getExecutor() {
        if(executor == null) {
            setExecutor(new SqlScriptExecutor());
        }
        return executor;
    }

    private List<String> getTableNames() {
        if (cleanupOrder != null) {
            return cleanupOrder;
        }
        List<String> tables = findRecordMethods(dbSchemaClass).stream()
                .map(m->m.getName())
                .toList();
        return getExecutor().sortTablesByDependencies(tables);
    }

    private List<Method> findRecordMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for(var m: clazz.getMethods()){
            Class<?> returnType = m.getReturnType();
            if(IDBRecord.class.isAssignableFrom(returnType)) {
                methods.add(m);
            }
        }
        for(var iface: clazz.getInterfaces()){
            methods.addAll(findRecordMethods(iface));
        }
        return methods;
    }

    /**
     * Builder for {@link JDBEngine}.
     *
     * @param <T> the schema interface type
     */
    public static class Builder<T extends IDbSchema> {
        private final Class<T> dbSchemaClass;
        private Supplier<DataSource> dataSourceSupplier;
        private IScriptExecutor executor;
        private Collection<IJDBTypeConverter> converters;
        private List<String> cleanupOrder;

        private Builder(Class<T> dbSchemaClass) {
            this.dbSchemaClass = dbSchemaClass;
        }

        public Builder<T> dataSource(DataSource dataSource) {
            this.dataSourceSupplier = toSupplier(dataSource);
            return this;
        }

        public Builder<T> dataSource(Supplier<DataSource> dataSourceSupplier) {
            this.dataSourceSupplier = dataSourceSupplier;
            return this;
        }

        public Builder<T> executor(IScriptExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Builder<T> converters(Collection<IJDBTypeConverter> converters) {
            this.converters = converters;
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

        public JDBEngine<T> build() {
            return new JDBEngine<>(this);
        }
    }
}