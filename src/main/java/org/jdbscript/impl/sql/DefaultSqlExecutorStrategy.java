package org.jdbscript.impl.sql;

import org.jdbscript.impl.JDbScript;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

class DefaultSqlExecutorStrategy implements ISqlExecutorStrategy {

    @Override
    public void afterInsert(Connection cnn) throws SQLException {

    }

    @Override
    public void beforeInsert(Connection cnn, JDbScript dbScript) throws SQLException {
    }

    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException{
        stmt.setBlob(columnIndex, value);
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        InputStream in = bytes == null?  null : new ByteArrayInputStream(bytes);
        setInputStream(stmt, columnIndex, in);
    }

    @Override
    public void setUUID(PreparedStatement stmt, int columnIndex, UUID uuid) throws SQLException {
        String value = uuid == null? null : uuid.toString();
        stmt.setString(columnIndex, value);
    }
}
