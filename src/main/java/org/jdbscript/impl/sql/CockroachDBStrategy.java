package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;

class CockroachDBStrategy extends PostgreSQLStrategy {

    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException {
        stmt.setBinaryStream(columnIndex, value);
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        stmt.setBytes(columnIndex, bytes);
    }
}
