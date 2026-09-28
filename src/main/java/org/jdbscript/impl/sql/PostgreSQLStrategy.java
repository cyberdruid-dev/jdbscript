package org.jdbscript.impl.sql;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class PostgreSQLStrategy extends  DefaultSqlExecutorStrategy{
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

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        if ("blob".equals(expectedType)) {
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
