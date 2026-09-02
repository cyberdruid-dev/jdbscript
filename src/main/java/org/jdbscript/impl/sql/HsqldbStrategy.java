package org.jdbscript.impl.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(HsqldbStrategy.class);

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
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private List<String> getSequences(Statement stmt) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("SEQUENCE_NAME"));
            }
        } catch (SQLException e) {
            log.debug("Failed to query HSQLDB sequences: {}", e.getMessage());
        }
        return result;
    }
}
