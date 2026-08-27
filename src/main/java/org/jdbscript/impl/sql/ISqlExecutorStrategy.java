package org.jdbscript.impl.sql;

import org.jdbscript.impl.JDbScript;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public interface ISqlExecutorStrategy {
    void afterInsert(Connection cnn) throws SQLException;

    void beforeInsert(Connection cnn, JDbScript dbScript) throws SQLException;

    void setInputStream(PreparedStatement stmt, int i, InputStream value) throws SQLException;

    void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException;

    void setByteArray(PreparedStatement stmt, int i, byte[] value) throws SQLException;

    void setObject(PreparedStatement stmt, int i, Object value) throws SQLException;
}
