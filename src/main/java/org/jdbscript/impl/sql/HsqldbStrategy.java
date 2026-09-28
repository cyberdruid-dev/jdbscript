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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class HsqldbStrategy extends DefaultSqlExecutorStrategy {

    @Override
    public void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
        resetHsqldbSequences(cnn, tableNames);
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
    public void resetHsqldbSequences(Connection cnn, List<String> tableNames) throws SQLException {
        // A standalone sequence isn't attributable to any table, so it stays schema-wide; an
        // identity column always has an owning table, so it's scoped to tableNames.
        Set<String> tableNamesUpper = tableNames.stream().map(String::toUpperCase).collect(Collectors.toSet());
        try (Statement stmt = cnn.createStatement()) {
            for (String seqName : getSequences(stmt)) {
                try {
                    stmt.executeUpdate(String.format("ALTER SEQUENCE %s RESTART WITH 10000", seqName));
                } catch (SQLException e) {
                    // Must surface loudly rather than being swallowed: an auto-generated ID from
                    // this sequence could now collide with a manually-inserted one.
                    throw new SQLException(
                            "Failed to reset HSQLDB sequence '" + seqName + "' to a safe value during "
                                    + "cleanup; auto-generated IDs from this sequence may now collide "
                                    + "with manually-inserted ones: " + e.getMessage(), e);
                }
            }
            for (IdentityColumn column : getIdentityColumns(stmt)) {
                if (!tableNamesUpper.contains(column.tableName().toUpperCase())) {
                    continue;
                }
                try {
                    stmt.executeUpdate(String.format("ALTER TABLE %s ALTER COLUMN %s RESTART WITH 10000",
                            column.tableName(), column.columnName()));
                } catch (SQLException e) {
                    throw new SQLException(
                            "Failed to reset identity column '" + column.tableName() + "." + column.columnName()
                                    + "' to a safe value during cleanup; auto-generated IDs from this column "
                                    + "may now collide with manually-inserted ones: " + e.getMessage(), e);
                }
            }
        }
    }

    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        // A failure here means the view itself is unavailable, not "no sequences" (an empty
        // database still queries it successfully) - must surface loudly, not be swallowed. This
        // view never lists an identity column's own counter, only a standalone CREATE SEQUENCE -
        // see getIdentityColumns() for that other half.
        String sql = "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("SEQUENCE_NAME"));
            }
        } catch (SQLException e) {
            throw new SQLException(
                    "Could not query INFORMATION_SCHEMA.SYSTEM_SEQUENCES to discover HSQLDB sequences "
                            + "to reset during cleanup; if this schema uses sequences, their "
                            + "auto-generated IDs may now collide with manually-inserted ones: "
                            + e.getMessage(), e);
        }
        return result;
    }

    private record IdentityColumn(String tableName, String columnName) {
    }

    // An identity column's own counter never appears in INFORMATION_SCHEMA.SYSTEM_SEQUENCES and
    // can't be touched by ALTER SEQUENCE; IS_IDENTITY + ALTER TABLE ... RESTART WITH is the only way
    // to find and reset it.
    private List<IdentityColumn> getIdentityColumns(Statement stmt) throws SQLException {
        List<IdentityColumn> result = new ArrayList<>();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = 'PUBLIC' AND IS_IDENTITY = 'YES'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new IdentityColumn(rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME")));
            }
        } catch (SQLException e) {
            throw new SQLException(
                    "Could not query INFORMATION_SCHEMA.COLUMNS to discover HSQLDB identity columns "
                            + "to reset during cleanup; if this schema uses identity columns, their "
                            + "auto-generated IDs may now collide with manually-inserted ones: "
                            + e.getMessage(), e);
        }
        return result;
    }
}
