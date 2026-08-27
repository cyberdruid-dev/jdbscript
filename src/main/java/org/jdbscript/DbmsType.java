package org.jdbscript;

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
    /** SQLite database. */
    SQLITE("jdbc:sqlite");

    private final String urlStart;

    DbmsType(String urlStart) {
        this.urlStart = urlStart;
    }

    /**
     * Detects the {@link DbmsType} based on the JDBC connection URL.
     *
     * @param url the JDBC connection URL
     * @return the matching {@link DbmsType}, or {@link #UNKNOWN} if not recognized
     */
    public static DbmsType getTypeFromUrl(String url) {
        DbmsType result = UNKNOWN;
        for (var type : DbmsType.values()) {
            if (url.startsWith(type.urlStart)) {
                result = type;
                break;
            }
        }
        return result;
    }

}
