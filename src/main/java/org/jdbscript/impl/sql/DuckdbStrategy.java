package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
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

    @Override
    public void onConnection(Connection cnn) throws SQLException {
        // DuckDB sometimes has issues with transactions and foreign keys in the same transaction.
        // We keep auto-commit enabled.
    }

    @Override
    public void commit(Connection cnn) throws SQLException {
        // Do nothing for DuckDB
    }

    @Override
    public String getSearchCatalog(Connection cnn) throws SQLException {
        return null;
    }

    @Override
    public String getSearchSchema(Connection cnn) throws SQLException {
        return null;
    }

    @Override
    public String[] getTableTypes() {
        return new String[]{"TABLE", "BASE TABLE"};
    }

    @Override
    public Set<String> getRawTableDependencies(Connection cnn, String catalog, String schema, String tableName) throws SQLException {
        Set<String> allDeps = new HashSet<>();
        String sql = "SELECT referenced_table FROM duckdb_constraints WHERE UPPER(table_name) = UPPER(?) AND constraint_type = 'FOREIGN KEY'";
        try (var stmt = cnn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String referencedTable = rs.getString(1);
                    if (referencedTable != null) {
                        allDeps.add(referencedTable.toUpperCase());
                    }
                }
            }
        } catch (SQLException e) {
            // Fallback to default if duckdb_constraints fails
            return super.getRawTableDependencies(cnn, catalog, schema, tableName);
        }

        if (allDeps.isEmpty()) {
            return super.getRawTableDependencies(cnn, catalog, schema, tableName);
        }

        return allDeps;
    }

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        if ("blob".equals(expectedType)) {
            java.sql.Blob blob = rs.getBlob(columnIndex);
            return blob == null ? null : blob.getBytes(1, (int) blob.length());
        } else if ("UUID".equals(expectedType)) {
            return rs.getObject(columnIndex);
        }

        int columnType = rs.getMetaData().getColumnType(columnIndex);
        if (columnType == Types.BLOB) {
            java.sql.Blob blob = rs.getBlob(columnIndex);
            return blob == null ? null : blob.getBytes(1, (int) blob.length());
        }

        return super.getColumnValue(rs, columnIndex, expectedType);
    }
}
