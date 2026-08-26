package org.jdbscript.utils;

import org.jdbscript.DbmsType;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import oracle.jdbc.pool.OracleDataSource;
import org.apache.commons.lang3.NotImplementedException;
import org.h2.jdbcx.JdbcDataSource;
import org.mariadb.jdbc.MariaDbDataSource;
import org.postgresql.ds.PGSimpleDataSource;
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

    public DataSource createDataSource(boolean dbmsSpecificDS) {
        DataSource newDataSource;
        if(dbmsSpecificDS) {
            newDataSource = switch (getDbmsType()){
                case MYSQL ->  newMysqlDS();
                case POSTGRESQL ->   newPostgresDS();
                case MARIADB ->   newMariadbDS();
                case MSSQL ->   newMSSqlDS();
                case ORACLE ->   newOracleDS();
                case H2 ->   newH2DS();
                default -> newHikariPool();
            };
        } else {
            newDataSource = newHikariPool();
        }
        logSchema(newDataSource);
        runLiquibase(newDataSource);
        return newDataSource;
    }

    public DbmsType getDbmsType() {
        if(this.dbmsType == null) {
            DbmsType type = DbmsType.getTypeFromUrl(jdbcUrl);
            if(type == DbmsType.UNKNOWN) {
                throw new NotImplementedException("Unknown dbms type for JDBC URL: " + jdbcUrl);
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

    private DataSource newMSSqlDS() {
        SQLServerDataSource jdbcDS = new SQLServerDataSource();
        jdbcDS.setURL(jdbcUrl);
        jdbcDS.setUser(jdbcUser);
        jdbcDS.setPassword(jdbcPassword);
        return jdbcDS;
    }

    private DataSource newMariadbDS() {
        try {
            MariaDbDataSource jdbcDS = new MariaDbDataSource();
            jdbcDS.setUrl(jdbcUrl);
            jdbcDS.setUser(jdbcUser);
            jdbcDS.setPassword(jdbcPassword);
            return jdbcDS;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void logSchema(DataSource newDataSource) {
        try(var cnn = newDataSource.getConnection()) {
            String schema = cnn.getSchema();
            log.debug("schema: {}", schema);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private DataSource newOracleDS() {
        try {
            OracleDataSource dataSource = new OracleDataSource();
            dataSource.setURL(jdbcUrl);
            dataSource.setUser(jdbcUser);
            dataSource.setPassword(jdbcPassword);
            return dataSource;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private DataSource newPostgresDS() {
        PGSimpleDataSource jdbcDS = new PGSimpleDataSource();
        jdbcDS.setURL(jdbcUrl);
        jdbcDS.setUser(jdbcUser);
        jdbcDS.setPassword(jdbcPassword);
        return jdbcDS;
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

    private DataSource newH2DS() {
        JdbcDataSource jdbcDS = new JdbcDataSource();
        jdbcDS.setURL(jdbcUrl);
        jdbcDS.setUser(jdbcUser);
        jdbcDS.setPassword(jdbcPassword);
        return jdbcDS;
    }

    private DataSource newMysqlDS() {
        MysqlDataSource jdbcDS = new MysqlDataSource();
        jdbcDS.setURL(jdbcUrl);
        jdbcDS.setUser(jdbcUser);
        jdbcDS.setPassword(jdbcPassword);
        return jdbcDS;

    }
}
