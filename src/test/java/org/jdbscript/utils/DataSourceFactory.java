package org.jdbscript.utils;

import org.jdbscript.DBMSType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
        TestSchemaInitStrategyFactory.getStrategy(getDbmsType()).initSchema(newDataSource);
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

}
