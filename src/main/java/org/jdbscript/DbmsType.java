package org.jdbscript;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Supported database management systems detected by JDBC URL prefix.
 */
public enum DbmsType {
    /** Unknown or unsupported database type. */
    UNKNOWN("jdbc:UNKNOWN:"),
    /** MySQL database. */
    MYSQL("jdbc:mysql:"),
    /** MariaDB database. */
    MARIADB("jdbc:mariadb"),
    /** PostgreSQL database. */
    POSTGRESQL("jdbc:postgresql"),
    /** Microsoft SQL Server. */
    MSSQL("jdbc:sqlserver"),
    /** Oracle database. */
    ORACLE("jdbc:oracle"),
    /** H2 in-memory/embedded database. */
    H2("jdbc:h2"),
    /** HSQLDB embedded database. */
    HSQLDB("jdbc:hsqldb"),
    /** IBM DB2 database. */
    DB2("jdbc:db2"),
    /** CockroachDB database. */
    COCKROACHDB("jdbc:cockroachdb"),
    /** DuckDB database. */
    DUCKDB("jdbc:duckdb:"),
    /** SQLite database. */
    SQLITE("jdbc:sqlite");

    private final String urlStart;

    DbmsType(String urlStart) {
        this.urlStart = urlStart;
    }

    /**
     * Detects the {@link DbmsType} based on the JDBC connection metadata.
     *
     * @param metaData the JDBC database metadata
     * @return the matching {@link DbmsType}, or {@link #UNKNOWN} if not recognized
     */
    public static DbmsType getType(DatabaseMetaData metaData) {
        try {
            String url = metaData.getURL();
            DbmsType type = getTypeFromUrl(url);

            if (type == POSTGRESQL) {
                String productName = metaData.getDatabaseProductName();
                if ("CockroachDB".equalsIgnoreCase(productName)) {
                    return COCKROACHDB;
                }

                // CockroachDB often identifies itself as PostgreSQL for compatibility.
                // We check the version string to be sure.
                try {
                    Connection cnn = metaData.getConnection();
                    if (cnn != null) {
                        try (Statement stmt = cnn.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT version()")) {
                            if (rs.next()) {
                                String version = rs.getString(1);
                                if (version != null && version.contains("CockroachDB")) {
                                    return COCKROACHDB;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Ignore and fall back to detected type
                }
            }
            return type;
        } catch (SQLException e) {
            return UNKNOWN;
        }
    }

    /**
     * Detects the {@link DbmsType} based on the JDBC connection URL.
     *
     * @param url the JDBC connection URL
     * @return the matching {@link DbmsType}, or {@link #UNKNOWN} if not recognized
     */
    public static DbmsType getTypeFromUrl(String url) {
        DbmsType result = UNKNOWN;
        if (url == null) {
            return result;
        }
        for (var type : DbmsType.values()) {
            if (url.startsWith(type.urlStart)) {
                result = type;
                break;
            }
        }
        return result;
    }

}
