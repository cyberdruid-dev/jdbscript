package org.jdbscript.impl.sql;

import org.jdbscript.JDBFeature;
import org.jdbscript.JDBFeatureSet;
import org.jdbscript.impl.JDBScript;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class DefaultSqlExecutorStrategy implements ISqlExecutorStrategy {


    private JDBFeatureSet features = JDBFeatureSet.empty();

    @Override
    public void setFeatures(JDBFeatureSet features) {
        this.features = features != null ? features : JDBFeatureSet.empty();
    }

    @Override
    public void afterInsert(Connection cnn) throws SQLException {

    }

    @Override
    public void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException {
    }

    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException{
        stmt.setBlob(columnIndex, value);
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        InputStream in = bytes == null?  null : new ByteArrayInputStream(bytes);
        setInputStream(stmt, columnIndex, in);
    }

    @Override
    public void setUUID(PreparedStatement stmt, int columnIndex, UUID uuid) throws SQLException {
        String value = uuid == null? null : uuid.toString();
        stmt.setString(columnIndex, value);
    }

    @Override
    public void setObject(PreparedStatement stmt, int columnIndex, Object value) throws SQLException {
        stmt.setObject(columnIndex, value);
    }

    @Override
    public void onConnection(Connection cnn) throws SQLException {
        cnn.setAutoCommit(false);
    }

    @Override
    public void commit(Connection cnn) throws SQLException {
        cnn.commit();
    }

    @Override
    public String getSearchCatalog(Connection cnn) throws SQLException {
        return cnn.getCatalog();
    }

    @Override
    public String getSearchSchema(Connection cnn) throws SQLException {
        return cnn.getSchema();
    }

    @Override
    public String[] getTableTypes() {
        return new String[]{"TABLE"};
    }

    @Override
    public Set<String> getRawTableDependencies(Connection cnn, String catalog, String schema, String tableName) throws SQLException {
        Set<String> allDeps = new HashSet<>();
        DatabaseMetaData metaData = cnn.getMetaData();
        collectImportedKeys(allDeps, metaData, catalog, schema, tableName);

        // If nothing found, try upper case
        if (allDeps.isEmpty()) {
            String upperTableName = tableName.toUpperCase();
            if (!upperTableName.equals(tableName)) {
                collectImportedKeys(allDeps, metaData, catalog, schema, upperTableName);
            }
        }

        // For some DBs (like Postgres or Oracle) catalog/schema handling in getImportedKeys can be tricky.
        // If still nothing found, try with null catalog and schema to be more permissive.
        if (allDeps.isEmpty() && (catalog != null || schema != null)) {
            collectImportedKeys(allDeps, metaData, null, null, tableName);
            if (allDeps.isEmpty()) {
                String upperTableName = tableName.toUpperCase();
                if (!upperTableName.equals(tableName)) {
                    collectImportedKeys(allDeps, metaData, null, null, upperTableName);
                }
            }
        }

        return allDeps;
    }

    private void collectImportedKeys(Set<String> allDeps, DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String pkTableName = rs.getString("PKTABLE_NAME");
                if (pkTableName != null) {
                    allDeps.add(pkTableName.toUpperCase());
                }
            }
        } catch (SQLException e) {
            // Some drivers might throw exception instead of returning empty result set if table not found or parameters are wrong
        }
    }

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        if ("blob".equals(expectedType)) {
            return rs.getBytes(columnIndex);
        } else if ("boolean".equals(expectedType)) {
            boolean value = rs.getBoolean(columnIndex);
            return rs.wasNull() ? null : value;
        } else if ("UUID".equals(expectedType)) {
            String columnValue = rs.getString(columnIndex);
            return columnValue == null ? null : UUID.fromString(columnValue);
        } else if ("LocalDate".equals(expectedType)) {
            java.sql.Date date = rs.getDate(columnIndex);
            return date == null ? null : date.toLocalDate();
        } else if ("Date".equals(expectedType)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.sql.Timestamp ts = rs.getTimestamp(columnIndex, cal);
            return ts == null ? null : new java.util.Date(ts.getTime());
        } else if ("Timestamp".equals(expectedType)) {
            return rs.getTimestamp(columnIndex);
        }

        int columnType = rs.getMetaData().getColumnType(columnIndex);
        return switch (columnType) {
            case Types.BLOB -> rs.getBytes(columnIndex);
            case Types.DATE -> {
                java.sql.Date date = rs.getDate(columnIndex);
                yield date == null ? null : date.toLocalDate();
            }
            case Types.TIMESTAMP -> rs.getTimestamp(columnIndex);
            default -> rs.getObject(columnIndex);
        };
    }

    protected JDBFeature getOrDefaultFeature(JDBFeature.Group featureGroup, JDBFeature defaultValue) {
        JDBFeature result = features.getOrDefault(featureGroup, defaultValue);
        return result;
    }
}
