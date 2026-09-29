package org.jdbscript.impl.sql;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.IJDBCache.IJDBCacheKey;
import org.jdbscript.impl.cache.NoCache;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PostgreSQLStrategy extends  DefaultSqlExecutorStrategy{
    private record ColumnTypesKey(String tableName) implements IJDBCacheKey<Map<String, String>> {}

    private IJDBCache cache = new NoCache();

    @Override
    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
    }

    @Override
    public void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
        // Ignored: getSequences() can't attribute a sequence to a table (see its comment), so
        // there's nothing to scope by.
        resetPostgreSequences(cnn);
    }

    @Override
    public void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException {
        stmt.setObject(i, uuid);
    }

    // A plain "blob" binding is a large object whose oid is sent, which a bytea column rejects.
    @Override
    public void setInputStream(Connection cnn, String tableName, String columnName, PreparedStatement stmt, int i, InputStream value) throws SQLException {
        if (isBytea(cnn, tableName, columnName)) {
            stmt.setBinaryStream(i, value);
        } else {
            setInputStream(stmt, i, value);
        }
    }

    @Override
    public void setByteArray(Connection cnn, String tableName, String columnName, PreparedStatement stmt, int i, byte[] value) throws SQLException {
        if (isBytea(cnn, tableName, columnName)) {
            stmt.setBytes(i, value);
        } else {
            setByteArray(stmt, i, value);
        }
    }

    protected boolean isBytea(Connection cnn, String tableName, String columnName) throws SQLException {
        return "bytea".equals(columnTypes(cnn, tableName).get(columnName.toLowerCase()));
    }

    private Map<String, String> columnTypes(Connection cnn, String tableName) throws SQLException {
        try {
            return cache.getOrCompute(new ColumnTypesKey(tableName), k -> {
                try {
                    return findColumnTypes(cnn, k.tableName());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw e;
        }
    }

    // pg_table_is_visible + lower() resolves the name exactly as the unquoted INSERT will (search_path,
    // case folding), and a missing table just yields no rows, so the INSERT reports it as usual.
    // Not to_regclass: it takes cstring before 9.6 and text from 9.6 on, so no one call fits both.
    private Map<String, String> findColumnTypes(Connection cnn, String tableName) throws SQLException {
        Map<String, String> result = new HashMap<>();
        String sql = """
                SELECT a.attname, t.typname FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_type t ON t.oid = a.atttypid
                WHERE c.relname = lower(?) AND pg_table_is_visible(c.oid) AND a.attnum > 0 AND NOT a.attisdropped
                """;
        try (PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1).toLowerCase(), rs.getString(2));
                }
            }
        }
        return result;
    }

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        if ("blob".equals(expectedType)) {
            if ("bytea".equals(rs.getMetaData().getColumnTypeName(columnIndex))) {
                return rs.getBytes(columnIndex);
            }
            Blob blob = rs.getBlob(columnIndex);
            return blob == null ? null : blob.getBytes(1, (int) blob.length());
        }
        return super.getColumnValue(rs, columnIndex, expectedType);
    }

    private void resetPostgreSequences(Connection cnn) throws SQLException {
            try (Statement stmt = cnn.createStatement()) {
                List<String> seqNames = getSequences(stmt);
                for (String seqName : seqNames) {
                    // is_called=false: true would mark 10000 itself as consumed, making the next
                    // nextval() return 10001.
                    try (ResultSet rs = stmt.executeQuery(String.format("SELECT setval('%s', 10000, false);", seqName))) {
                    }
                }
            }
    }

    // information_schema.sequences deliberately excludes sequences "owned" by a table column
    // (i.e. SERIAL/GENERATED ... AS IDENTITY - see its pg_depend deptype='i' check) - exactly the
    // kind of sequence this needs to reset, so every one of them was silently skipped. pg_class
    // has no such exclusion and works on every supported Postgres/CockroachDB version, so there's
    // no need to branch on version at all. Not scoped to current_schema(): a table's owning
    // sequence can legitimately live in a different schema (see
    // PostgresSequenceSchemaQualificationTest), so scoping this would risk missing it.
    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = """
                            SELECT quote_ident(n.nspname) || '.' || quote_ident(c.relname) FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            WHERE c.relkind = 'S' AND n.nspname NOT IN ('pg_catalog', 'information_schema');
                        """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }
}
