package org.jdbscript.impl.sql;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class OracleStrategy extends DefaultSqlExecutorStrategy {

    /** A sequence from {@code user_sequences}, plus the owning table/column for an identity-owned one. */
    private record SequenceInfo(String name, IdentityColumn identityColumn) {
        boolean identityOwned() {
            return identityColumn != null;
        }
    }

    private record IdentityColumn(String tableName, String columnName, String generationType) {
    }

    @Override
    public void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException {
        byte[] bytes = uuid == null? null : toBytes(uuid);
        stmt.setBytes(i, bytes);
    }

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        if ("UUID".equals(expectedType)) {
            byte[] bytes = rs.getBytes(columnIndex);
            return bytes == null ? null : toUUID(bytes);
        }
        return super.getColumnValue(rs, columnIndex, expectedType);
    }

    @Override
    public void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
        resetOracleSequences(cnn, tableNames);
    }

    public void resetOracleSequences(Connection cnn, List<String> tableNames) throws SQLException {
            // A regular sequence isn't attributable to any table, so it stays schema-wide; an
            // identity-owned one always has an owning table/column, so it's scoped to tableNames.
            Set<String> tableNamesUpper = tableNames.stream().map(String::toUpperCase).collect(Collectors.toSet());
            try(Statement stmt = cnn.createStatement()) {
                for (SequenceInfo seq : getSequences(stmt)) {
                    if (seq.identityOwned()) {
                        if (!tableNamesUpper.contains(seq.identityColumn().tableName().toUpperCase())) {
                            continue;
                        }
                        // ALTER TABLE ... MODIFY, not ALTER SEQUENCE - Oracle rejects ALTER
                        // SEQUENCE on an identity column's implicit sequence. Reusing the column's
                        // own GENERATION_TYPE matters: forcing ALWAYS would silently break explicit
                        // inserts into a BY DEFAULT identity column.
                        stmt.executeUpdate("ALTER TABLE %s MODIFY %s GENERATED %s AS IDENTITY (START WITH 10000)"
                                .formatted(seq.identityColumn().tableName(), seq.identityColumn().columnName(),
                                        seq.identityColumn().generationType()));
                    } else {
                        resetRegularSequence(stmt, seq);
                    }
                }
            }
    }

    private void resetRegularSequence(Statement stmt, SequenceInfo seq) throws SQLException {
        try {
            stmt.executeUpdate("ALTER SEQUENCE %s RESTART START WITH 10000".formatted(seq.name()));
        } catch (SQLException e) {
            throw new SQLException(
                    "Failed to reset Oracle sequence '" + seq.name() + "' to 10000 during cleanup "
                            + "(ALTER SEQUENCE ... RESTART requires Oracle 18c+, or 12.2); auto-generated "
                            + "IDs from this sequence may now collide with manually-inserted ones: "
                            + e.getMessage(), e);
        }
    }

    @Override
    public Set<String> getTemporaryTables(Connection cnn, String schema) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement stmt = cnn.prepareStatement(
                "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? AND TEMPORARY = 'Y'")) {
            stmt.setString(1, schema != null ? schema : cnn.getMetaData().getUserName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    private List<SequenceInfo> getSequences(Statement stmt) throws SQLException {
        Map<String, IdentityColumn> identityColumnsBySeqName = new HashMap<>();
        // Oracle names an identity column's implicit sequence 'ISEQ$$_<object_id>' - there's no
        // catalog column that spells out the sequence name directly, so this rebuilds it from the
        // owning table's object_id to join identity-column metadata back to user_sequences.
        String identitySql = """
                SELECT t.TABLE_NAME, t.COLUMN_NAME, t.GENERATION_TYPE, 'ISEQ$$_' || o.OBJECT_ID AS SEQ_NAME
                FROM USER_TAB_IDENTITY_COLS t
                JOIN USER_OBJECTS o ON o.OBJECT_NAME = t.TABLE_NAME AND o.OBJECT_TYPE = 'TABLE'
                """;
        try (ResultSet rs = stmt.executeQuery(identitySql)) {
            while (rs.next()) {
                identityColumnsBySeqName.put(rs.getString("SEQ_NAME"),
                        new IdentityColumn(rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"),
                                rs.getString("GENERATION_TYPE")));
            }
        }

        List<SequenceInfo> result = new ArrayList<>();
        String sql = "SELECT SEQUENCE_NAME FROM user_sequences";
        try(ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String seqName = rs.getString("SEQUENCE_NAME");
                result.add(new SequenceInfo(seqName, identityColumnsBySeqName.get(seqName)));
            }
        }
        return result;
    }

    private UUID toUUID(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return new UUID(byteBuffer.getLong(), byteBuffer.getLong());
    }

    private byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES*2);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
