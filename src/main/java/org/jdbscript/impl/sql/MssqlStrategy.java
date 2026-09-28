package org.jdbscript.impl.sql;

import org.jdbscript.impl.JDBScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class MssqlStrategy extends DefaultSqlExecutorStrategy {
    private static final Logger log = LoggerFactory.getLogger(MssqlStrategy.class);
    private Set<String> identityInsertOn;

    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        for (String table : identityInsertOn) {
            identityInsertOff(cnn, table);
        }
    }

    @Override
    public void setUUID(PreparedStatement stmt, int columnIndex, UUID uuid) throws SQLException {
        if(uuid == null) {
            stmt.setNull(columnIndex, Types.BLOB);
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

    @Override
    public void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException {
        this.identityInsertOn = new HashSet<>();
        Set<String> seen = new HashSet<>();
        //TODO: find a way to lookup tables that require it.
        for (var record : dbScript.getRecords()) {
            String tableName = record.getTableName();
            if (seen.add(tableName)) {
                identityInsertOn(cnn, tableName);
            }
        }
    }

    private void identityInsertOn(Connection cnn, String tableName) throws SQLException {
        String sql = String.format("SET IDENTITY_INSERT %s ON;", tableName);
        execute(cnn, sql, ()-> identityInsertOn.add(tableName));
    }

    private void identityInsertOff(Connection cnn, String tableName) throws SQLException {
        String sql = String.format("SET IDENTITY_INSERT %s OFF;", tableName);
        execute(cnn, sql, ()-> identityInsertOn.remove(tableName));
    }

    private void execute(Connection cnn, String  sql, Runnable onSuccess) throws SQLException {
        try ( Statement stmt = cnn.createStatement()) {
            stmt.execute(sql);
            onSuccess.run();
        } catch (SQLException e) {//just ignore for now
            log.error(e.getMessage(), e);
        }
    }
}
