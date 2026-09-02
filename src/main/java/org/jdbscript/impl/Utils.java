package org.jdbscript.impl;

public class Utils {

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
