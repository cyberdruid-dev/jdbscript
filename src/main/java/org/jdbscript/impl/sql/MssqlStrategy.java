package org.jdbscript.impl.sql;

import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.IJDBCache.IJDBCacheKey;
import org.jdbscript.impl.cache.NoCache;

import java.io.InputStream;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;

class MssqlStrategy extends DefaultSqlExecutorStrategy {
    private record IdentityColumnKey(String tableName) implements IJDBCacheKey<String> {}

    private IJDBCache cache = new NoCache();
    private String identityInsertOnTable;

    @Override
    public void setCache(IJDBCache cache) {
        this.cache = cache != null ? cache : new NoCache();
    }

    @Override
    public void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException {
        this.identityInsertOnTable = null;
    }

    @Override
    public void beforeEachInsert(Connection cnn, JDBRecord record) throws SQLException {
        String tableName = record.getTableName();
        String identityColumn = identityColumn(cnn, tableName);
        boolean suppliesIdentityColumn = identityColumn != null
                && record.getColumns().keySet().stream().anyMatch(c -> c.equalsIgnoreCase(identityColumn));
        String desiredTable = suppliesIdentityColumn ? tableName : null;
        if (Objects.equals(desiredTable, identityInsertOnTable)) {
            return;
        }
        if (identityInsertOnTable != null) {
            identityInsertOff(cnn, identityInsertOnTable);
        }
        if (desiredTable != null) {
            identityInsertOn(cnn, desiredTable);
        }
        identityInsertOnTable = desiredTable;
    }

    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        if (identityInsertOnTable != null) {
            identityInsertOff(cnn, identityInsertOnTable);
            identityInsertOnTable = null;
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

    private String identityColumn(Connection cnn, String tableName) throws SQLException {
        try {
            return cache.getOrCompute(new IdentityColumnKey(tableName), k -> {
                try {
                    return findIdentityColumn(cnn, k.tableName());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw e;
        }
    }

    private String findIdentityColumn(Connection cnn, String tableName) throws SQLException {
        try (ResultSet rs = cnn.getMetaData().getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                    return rs.getString("COLUMN_NAME");
                }
            }
        }
        return null;
    }

    private void identityInsertOn(Connection cnn, String tableName) throws SQLException {
        execute(cnn, String.format("SET IDENTITY_INSERT %s ON;", tableName));
    }

    private void identityInsertOff(Connection cnn, String tableName) throws SQLException {
        execute(cnn, String.format("SET IDENTITY_INSERT %s OFF;", tableName));
    }

    private void execute(Connection cnn, String sql) throws SQLException {
        try (Statement stmt = cnn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
