package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

class DuckdbStrategy extends DefaultSqlExecutorStrategy {
    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException {
        if (value == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            stmt.setBinaryStream(columnIndex, value);
        }
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        if (bytes == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            stmt.setBytes(columnIndex, bytes);
        }
    }

    @Override
    public void setUUID(PreparedStatement stmt, int columnIndex, UUID uuid) throws SQLException {
        if (uuid == null) {
            stmt.setNull(columnIndex, Types.OTHER);
        } else {
            stmt.setObject(columnIndex, uuid.toString());
        }
    }
}
