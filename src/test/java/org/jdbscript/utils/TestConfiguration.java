package org.jdbscript.utils;

import org.jdbscript.DbmsType;
import org.jdbscript.IScriptExecutor;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.TimeZone;

public class TestConfiguration {
    public final static TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final Logger log = LoggerFactory.getLogger(TestConfiguration.class);
    private final static String PROPERTY_JDBC_URL = "test.jdbc.url";
    private final static String PROPERTY_JDBC_USER = "test.jdbc.user";
    private final static String PROPERTY_JDBC_PASSWORD = "test.jdbc.password";
    private final static String PROPERTY_JDBC_SCHEMA_NAME = "test.jdbc.schema.name";
    private final static String PROPERTY_SCRIPT_EXECUTOR = "test.script.executor";
    private final static String PROPERTY_DBMS_TYPE = "test.dbms.type";
    private final static Map<String,Class<? extends IScriptExecutor>> executors = Map.of(
            "sql", SqlScriptExecutor.class
    );
    private final DataSourceFactory dsFactory = new DataSourceFactory();
    private DataSource dataSource;
    private String executorType;
    private String jdbcSchemaName;
    private DbmsType expectedDbmsType;

    public TestConfiguration() {
        TimeZone.setDefault(UTC);// to not play with timezones .....
        readConnectionProperties();
    }

    public String getJdbcSchemaName() {
        return jdbcSchemaName;
    }

    public DataSource getDataSource() {
        if(dataSource == null) {
            dataSource = dsFactory.createDataSource();
        }
        return dataSource;
    }


    public DbmsType getDbmsType() {
        return dsFactory.getDbmsType();
    }

    public DbmsType getExpectedDbmsType() {
        return expectedDbmsType;
    }

    public IScriptExecutor getScriptExecutor() {
        String type = (executorType == null || executorType.isBlank()) ? "sql" : executorType;
        Class<? extends IScriptExecutor> scriptExecutor = executors.get(type);
        if(scriptExecutor == null) {
            String msg = "Unknown script executor: '%s'. Expecting one of '%s' in system property '%s'";
            msg = msg.formatted(executorType, executors.keySet(), PROPERTY_SCRIPT_EXECUTOR);
            log.error(msg);
            throw new JDBScriptException(msg);
        }
        return constructExecutor(scriptExecutor);
    }

    private IScriptExecutor constructExecutor(Class<? extends IScriptExecutor> executorClass) {
        try {
            return executorClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void readConnectionProperties() {
        String jdbcUrl = System.getProperty(PROPERTY_JDBC_URL);
        String jdbcUser = System.getProperty(PROPERTY_JDBC_USER);
        String jdbcPassword = System.getProperty(PROPERTY_JDBC_PASSWORD);
        this.jdbcSchemaName = System.getProperty(PROPERTY_JDBC_SCHEMA_NAME);
        this.executorType = System.getProperty(PROPERTY_SCRIPT_EXECUTOR);
        String dbmsTypeStr = System.getProperty(PROPERTY_DBMS_TYPE);
        if (dbmsTypeStr != null && !dbmsTypeStr.isBlank()) {
            this.expectedDbmsType = DbmsType.valueOf(dbmsTypeStr.toUpperCase());
        }
        log.debug("jdbcUrl={}", jdbcUrl);
        log.debug("jdbcUser={}", jdbcUser);
        log.debug("jdbcPassword={}", jdbcPassword);
        log.debug("jdbcSchemaName={}", jdbcSchemaName);
        log.debug("executorType={}", executorType);
        dsFactory.setJdbcPassword(jdbcPassword);
        dsFactory.setJdbcUser(jdbcUser);
        dsFactory.setJdbcUrl(jdbcUrl);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new NullPointerException(PROPERTY_JDBC_URL + " is null or blank");
        }
        if (jdbcUser == null || jdbcUser.isBlank()) {
            throw new NullPointerException(PROPERTY_JDBC_USER + " is null or blank");
        }
    }

}