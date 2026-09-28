package org.jdbscript.impl.sql;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class PostgreSQLStrategy extends  DefaultSqlExecutorStrategy{
    @Override
    public void afterInsert(Connection cnn) throws SQLException {
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
                // GREATEST() makes this advance-only: setval() alone would rewind a sequence
                // that's already past 10000, handing out a value it gave out once already.
                // Selecting last_value from the sequence relation itself (rather than the
                // pg_sequences catalog view, added only in PG10+) works on every Postgres version.
                String sql = "SELECT setval('%1$s', GREATEST((SELECT last_value FROM %1$s), 10000), true);";
                for (String seqName : seqNames) {
                    stmt.executeQuery(String.format(sql, seqName));
                }
            }
    }

    // information_schema.sequences deliberately excludes sequences "owned" by a table column
    // (i.e. SERIAL/GENERATED ... AS IDENTITY - see its pg_depend deptype='i' check) - exactly the
    // kind of sequence this needs to reset, so every one of them was silently skipped. pg_class
    // has no such exclusion and works on every supported Postgres/CockroachDB version, so there's
    // no need to branch on version at all.
    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = """
                            SELECT c.relname FROM pg_class c
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
