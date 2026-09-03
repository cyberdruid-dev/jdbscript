package org.jdbscript.impl.cache;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.jdbscript.impl.Utils.isBlank;
import static org.jdbscript.impl.Utils.nullToEmpty;

record DatabaseCacheKey(String jdbcUrl, String username, String catalog, String schema) {

    public static DatabaseCacheKey from(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        String url = nullToEmpty(metaData.getURL());
        url = sanitizeUrl(url);
        String userName = nullToEmpty(metaData.getUserName());
        // Capture both: some DBMSes only distinguish tenants by catalog (MySQL, SQL Server), others
        // only by schema (Oracle, Postgres search_path) - using one as a fallback for the other
        // (instead of both) let same-catalog/different-schema connections collide onto one cache key.
        String catalog = nullToEmpty(safeGetCatalog(connection));
        String schema = nullToEmpty(safeGetSchema(connection));

        return new DatabaseCacheKey(url, userName, catalog, schema);
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
