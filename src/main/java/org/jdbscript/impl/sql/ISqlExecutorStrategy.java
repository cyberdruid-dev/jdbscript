package org.jdbscript.impl.sql;

import org.jdbscript.impl.JDBScript;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public interface ISqlExecutorStrategy {
    void afterInsert(Connection cnn) throws SQLException;

    void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException;

    void setInputStream(PreparedStatement stmt, int i, InputStream value) throws SQLException;

    void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException;

    void setByteArray(PreparedStatement stmt, int i, byte[] value) throws SQLException;

    void setObject(PreparedStatement stmt, int i, Object value) throws SQLException;

    void onConnection(Connection cnn) throws SQLException;

    void commit(Connection cnn) throws SQLException;

    String getSearchCatalog(Connection cnn) throws SQLException;

    String getSearchSchema(Connection cnn) throws SQLException;

    String[] getTableTypes();

    Set<String> getRawTableDependencies(Connection cnn, String catalog, String schema, String tableName) throws SQLException;

    Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException;
}
