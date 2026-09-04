package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class HsqldbStrategy extends DefaultSqlExecutorStrategy {

    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        resetHsqldbSequences(cnn);
    }

    @Override
    public void setUUID(PreparedStatement stmt, int columnIndex, UUID uuid) throws SQLException {
        if(uuid == null) {
            stmt.setNull(columnIndex, Types.VARCHAR);
        } else {
            super.setUUID(stmt, columnIndex, uuid);
        }
    }

    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException {
        if(value == null ) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            super.setInputStream(stmt, columnIndex, value);
        }
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        if(bytes == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            super.setByteArray(stmt, columnIndex, bytes);
        }
    }
    public void resetHsqldbSequences(Connection cnn) throws SQLException {
        try (Statement stmt = cnn.createStatement()) {
            List<String> seqNames = getSequences(stmt);
            for (String seqName : seqNames) {
                try {
                    stmt.executeUpdate(String.format("ALTER SEQUENCE %s RESTART WITH 10000", seqName));
                } catch (SQLException e) {
                    // Must surface loudly rather than being swallowed: an auto-generated ID from
                    // this sequence could now collide with a manually-inserted one.
                    throw new SQLException(
                            "Failed to reset HSQLDB sequence '" + seqName + "' to a safe value after "
                                    + "insert; auto-generated IDs from this sequence may now collide "
                                    + "with manually-inserted ones: " + e.getMessage(), e);
                }
            }
        }
    }

    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        // An empty database (zero sequences) still queries INFORMATION_SCHEMA.SYSTEM_SEQUENCES
        // successfully, returning zero rows - so this query failing at all means the view itself is
        // unavailable, not "no sequences". If the schema actually uses sequences, they'd go silently
        // unreset - that must surface loudly, not be treated as nothing to do.
        String sql = "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("SEQUENCE_NAME"));
            }
        } catch (SQLException e) {
            throw new SQLException(
                    "Could not query INFORMATION_SCHEMA.SYSTEM_SEQUENCES to discover HSQLDB sequences "
                            + "to reset after insert; if this schema uses sequences, their "
                            + "auto-generated IDs may now collide with manually-inserted ones: "
                            + e.getMessage(), e);
        }
        return result;
    }
}
