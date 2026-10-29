package org.jdbscript.impl.sql;
import org.jdbscript.impl.IMetadataProvider;

import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class PostgreSQLStrategy extends  DefaultSqlExecutorStrategy{
    private IMetadataProvider metadataProvider;

    @Override
    public void setMetadataProvider(IMetadataProvider metadataProvider) {
        this.metadataProvider = metadataProvider;
    }

    @Override
    public void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
        resetPostgreSequences(cnn, tableNames);
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
        return "bytea".equals(metadataProvider.getColumnTypes(tableName).get(columnName.toLowerCase()));
    }

    // pg_table_is_visible applies the search_path. A missing table yields no rows, so the INSERT reports it.
    // Not to_regclass: it takes cstring before 9.6 and text from 9.6 on, so no one call fits both.
    @Override
    public Map<String, String> findColumnTypes(Connection cnn, String tableName) throws SQLException {
        Map<String, String> result = new HashMap<>();
        String sql = """
                SELECT a.attname, t.typname FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_type t ON t.oid = a.atttypid
                WHERE c.relname = ? AND pg_table_is_visible(c.oid) AND a.attnum > 0 AND NOT a.attisdropped
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

    // A standalone sequence isn't attributable to any table, so it stays schema-wide; a sequence
    // owned by a column (SERIAL, IDENTITY, OWNED BY) is reset only with its table.
    private void resetPostgreSequences(Connection cnn, List<String> tableNames) throws SQLException {
            Set<String> tableNamesUpper = tableNames.stream().map(String::toUpperCase).collect(Collectors.toSet());
            try (Statement stmt = cnn.createStatement()) {
                for (Sequence sequence : getSequences(stmt)) {
                    if (sequence.ownerTable() != null && !tableNamesUpper.contains(sequence.ownerTable().toUpperCase())) {
                        continue;
                    }
                    String seqName = sequence.name();
                    // is_called=false: true would mark 10000 itself as consumed, making the next
                    // nextval() return 10001.
                    try (ResultSet rs = stmt.executeQuery(String.format("SELECT setval('%s', 10000, false);", seqName))) {
                    }
                }
            }
    }

    private record Sequence(String name, String ownerTable) {
    }

    // information_schema.sequences deliberately excludes sequences "owned" by a table column
    // (i.e. SERIAL/GENERATED ... AS IDENTITY - see its pg_depend deptype='i' check) - exactly the
    // kind of sequence this needs to reset, so every one of them was silently skipped. pg_class
    // has no such exclusion and works on every supported Postgres/CockroachDB version, so there's
    // no need to branch on version at all. Not scoped to current_schema(): a table's owning
    // sequence can legitimately live in a different schema (see
    // PostgresSequenceSchemaQualificationTest), so scoping this would risk missing it.
    // pg_depend links an owned sequence to its table: deptype 'a' for SERIAL/OWNED BY, 'i' for IDENTITY.
    private List<Sequence> getSequences(Statement stmt) throws SQLException {
        List<Sequence> result = new ArrayList<>();
        String sql = """
                            SELECT quote_ident(n.nspname) || '.' || quote_ident(c.relname), t.relname FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            LEFT JOIN pg_depend d ON d.objid = c.oid AND d.classid = 'pg_class'::regclass
                                AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')
                            LEFT JOIN pg_class t ON t.oid = d.refobjid
                            WHERE c.relkind = 'S' AND n.nspname NOT IN ('pg_catalog', 'information_schema');
                        """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new Sequence(rs.getString(1), rs.getString(2)));
            }
        }
        return result;
    }
}
