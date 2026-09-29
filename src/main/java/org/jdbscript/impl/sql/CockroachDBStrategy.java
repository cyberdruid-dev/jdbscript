package org.jdbscript.impl.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

class CockroachDBStrategy extends PostgreSQLStrategy {

    // CockroachDB has no large objects: every binary column is BYTES (bytea).
    @Override
    protected boolean isBytea(Connection cnn, String tableName, String columnName) {
        return true;
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
