package org.jdbscript.impl.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

class MySqlStrategy extends DefaultSqlExecutorStrategy {

    // InnoDB checks foreign keys row by row, not at statement end, so deleting all rows of a
    // self-referencing table fails whenever a parent row is visited before its children.
    @Override
    public void beforeCleanup(Connection cnn) throws SQLException {
        setForeignKeyChecks(cnn, 0);
    }

    @Override
    public void afterCleanup(Connection cnn) throws SQLException {
        setForeignKeyChecks(cnn, 1);
    }

    private void setForeignKeyChecks(Connection cnn, int value) throws SQLException {
        try (Statement stmt = cnn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = " + value);
        }
    }
}
