package org.jdbscript.impl.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class H2Strategy extends DefaultSqlExecutorStrategy {

    @Override
    public void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
        // A standalone sequence isn't attributable to any table, so it stays schema-wide; an
        // identity column always has an owning table, so it's scoped to tableNames.
        Set<String> tableNamesUpper = tableNames.stream().map(String::toUpperCase).collect(Collectors.toSet());
        try (Statement stmt = cnn.createStatement()) {
            for (String seqName : queryNames(stmt, "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES "
                    + "WHERE SEQUENCE_SCHEMA = CURRENT_SCHEMA", "sequences")) {
                restart(stmt, "ALTER SEQUENCE " + quote(seqName) + " RESTART WITH 10000", "sequence '" + seqName + "'");
            }
            for (IdentityColumn column : queryIdentityColumns(stmt)) {
                if (!tableNamesUpper.contains(column.tableName().toUpperCase())) {
                    continue;
                }
                restart(stmt, "ALTER TABLE " + quote(column.tableName()) + " ALTER COLUMN " + quote(column.columnName())
                        + " RESTART WITH 10000", "identity column '" + column.tableName() + "." + column.columnName() + "'");
            }
        }
    }

    private void restart(Statement stmt, String sql, String what) throws SQLException {
        try {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new SQLException("Failed to reset H2 " + what + " to a safe value during cleanup; "
                    + "auto-generated IDs may now collide with manually-inserted ones: " + e.getMessage(), e);
        }
    }

    private List<String> queryNames(Statement stmt, String sql, String what) throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new SQLException("Could not query INFORMATION_SCHEMA to discover H2 " + what + " to reset "
                    + "during cleanup; their auto-generated IDs may now collide with manually-inserted ones: "
                    + e.getMessage(), e);
        }
        return result;
    }

    private record IdentityColumn(String tableName, String columnName) {
    }

    // An identity column's counter isn't listed in INFORMATION_SCHEMA.SEQUENCES; it can only be
    // reset through ALTER TABLE ... ALTER COLUMN ... RESTART WITH.
    private List<IdentityColumn> queryIdentityColumns(Statement stmt) throws SQLException {
        List<IdentityColumn> result = new ArrayList<>();
        String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND IS_IDENTITY = 'YES'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new IdentityColumn(rs.getString(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            throw new SQLException("Could not query INFORMATION_SCHEMA to discover H2 identity columns to reset "
                    + "during cleanup; their auto-generated IDs may now collide with manually-inserted ones: "
                    + e.getMessage(), e);
        }
        return result;
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
