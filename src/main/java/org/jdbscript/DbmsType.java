package org.jdbscript;

public enum DbmsType {
    UNKNOWN("jdbc:UNKNOWN:"),
    MYSQL("jdbc:mysql:"),
    MARIADB("jdbc:mariadb"),
    POSTGRESQL("jdbc:postgresql"),
    MSSQL("jdbc:sqlserver"),
    ORACLE("jdbc:oracle"),
    H2("jdbc:h2"),
    HSQLDB("jdbc:hsqldb"),
    DB2("jdbc:db2"),
    SQLITE("jdbc:sqlite");
    private String urlStart;

    DbmsType(String urlStart) {
        this.urlStart = urlStart;
    }

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
