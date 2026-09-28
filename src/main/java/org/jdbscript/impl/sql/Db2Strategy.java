package org.jdbscript.impl.sql;

import org.jdbscript.JDBFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.jdbscript.JDBFeature.DB2_ID_OWNED_SEQUENCE_ERROR;
import static org.jdbscript.JDBFeature.Group.DB2_ID_OWNED_SEQUENCE;

class Db2Strategy extends DefaultSqlExecutorStrategy {
    private static final Logger log = LoggerFactory.getLogger(Db2Strategy.class);


    private JDBFeature getIdOwnedSequenceFeature() {
        return getOrDefaultFeature(DB2_ID_OWNED_SEQUENCE, DB2_ID_OWNED_SEQUENCE_ERROR);
    }

    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        resetDb2Sequences(cnn);
    }

    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException {
        if (value == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            super.setInputStream(stmt, columnIndex, value);
        }
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        if (bytes == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            super.setByteArray(stmt, columnIndex, bytes);
        }
    }

    @Override
    public void setObject(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        int targetType = Types.OTHER;
        boolean hasType = false;
        try {
            ParameterMetaData meta = stmt.getParameterMetaData();
            if (meta != null) {
                targetType = meta.getParameterType(columnIndex);
                hasType = true;
            }
        } catch (SQLException e) {
            log.debug("Unable to determine parameter metadata: {}", e.getMessage());
        }

        if (value == null) {
            if (hasType) {
                stmt.setNull(columnIndex, targetType);
            } else {
                stmt.setNull(columnIndex, Types.NULL);
            }
            return;
        }

        if (targetType == Types.DATE) {
            setDate(stmt, columnIndex, value);
            return;
        }

        if (targetType == Types.TIMESTAMP) {
            setTimestamp(stmt, columnIndex, value);
            return;
        }

        if (value instanceof LocalDate) {
            setDate(stmt, columnIndex, value);
            return;
        }

        if (value instanceof Instant || value instanceof Date) {
            setTimestamp(stmt, columnIndex, value);
            return;
        }

        if (hasType) {
            stmt.setObject(columnIndex, value, targetType);
        } else {
            stmt.setObject(columnIndex, value);
        }
    }

    private void setDate(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        if (value instanceof LocalDate localDate) {
            stmt.setDate(columnIndex, java.sql.Date.valueOf(localDate));
        } else if (value instanceof java.sql.Date sqlDate) {
            stmt.setDate(columnIndex, sqlDate);
        } else if (value instanceof Date date) {
            stmt.setDate(columnIndex, new java.sql.Date(date.getTime()));
        } else {
            stmt.setObject(columnIndex, value, Types.DATE);
        }
    }

    private void setTimestamp(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        if (value instanceof Timestamp timestamp) {
            stmt.setTimestamp(columnIndex, timestamp);
        } else if (value instanceof Instant instant) {
            stmt.setTimestamp(columnIndex, Timestamp.from(instant));
        } else if (value instanceof Date date) {
            stmt.setTimestamp(columnIndex, new Timestamp(date.getTime()));
        } else {
            stmt.setObject(columnIndex, value, Types.TIMESTAMP);
        }
    }

    public void resetDb2Sequences(Connection cnn) throws SQLException {
        try (Statement stmt = cnn.createStatement()) {
            for (SequenceInfo seq : getSequences(stmt)) {
                if (seq.identityOwned()) {
                    resetIdentityOwnedSequence(stmt, seq);
                } else {
                    resetRegularSequence(stmt, seq);
                }
            }
        }
    }

    private void resetRegularSequence(Statement stmt, SequenceInfo seq) throws SQLException {
        try {
            stmt.executeUpdate(String.format("ALTER SEQUENCE %s RESTART WITH 10000", seq.name()));
        } catch (SQLException e) {
            // Must surface loudly rather than being swallowed: an auto-generated ID from this
            // sequence could now collide with a manually-inserted one.
            throw new SQLException(
                    "Failed to reset DB2 sequence '" + seq.name() + "' to a safe value after "
                            + "insert; auto-generated IDs from this sequence may now collide "
                            + "with manually-inserted ones: " + e.getMessage(), e);
        }
    }

    /**
     * A sequence DB2 created implicitly for an identity column (SEQTYPE 'I') can't be touched via
     * {@code ALTER SEQUENCE} directly - DB2 rejects it with SQLCODE -20142. What to do about it is
     * governed by the {@link JDBFeature.Group#DB2_ID_OWNED_SEQUENCE} feature group, since none of
     * the alternatives are safe to pick silently: skipping it leaves a collision risk, and
     * resetting it requires DDL ({@code ALTER TABLE}) some users may not want run automatically.
     */
    private void resetIdentityOwnedSequence(Statement stmt, SequenceInfo seq) throws SQLException {
        JDBFeature feature = getIdOwnedSequenceFeature();
        switch (feature) {
            case DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED -> {
                // Left alone, on purpose - see JDBFeature's javadoc for the tradeoff.
            }
            case DB2_ID_OWNED_SEQUENCE_RESTART_WITH -> {
                try {
                    stmt.executeUpdate(String.format("ALTER TABLE %s ALTER COLUMN %s RESTART WITH 10000",
                            seq.tableName(), seq.columnName()));
                } catch (SQLException e) {
                    throw new SQLException(
                            "Failed to reset identity column '" + seq.tableName() + "." + seq.columnName()
                                    + "' (backed by DB2 sequence '" + seq.name() + "') to a safe value "
                                    + "after insert; auto-generated IDs from this column may now collide "
                                    + "with manually-inserted ones: " + e.getMessage(), e);
                }
            }
            case DB2_ID_OWNED_SEQUENCE_ERROR -> throw new SQLException(
                    "Table '" + seq.tableName() + "' column '" + seq.columnName() + "' has an "
                            + "identity-owned sequence ('" + seq.name() + "') that DB2 won't let "
                            + "ALTER SEQUENCE touch directly. Call .feature(JDBFeature."
                            + "DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED) on the engine builder to leave it "
                            + "alone, or .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH) to "
                            + "reset it via ALTER TABLE.");
            default -> throw new IllegalStateException("Unexpected feature: " + feature);
        }
    }

    /** One row of the SYSCAT.SEQUENCES/SYSCAT.COLIDENTATTRIBUTES join below. */
    private record SequenceInfo(String name, boolean identityOwned, String tableName, String columnName) {
    }

    private List<SequenceInfo> getSequences(Statement stmt) throws SQLException {
        List<SequenceInfo> result = new ArrayList<>();
        // An empty database (zero sequences) still queries SYSCAT.SEQUENCES successfully, returning
        // zero rows - so this query failing at all means something is actually wrong, not "no
        // sequences". If the schema actually uses sequences, they'd go silently unreset - that must
        // surface loudly, not be treated as nothing to do.
        // SEQTYPE 'I' identifies a sequence DB2 created implicitly for an identity column (as
        // opposed to 'S', a sequence created explicitly via CREATE SEQUENCE); the left join against
        // SYSCAT.COLIDENTATTRIBUTES resolves such a sequence back to the table/column it backs, so
        // an 'I' row always has TABNAME/COLNAME populated and an 'S' row never does.
        String sql = """
                SELECT s.SEQNAME, s.SEQTYPE, c.TABNAME, c.COLNAME
                FROM SYSCAT.SEQUENCES s
                LEFT JOIN SYSCAT.COLIDENTATTRIBUTES c
                  ON c.SEQID = s.SEQID AND c.TABSCHEMA = s.SEQSCHEMA
                WHERE s.SEQSCHEMA = CURRENT SCHEMA
                """;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                boolean identityOwned = "I".equals(rs.getString("SEQTYPE"));
                result.add(new SequenceInfo(rs.getString("SEQNAME"), identityOwned,
                        rs.getString("TABNAME"), rs.getString("COLNAME")));
            }
        } catch (SQLException e) {
            throw new SQLException(
                    "Could not query SYSCAT.SEQUENCES to discover DB2 sequences to reset after "
                            + "insert; if this schema uses sequences, their auto-generated IDs may "
                            + "now collide with manually-inserted ones: " + e.getMessage(), e);
        }
        return result;
    }
}
