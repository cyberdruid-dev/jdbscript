package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        // Unlike real PostgreSQL, CockroachDB's `bytea` columns don't round-trip through the
        // Postgres JDBC driver's rs.getBlob(); fall back to the plain byte-array read that
        // DefaultSqlExecutorStrategy uses instead of PostgreSQLStrategy's getBlob()-based one.
        if ("blob".equals(expectedType)) {
            return rs.getBytes(columnIndex);
        }
        return super.getColumnValue(rs, columnIndex, expectedType);
    }
}
