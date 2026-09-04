package org.jdbscript.impl.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class Db2Strategy extends DefaultSqlExecutorStrategy {
    private static final Logger log = LoggerFactory.getLogger(Db2Strategy.class);

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
            List<String> seqNames = getSequences(stmt);
            for (String seqName : seqNames) {
                try {
                    stmt.executeUpdate(String.format("ALTER SEQUENCE %s RESTART WITH 10000", seqName));
                } catch (SQLException e) {
                    // Must surface loudly rather than being swallowed: an auto-generated ID from
                    // this sequence could now collide with a manually-inserted one.
                    throw new SQLException(
                            "Failed to reset DB2 sequence '" + seqName + "' to a safe value after "
                                    + "insert; auto-generated IDs from this sequence may now collide "
                                    + "with manually-inserted ones: " + e.getMessage(), e);
                }
            }
        }
    }

    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        // An empty database (zero sequences) still queries SYSCAT.SEQUENCES successfully, returning
        // zero rows - so this query failing at all means something is actually wrong, not "no
        // sequences". If the schema actually uses sequences, they'd go silently unreset - that must
        // surface loudly, not be treated as nothing to do.
        String sql = "SELECT SEQNAME FROM SYSCAT.SEQUENCES WHERE SEQSCHEMA = CURRENT SCHEMA";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("SEQNAME"));
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
