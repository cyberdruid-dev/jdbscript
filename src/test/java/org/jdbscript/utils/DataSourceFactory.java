package org.jdbscript.utils;

import org.jdbscript.DbmsType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.sql.SQLException;

class DataSourceFactory {
    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private DbmsType dbmsType;

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public void setJdbcUser(String jdbcUser) {
        this.jdbcUser = jdbcUser;
    }

    public void setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    public DataSource createDataSource() {
        HikariDataSource newDataSource = newHikariPool();
        logSchema(newDataSource);
        runLiquibase(newDataSource);
        return newDataSource;
    }

    public DbmsType getDbmsType() {
        if(this.dbmsType == null) {
            DbmsType type = DbmsType.getTypeFromUrl(jdbcUrl);
            if(type == DbmsType.UNKNOWN) {
                throw new UnsupportedOperationException("Unknown dbms type for JDBC URL: " + jdbcUrl);
            }
            this.dbmsType = type;
        }
        return this.dbmsType;
    }


    private HikariDataSource newHikariPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(jdbcUser);
        config.setPassword(jdbcPassword);
        return new HikariDataSource(config);
    }

    private void logSchema(DataSource newDataSource) {
        try(var cnn = newDataSource.getConnection()) {
            String schema = cnn.getSchema();
            log.debug("schema: {}", schema);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void runLiquibase(DataSource newDataSource) {
        log.debug("runLiquibase()");
        try(Connection connection = newDataSource.getConnection()) {
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
