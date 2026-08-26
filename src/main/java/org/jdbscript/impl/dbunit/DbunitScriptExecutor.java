package org.jdbscript.impl.dbunit;

import org.jdbscript.DbmsType;
import org.jdbscript.IScriptExecutor;
import org.jdbscript.errors.JdbsErrors;
import org.jdbscript.impl.JDbScript;
import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseDataSourceConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.ext.mssql.InsertIdentityOperation;
import org.dbunit.operation.DatabaseOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.jdbscript.DbmsType.UNKNOWN;
import static org.jdbscript.errors.Checks.checkIsNull;
import static org.jdbscript.errors.Checks.checkNotNull;
import static org.dbunit.operation.DatabaseOperation.DELETE_ALL;
import static org.dbunit.operation.DatabaseOperation.INSERT;

public class DbunitScriptExecutor implements IScriptExecutor {
    private static final Logger log = LoggerFactory.getLogger(DbunitScriptExecutor.class);

    private DataSource dataSource;
    private IDatabaseConnection connection;
    private IDBUnitConfigurator configurator;
    private IDBUnitConnectionCreator connectionCreator;
    private String schemaName = null;
    private DbmsType dbmsType = UNKNOWN;

    @FunctionalInterface
    public interface IDBUnitConfigurator {

        void configure(IDatabaseConnection connection) throws Exception;

    }

    @FunctionalInterface
    public interface IDBUnitConnectionCreator {

        IDatabaseConnection create(DataSource source) throws Exception;

    }

    public DbunitScriptExecutor() {
        configure(null);
        setConnectionCreator(null);
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    @Override
    public void setDbmsType(DbmsType dbmsType) {
        this.dbmsType = dbmsType;
    }

    @Override
    public void setDataSource(DataSource value) {
        checkNotNull(value, JdbsErrors.EXECUTOR_IS_NULL);
        checkIsNull(this.dataSource, JdbsErrors.DATASOURCE_ALREADY_SET);
        this.dataSource = value;
    }

    public void configure(IDBUnitConfigurator configurator) {
        this.configurator = configurator;
        if(this.configurator == null) {
            this.configurator = (x)->{};
        }
    }

    public void setConnectionCreator(IDBUnitConnectionCreator creator) {
        this.connectionCreator = creator;
        if(this.connectionCreator == null) {
            this.connectionCreator = DatabaseDataSourceConnection::new;
        }
    }

    @Override
    public void insert(JDbScript dbScript) {
        log.debug("execute() script:\n{}", dbScript);
        try {
            ScriptXmlWriter writer = new ScriptXmlWriter(dbScript, schemaName, dbmsType);
            String xml = writer.getScript();
            Map<String,Object> replacements = writer.getReplacements();
            log.debug("execute() script dbUnit XML:\n{}", xml);
            DatabaseOperation operation = dbmsType == DbmsType.MSSQL ? InsertIdentityOperation.INSERT : INSERT;
            execute(operation, xml, replacements);
        } catch(Exception e) {
            throw new RuntimeException("Fail to execute script "+dbScript, e);
        }
    }

    @Override
    public void cleanupTables(List<String> tableNames) {
        log.debug("clearAllDB()");
        for(String table: tableNames) {
            cleanupTable(schemaName, table);
        }
        //TODO: resetOracleSequences();
    }

    private void cleanupTable(String dbName, String table) {
        try {
            table = dbName == null? table :dbName+"."+table;
            String xml = "<dataset><"+table+"/></dataset>";
            execute(DELETE_ALL, xml, null);
        } catch(Exception e) {
            String msg = "Fail to cleanup tables:"+ table;
            log.error(msg, e);
            new RuntimeException(msg, e);
        }
    }

    private void execute(DatabaseOperation operation, String xml, Map<String, Object> replacements) throws DatabaseUnitException, SQLException {
        log.trace("execute({}) xml:\n{}", operation, xml);
        IDatabaseConnection cnn = null;
        try {
            cnn = getConnection();
            operation.execute(cnn, createDataset(xml, replacements));
        } finally {
            if(cnn != null) {
                cnn.close();
            }

        }
    }

    private IDataSet createDataset(String xml, Map<String, Object> replacements) throws SQLException, DataSetException {
        replacements = replacements == null ? Map.of(): replacements;
        IDataSet metadata = getConnection().createDataSet();
        FlatXmlDataSetBuilder builder = new FlatXmlDataSetBuilder();
        //builder.setColumnSensing(true);
        builder.setMetaDataSet(metadata);
        FlatXmlDataSet dataSet = builder.build(new StringReader(xml));
        ReplacementDataSet replacement = new ReplacementDataSet(dataSet);
        replacement.setStrictReplacement(true);
        for(Entry<String, Object> entry: replacements.entrySet()) {
            replacement.addReplacementObject(entry.getKey(), entry.getValue());
        }
        return replacement;
    }

    private IDatabaseConnection getConnection() {
        if(connection == null) {
            checkNotNull(dataSource, JdbsErrors.DATASOURCE_IS_NOT_CONFIGURED);
            try {
                IDatabaseConnection result = connectionCreator.create(dataSource);
                configurator.configure(result);
                connection = result;
            } catch (Exception ex) {
                throw new RuntimeException("Can not create and configure db connection.", ex);
            }
        }
        return connection;
    }

}
