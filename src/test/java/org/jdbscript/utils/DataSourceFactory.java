package org.jdbscript.utils;

import org.jdbscript.DBMSType;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class DataSourceFactory {
    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private DBMSType dbmsType;

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

    public DBMSType getDbmsType() {
        if(this.dbmsType == null) {
            DBMSType type = DBMSType.getTypeFromUrl(jdbcUrl);
            if (type == DBMSType.POSTGRESQL) {
                // CockroachDB often uses PostgreSQL JDBC URL. Try to refine detection if possible.
                try (Connection connection = java.sql.DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
                    this.dbmsType = DBMSType.getType(connection.getMetaData());
                } catch (SQLException e) {
                    log.warn("Failed to refine DBMS type detection via connection, falling back to URL-based detection: {}", e.getMessage());
                    this.dbmsType = type;
                }
            } else {
                this.dbmsType = type;
            }

            if(this.dbmsType == DBMSType.UNKNOWN) {
                throw new UnsupportedOperationException("Unknown dbms type for JDBC URL: " + jdbcUrl);
            }
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
        if (getDbmsType() == DBMSType.DUCKDB) {
            log.info("Skipping Liquibase for DuckDB as it is not fully supported yet. Running manual initialization.");
            runDuckdbInit(newDataSource);
            return;
        }
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

    private void runDuckdbInit(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("db/duckdb-schema.sql");
            if (is == null) {
                throw new IOException("Could not find db/duckdb-schema.sql");
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            int count = 0;
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                    count++;
                }
            }
            log.info("Initialized DuckDB schema with {} statements.", count);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }

    private Database findDatabase(Connection connection) throws DatabaseException {
        return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
    }
}
