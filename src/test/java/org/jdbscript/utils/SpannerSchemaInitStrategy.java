package org.jdbscript.utils;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Cloud Spanner's emulator doesn't reliably roll back DDL between test runs, so leftover tables
 * (including Liquibase's own tracking tables) are force-dropped before the shared changelog runs.
 */
class SpannerSchemaInitStrategy implements ITestSchemaInitStrategy {
    private static final Logger log = LoggerFactory.getLogger(SpannerSchemaInitStrategy.class);

    @Override
    public void initSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            dropAllTables(connection);
            runLiquibase(connection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runLiquibase(Connection connection) throws Exception {
        log.debug("runLiquibase()");
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase("db/changelog.yaml", new ClassLoaderResourceAccessor(), database);
        liquibase.clearCheckSums();
        liquibase.update();
    }

    private void dropAllTables(Connection connection) throws SQLException {
        log.debug("dropAllTables()");
        connection.setAutoCommit(true);
        for (int i = 0; i < 5; i++) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            if (tables.isEmpty() || (tables.size() <= 2 && tables.stream().allMatch(t -> t.startsWith("DATABASECHANGELOG")))) {
                break;
            }
            try (Statement stmt = connection.createStatement()) {
                for (String table : tables) {
                    if (table.startsWith("DATABASECHANGELOG")) continue;
                    try {
                        stmt.execute("DROP TABLE " + table);
                        log.debug("Dropped table: {}", table);
                    } catch (Exception e) {
                        // Ignore and try in next pass
                    }
                }
            }
        }
        try (Statement stmt = connection.createStatement()) {
            try { stmt.execute("DROP TABLE DATABASECHANGELOGLOCK"); } catch (Exception e) {}
            try { stmt.execute("DROP TABLE DATABASECHANGELOG"); } catch (Exception e) {}
        }
    }
}
