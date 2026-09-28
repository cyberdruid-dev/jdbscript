package org.jdbscript;

import org.jdbscript.impl.conversion.IJDBTypeConverter;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_SUPPLIER_IS_NULL;
import static org.jdbscript.errors.JDBErrors.FROM_SCHEMA_IS_NULL;
import static org.jdbscript.errors.JDBErrors.MIGRATOR_IS_NULL;
import static org.jdbscript.errors.JDBErrors.TO_SCHEMA_IS_NULL;

/**
 * Test harness for a migration that changes existing data, not just schema - e.g. a Liquibase or
 * Flyway changeset that backfills a new column from existing rows. Testing that needs the
 * database at two different shapes within one test: {@code F} (the shape right before the
 * migration under test, for seeding) and {@code A} (the shape right after, for asserting).
 * <p>
 * {@link #before()} and {@link #after()} share the same {@link DataSource} and settings, always
 * with {@code CacheStrategy.NONE} - unlike plain {@link JDBEngine}, this class always spans a
 * schema-changing migration, so caching metadata across that boundary isn't a rare case to guard
 * against, it's the normal case. That default isn't configurable here; if you need caching on
 * both sides despite the risk, build the two {@link JDBEngine}s yourself instead of using this
 * class.
 * <p>
 * Deliberately exposes {@link #migrateTo(String)} only, not a bare "migrate everything" method -
 * that would silently pull in whatever gets appended to the migration tool's changelog/history
 * later, defeating the point of a test scoped to one specific migration. Bound both ends by
 * marker (e.g. a tag placed right before and right after the migration under test).
 * <p>
 * {@link #reset()} wipes the datasource back to blank - an explicit opt-in for starting a test
 * from scratch, never invoked automatically by {@link #migrateTo(String)}.
 *
 * @param <F> the schema interface modeling the pre-migration shape
 * @param <A> the schema interface modeling the post-migration shape
 */
public class JDBMigrationEngine<F extends IDBSchema, A extends IDBSchema> {

    private final Class<F> fromSchemaClass;
    private final Class<A> toSchemaClass;
    private final Supplier<DataSource> dataSourceSupplier;
    private final MigrationRunner migrator;
    private final Supplier<IScriptExecutor> executorSupplier;
    private final List<IJDBTypeConverter> additionalConverters;
    private final boolean disableDefaultConverters;
    private final ValidationStrategy unmappedTableStrategy;
    private final Set<String> suppressedTables;
    private final Boolean suppressDefaultUnmappedTables;
    private final List<JDBFeature> features;
    private final Consumer<JDBEngine.Builder<F>> beforeConfigurer;
    private final Consumer<JDBEngine.Builder<A>> afterConfigurer;
    private DataSource dataSource;
    private IJDBEngine<F> before;
    private IJDBEngine<A> after;

    private JDBMigrationEngine(Builder<F, A> builder) {
        this.fromSchemaClass = checkNotNull(builder.fromSchemaClass, FROM_SCHEMA_IS_NULL);
        this.toSchemaClass = checkNotNull(builder.toSchemaClass, TO_SCHEMA_IS_NULL);
        this.dataSourceSupplier = checkNotNull(builder.dataSourceSupplier, DATASOURCE_SUPPLIER_IS_NULL);
        this.migrator = checkNotNull(builder.migrator, MIGRATOR_IS_NULL);
        this.executorSupplier = builder.executorSupplier;
        this.additionalConverters = List.copyOf(builder.additionalConverters);
        this.disableDefaultConverters = builder.disableDefaultConverters;
        this.unmappedTableStrategy = builder.unmappedTableStrategy;
        this.suppressedTables = Set.copyOf(builder.suppressedTables);
        this.suppressDefaultUnmappedTables = builder.suppressDefaultUnmappedTables;
        this.features = List.copyOf(builder.features);
        this.beforeConfigurer = builder.beforeConfigurer;
        this.afterConfigurer = builder.afterConfigurer;
    }

    /**
     * Returns the engine for the pre-migration shape, for seeding data before the migration under
     * test runs. Lazily built on first call, then reused for the lifetime of this instance.
     */
    public synchronized IJDBEngine<F> before() {
        if (before == null) {
            before = buildEngine(fromSchemaClass, beforeConfigurer);
        }
        return before;
    }

    /**
     * Returns the engine for the post-migration shape, for asserting data after the migration
     * under test has run. Lazily built on first call, then reused for the lifetime of this
     * instance.
     */
    public synchronized IJDBEngine<A> after() {
        if (after == null) {
            after = buildEngine(toSchemaClass, afterConfigurer);
        }
        return after;
    }

    private <T extends IDBSchema> IJDBEngine<T> buildEngine(Class<T> schemaClass, Consumer<JDBEngine.Builder<T>> configurer) {
        JDBEngine.Builder<T> b = JDBEngine.builder(schemaClass).dataSource(getDataSource());
        applySharedSettings(b);
        configurer.accept(b);
        return b.build();
    }

    private void applySharedSettings(JDBEngine.Builder<?> builder) {
        builder.cacheStrategy(CacheStrategy.NONE);
        if (executorSupplier != null) {
            // A fresh instance per side - IScriptExecutor (e.g. SqlScriptExecutor) is single-use,
            // tied to exactly one JDBEngine's dataSource/cache, so before() and after() can't
            // share one instance the way they share the plain settings below.
            builder.executor(executorSupplier.get());
        }
        for (IJDBTypeConverter converter : additionalConverters) {
            builder.converter(converter);
        }
        if (disableDefaultConverters) {
            builder.disableDefaultConverters();
        }
        if (unmappedTableStrategy != null) {
            builder.unmappedTableStrategy(unmappedTableStrategy);
        }
        if (!suppressedTables.isEmpty()) {
            builder.suppressUnmappedTable(suppressedTables.toArray(new String[0]));
        }
        if (suppressDefaultUnmappedTables != null) {
            builder.suppressDefaultUnmappedTables(suppressDefaultUnmappedTables);
        }
        for (JDBFeature feature : features) {
            builder.feature(feature);
        }
    }

    /**
     * Runs the configured {@link MigrationRunner} up to and including {@code marker}, then stops.
     *
     * @param marker the tool-specific stopping point (e.g. a Liquibase tag)
     */
    public void migrateTo(String marker) {
        migrator.migrateTo(getDataSource(), marker);
    }

    /**
     * Wipes everything the configured {@link MigrationRunner} manages on this engine's datasource
     * - an explicit opt-in, never invoked automatically by {@link #migrateTo(String)}.
     */
    public void reset() {
        migrator.reset(getDataSource());
    }

    private synchronized DataSource getDataSource() {
        if (dataSource == null) {
            dataSource = dataSourceSupplier.get();
        }
        return dataSource;
    }

    /**
     * Creates a new builder for {@code JDBMigrationEngine}.
     *
     * @param from the schema interface modeling the pre-migration shape
     * @param to the schema interface modeling the post-migration shape
     */
    public static <F extends IDBSchema, A extends IDBSchema> Builder<F, A> builder(Class<F> from, Class<A> to) {
        return new Builder<>(from, to);
    }

    /**
     * Builder for {@link JDBMigrationEngine}.
     *
     * @param <F> the schema interface modeling the pre-migration shape
     * @param <A> the schema interface modeling the post-migration shape
     */
    public static class Builder<F extends IDBSchema, A extends IDBSchema> {

        private final Class<F> fromSchemaClass;
        private final Class<A> toSchemaClass;
        private Supplier<DataSource> dataSourceSupplier;
        private MigrationRunner migrator;
        private Supplier<IScriptExecutor> executorSupplier;
        private final List<IJDBTypeConverter> additionalConverters = new ArrayList<>();
        private boolean disableDefaultConverters = false;
        private ValidationStrategy unmappedTableStrategy;
        private final Set<String> suppressedTables = new HashSet<>();
        private Boolean suppressDefaultUnmappedTables;
        private final List<JDBFeature> features = new ArrayList<>();
        private Consumer<JDBEngine.Builder<F>> beforeConfigurer = b -> {};
        private Consumer<JDBEngine.Builder<A>> afterConfigurer = b -> {};

        private Builder(Class<F> from, Class<A> to) {
            this.fromSchemaClass = from;
            this.toSchemaClass = to;
        }

        public Builder<F, A> dataSource(DataSource dataSource) {
            this.dataSourceSupplier = () -> dataSource;
            return this;
        }

        public Builder<F, A> dataSource(Supplier<DataSource> dataSourceSupplier) {
            this.dataSourceSupplier = dataSourceSupplier;
            return this;
        }

        /**
         * The migration tool adapter used by {@link JDBMigrationEngine#migrateTo(String)} (e.g. a
         * {@code LiquibaseMigrator}).
         */
        public Builder<F, A> migrator(MigrationRunner migrator) {
            this.migrator = migrator;
            return this;
        }

        /**
         * A fresh {@link IScriptExecutor} instance for each of {@link #before()}/{@link #after()}
         * - not the same shared instance, since {@code IScriptExecutor} (e.g.
         * {@code SqlScriptExecutor}) is single-use, tied to exactly one {@link JDBEngine}. See
         * {@link JDBEngine.Builder#executor}.
         */
        public Builder<F, A> executor(Supplier<IScriptExecutor> executorSupplier) {
            this.executorSupplier = executorSupplier;
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#converter}.
         */
        public Builder<F, A> converter(IJDBTypeConverter converter) {
            this.additionalConverters.add(converter);
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#disableDefaultConverters}.
         */
        public Builder<F, A> disableDefaultConverters() {
            this.disableDefaultConverters = true;
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#unmappedTableStrategy}.
         */
        public Builder<F, A> unmappedTableStrategy(ValidationStrategy strategy) {
            this.unmappedTableStrategy = strategy;
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#suppressUnmappedTable}.
         */
        public Builder<F, A> suppressUnmappedTable(String... tableNames) {
            if (tableNames != null) {
                for (String tableName : tableNames) {
                    this.suppressedTables.add(tableName.toUpperCase());
                }
            }
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#suppressDefaultUnmappedTables}.
         */
        public Builder<F, A> suppressDefaultUnmappedTables(boolean suppress) {
            this.suppressDefaultUnmappedTables = suppress;
            return this;
        }

        /**
         * Applied identically to both {@link #before()} and {@link #after()} - see
         * {@link JDBEngine.Builder#feature}.
         */
        public Builder<F, A> feature(JDBFeature feature) {
            this.features.add(feature);
            return this;
        }

        /**
         * Escape hatch: further configures the {@link #before()} engine's builder, applied after
         * every option above. Use this for settings that may legitimately differ between the
         * pre- and post-migration shape - e.g. {@code tableDependencyOrder(...)}, if the
         * migration under test adds or removes a whole table, not just a column.
         */
        public Builder<F, A> beforeEngine(Consumer<JDBEngine.Builder<F>> configurer) {
            this.beforeConfigurer = configurer;
            return this;
        }

        /**
         * Escape hatch: further configures the {@link #after()} engine's builder, applied after
         * every option above. See {@link #beforeEngine}.
         */
        public Builder<F, A> afterEngine(Consumer<JDBEngine.Builder<A>> configurer) {
            this.afterConfigurer = configurer;
            return this;
        }

        public JDBMigrationEngine<F, A> build() {
            return new JDBMigrationEngine<>(this);
        }
    }
}
