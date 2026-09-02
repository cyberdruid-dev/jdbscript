package org.jdbscript.impl.cache;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.jdbscript.impl.Utils.isBlank;
import static org.jdbscript.impl.Utils.nullToEmpty;

public record DataBaseCacheKey(String jdbcUrl, String username, String schema) {

    public static DataBaseCacheKey from(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        String url = nullToEmpty(metaData.getURL());
        url = sanitizeUrl(url);
        String userName = nullToEmpty(metaData.getUserName());
        String schema = nullToEmpty(safeGetCatalog(connection));
        if(isBlank(schema)) {
            schema = nullToEmpty(safeGetSchema(connection));
        }

        return new DataBaseCacheKey(url, userName, schema);
    }

    private static String safeGetCatalog(Connection connection) {
        try {
            return connection.getCatalog();
        } catch (SQLException ignored) {
            return "";
        }
    }

    private static String safeGetSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException ignored) {
            return "";
        }
    }

    public static String sanitizeUrl(String rawUrl) {
        if (isBlank(rawUrl)) {
            return "";
        }

        // Cut off query parameters starting with '?' (Postgres, MySQL, SQLite, etc.)
        int queryIdx = rawUrl.indexOf('?');
        if (queryIdx != -1) {
            rawUrl = rawUrl.substring(0, queryIdx);
        }

        // Cut off parameter options starting with ';' (H2, Oracle, SQL Server)
        int semicolonIdx = rawUrl.indexOf(';');
        if (semicolonIdx != -1) {
            rawUrl = rawUrl.substring(0, semicolonIdx);
        }

        return rawUrl.trim();
    }
}
