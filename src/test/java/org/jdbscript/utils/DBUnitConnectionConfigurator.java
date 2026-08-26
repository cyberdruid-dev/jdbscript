package org.jdbscript.utils;

import org.jdbscript.impl.dbunit.DbunitScriptExecutor;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseDataSourceConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory;
import org.dbunit.ext.mssql.MsSqlDataTypeFactory;
import org.dbunit.ext.mysql.MySqlDataTypeFactory;
import org.dbunit.ext.oracle.Oracle10DataTypeFactory;
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory;

import javax.sql.DataSource;

public class DBUnitConnectionConfigurator implements DbunitScriptExecutor.IDBUnitConfigurator, DbunitScriptExecutor.IDBUnitConnectionCreator {


    private String schemaName;

    public void setSchemaName(String value) {
        this.schemaName = value == null || value.isBlank()? null : value;
    }

    @Override
    public void configure(IDatabaseConnection connection) throws Exception {
        DatabaseConfig config = connection.getConfig();
        config.setProperty(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES, false);
        config.setProperty(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, false);
        config.setProperty(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, true);
        String driverName = connection.getConnection().getMetaData().getDriverName().toLowerCase();
        if (driverName.contains("oracle")) {
            configureForOracle(config);
        } else if (driverName.contains("mysql")) {
            configureForMySQL(config);
        } else if (driverName.contains("sqlserver")) {
            configureForMSSql(config);
        } else if (driverName.contains("postgres")) {
            configureForPostgres(config);
        } else if (driverName.contains("h2")) {
            configureForH2(config);
        } else if (driverName.contains("hsql")) {
            configureForHsqldb(config);
        }
    }

    private void configureForHsqldb(DatabaseConfig config) {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new HsqldbDataTypeFactory());
    }

    private void configureForMSSql(DatabaseConfig config) {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new MsSqlDataTypeFactory());
    }

    private void configureForPostgres(DatabaseConfig config) {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new PostgresqlDataTypeFactory());
    }

    private void configureForH2(DatabaseConfig config) {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new H2DataTypeFactory());
    }

    private void configureForMySQL(DatabaseConfig config) {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new MySqlDataTypeFactory());
    }

    private void configureForOracle(DatabaseConfig config) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        config.setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new Oracle10DataTypeFactory());
        config.setProperty(DatabaseConfig.FEATURE_QUALIFIED_TABLE_NAMES, true);
    }

    @Override
    public IDatabaseConnection create(DataSource source) throws Exception {
        DatabaseDataSourceConnection connection;
        if(schemaName != null) {
            connection = new DatabaseDataSourceConnection(source, schemaName);
        } else {
            connection = new DatabaseDataSourceConnection(source);
        }
        return connection;
    };
}
