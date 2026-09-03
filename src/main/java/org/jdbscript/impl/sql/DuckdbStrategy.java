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
    public void afterInsert(Connection cnn) throws SQLException {
        resetDuckdbSequences(cnn);
    }

    private void resetDuckdbSequences(Connection cnn) throws SQLException {
        try (var stmt = cnn.createStatement()) {
            Set<String> seqNames = new HashSet<>();
            try (var rs = stmt.executeQuery("SELECT sequencename FROM pg_catalog.pg_sequences")) {
                while (rs.next()) {
                    seqNames.add(rs.getString(1));
                }
            } catch (SQLException e) {
                // A database with zero sequences still queries pg_catalog.pg_sequences
                // successfully, returning zero rows - so this query failing at all means the view
                // itself is unavailable (e.g. an older duckdb_jdbc driver), not "no sequences". If
                // the schema actually uses sequences, they'd go silently unreset - that must
                // surface loudly, not be treated as nothing to do.
                throw new SQLException(
                        "Could not query pg_catalog.pg_sequences to discover DuckDB sequences to "
                                + "reset after insert; if this schema uses sequences, their "
                                + "auto-generated IDs may now collide with manually-inserted ones: "
                                + e.getMessage(), e);
            }
            for (String seqName : seqNames) {
                // DuckDB sequences don't advance automatically on manual inserts.
                // We advance them by a safe margin (10000) to avoid collisions with manual IDs.
                // We use range() to call nextval multiple times as ALTER SEQUENCE RESTART is not yet fully supported in JDBC.
                try (var rs = stmt.executeQuery("SELECT nextval('" + seqName + "') FROM range(1, 10000)")) {
                    // Just execute and close
                } catch (SQLException e) {
                    throw new SQLException(
                            "Failed to reset DuckDB sequence '" + seqName + "' to a safe value after "
                                    + "insert; auto-generated IDs from this sequence may now collide "
                                    + "with manually-inserted ones: " + e.getMessage(), e);
                }
            }
        }
    }

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
