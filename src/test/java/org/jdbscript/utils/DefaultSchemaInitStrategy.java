package org.jdbscript.utils;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Runs the shared Liquibase changelog. Used by every DBMS that doesn't need its own
 * {@link ITestSchemaInitStrategy}.
 */
class DefaultSchemaInitStrategy implements ITestSchemaInitStrategy {
    private static final Logger log = LoggerFactory.getLogger(DefaultSchemaInitStrategy.class);

    @Override
    public void initSchema(DataSource dataSource) {
        log.debug("runLiquibase()");
        try (Connection connection = dataSource.getConnection()) {
            Database database = findDatabase(connection);
            log.debug("Liquibase.database = {} ", database.getDatabaseProductName());
            Liquibase liquibase = new Liquibase("db/changelog.yaml",
                    new ClassLoaderResourceAccessor(),
                    database);
            liquibase.update();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Database findDatabase(Connection connection) throws DatabaseException {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    }
}
