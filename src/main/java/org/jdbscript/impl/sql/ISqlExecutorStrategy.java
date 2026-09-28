package org.jdbscript.impl.sql;

import org.jdbscript.JDBFeatureSet;
import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.cache.IJDBCache;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ISqlExecutorStrategy {
    void afterInsert(Connection cnn) throws SQLException;

    void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException;

    /**
     * Resets any DBMS-managed sequences this strategy is responsible for to a predictable floor.
     * Called once per {@code cleanupTables(...)} call, after all rows are deleted, so a reset can
     * be unconditional. No-op by default; only strategies for DBMS that manage sequences override
     * it.
     *
     * @param tableNames the tables just cleaned up. A sequence attributable to a specific
     *                    table/column (an identity/auto-increment column) must be scoped to these
     *                    tables; one that can't (a standalone, user-created sequence) is reset
     *                    schema/database-wide, as before.
     */
    default void resetSequences(Connection cnn, List<String> tableNames) throws SQLException {
    }

    default void beforeEachInsert(Connection cnn, JDBRecord record) throws SQLException {
    }

    void setInputStream(PreparedStatement stmt, int i, InputStream value) throws SQLException;

    void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException;

    void setByteArray(PreparedStatement stmt, int i, byte[] value) throws SQLException;

    void setObject(PreparedStatement stmt, int i, Object value) throws SQLException;

    void onConnection(Connection cnn) throws SQLException;

    void commit(Connection cnn) throws SQLException;

    String getSearchCatalog(Connection cnn) throws SQLException;

    String getSearchSchema(Connection cnn) throws SQLException;

    String[] getTableTypes();

    Set<String> getRawTableDependencies(Connection cnn, String catalog, String schema, String tableName) throws SQLException;

    Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException;

    /**
     * Sets the enabled features. No-op by default; only a strategy with DBMS-specific behavior
     * gated by a feature overrides this (e.g. DB2's identity-owned-sequence handling).
     *
     * @param features the enabled features
     */
    default void setFeatures(JDBFeatureSet features) {
    }

    default void setCache(IJDBCache cache) {
    }
}
