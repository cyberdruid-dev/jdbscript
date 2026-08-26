package org.jdbscript;

import org.jdbscript.impl.JDbScript;

import javax.sql.DataSource;
import java.util.List;

public interface IScriptExecutor {
    void setDataSource(DataSource dataSource);

    void setDbmsType(DbmsType dbmsType);

    void insert(JDbScript dbScript);

    void cleanupTables(List<String> tableNames);
}
