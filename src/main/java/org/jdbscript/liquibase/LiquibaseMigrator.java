package org.jdbscript.liquibase;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jdbscript.DBMSType;
import org.jdbscript.MigrationRunner;
import org.jdbscript.errors.JDBScriptException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MigrationRunner} backed by Liquibase. {@code marker} in {@link #migrateTo} is a
 * Liquibase tag - place a {@code tagDatabase} changeset at each point in your changelog you want
 * to stop at for a test.
 */
public class LiquibaseMigrator implements MigrationRunner {

    private final String changeLogFile;

    /**
     * @param changeLogFile classpath-relative path to the Liquibase changelog (e.g.
     *                       {@code "db/changelog.yaml"})
     */
    public LiquibaseMigrator(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    @Override
    public void migrateTo(DataSource dataSource, String marker) {
        run(dataSource, liquibase -> {
            liquibase.update(marker, new Contexts());
            // Liquibase silently applies zero changesets for an unknown tag rather than erroring -
            // a typo would otherwise leave the DB silently at the wrong state.
            if (!liquibase.tagExists(marker)) {
                throw new JDBScriptException(
                        "Liquibase tag '" + marker + "' not found in changelog '" + changeLogFile + "'.");
            }
        });
    }

    @Override
    public void migrate(DataSource dataSource) {
        run(dataSource, liquibase -> liquibase.update(new Contexts()));
    }

    @Override
    public void reset(DataSource dataSource) {
        // Liquibase's dropAll() can't generate a DROP FOREIGN KEY statement for SQLite - dropping
        // an FK there requires recreating the whole table, which Liquibase has never implemented
        // (see https://github.com/liquibase/liquibase/issues/2267, closed without a fix). Drop
        // tables directly instead; this also naturally clears DATABASECHANGELOG, same as
        // dropAll() does everywhere else.
        DBMSType dbmsType = detectDbmsType(dataSource);
        if (dbmsType == DBMSType.SQLITE) {
            resetSqlite(dataSource);
        } else if (dbmsType == DBMSType.SPANNER) {
            resetSpanner(dataSource);
        } else {
            run(dataSource, Liquibase::dropAll);
        }
    }

    private DBMSType detectDbmsType(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return DBMSType.getType(connection.getMetaData());
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to detect database type (changelog '" + changeLogFile + "').", e);
        }
    }

    private void resetSqlite(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // Safe regardless of the caller's own PRAGMA setting - dropping a referenced table
            // would otherwise fail the same way dropAll() does.
            statement.execute("PRAGMA foreign_keys = OFF");
            List<String> tables = new ArrayList<>();
            try (var rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            for (String table : tables) {
                statement.execute("DROP TABLE \"" + table + "\"");
            }
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to reset SQLite database (changelog '" + changeLogFile + "').", e);
        }
    }

    private void resetSpanner(DataSource dataSource) {
        // dropAll() opens a transaction to acquire its changelog lock, then fails issuing DDL
        // inside it - Spanner forbids DDL in an open transaction. Drop tables directly in
        // autocommit mode instead, retrying since Spanner has no SQLite-style pragma to disable FK
        // enforcement.
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            for (int i = 0; i < 5; i++) {
                List<String> tables = new ArrayList<>();
                try (var rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
                if (tables.isEmpty() || (tables.size() <= 2 && tables.stream().allMatch(t -> t.startsWith("DATABASECHANGELOG")))) {
                    break;
                }
                try (Statement statement = connection.createStatement()) {
                    for (String table : tables) {
                        if (table.startsWith("DATABASECHANGELOG")) continue;
                        try {
                            statement.execute("DROP TABLE " + table);
                        } catch (Exception e) {
                            // FK-dependent table not yet droppable this pass - retry next iteration.
                        }
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                try { statement.execute("DROP TABLE DATABASECHANGELOGLOCK"); } catch (Exception e) {}
                try { statement.execute("DROP TABLE DATABASECHANGELOG"); } catch (Exception e) {}
            }
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to reset Spanner database (changelog '" + changeLogFile + "').", e);
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }

    private void run(DataSource dataSource, LiquibaseAction action) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            // Not just for the JDBC connection (the outer try already closes that) - Liquibase
            // keeps this Database in an internal per-instance executor cache that's only cleared
            // by close(), so skipping it leaks an entry there on every call.
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader loader = contextClassLoader != null ? contextClassLoader : LiquibaseMigrator.class.getClassLoader();
            try (Liquibase liquibase = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(loader), database)) {
                action.run(liquibase);
            }
        } catch (JDBScriptException e) {
            throw e;
        } catch (Exception e) {
            throw new JDBScriptException("Liquibase migration failed (changelog '" + changeLogFile + "').", e);
        }
    }
}
