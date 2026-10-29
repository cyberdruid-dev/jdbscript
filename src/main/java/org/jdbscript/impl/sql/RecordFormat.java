package org.jdbscript.impl.sql;

import org.jdbscript.impl.TypedNull;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.util.Date;
import java.util.Map;

import static java.util.stream.Collectors.joining;

final class RecordFormat {
    private static final int MAX_STRING_LENGTH = 100;

    private RecordFormat() {
    }

    static String describe(Map<String, ?> columns) {
        return columns.entrySet().stream()
                .map(e -> e.getKey() + "=" + format(e.getValue()))
                .collect(joining(", "));
    }

    static String format(Object value) {
        if (value == null || value instanceof TypedNull) {
            return "null";
        }
        if (value instanceof String s) {
            return s.length() <= MAX_STRING_LENGTH
                    ? "'" + s + "'"
                    : "'" + s.substring(0, MAX_STRING_LENGTH) + "...' (" + s.length() + " chars)";
        }
        if (value instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }
        if (value instanceof InputStream) {
            return "InputStream";
        }
        if (value instanceof Blob) {
            return "BLOB";
        }
        if (value instanceof Clob) {
            return "CLOB";
        }
        // java.sql.Date/Time/Timestamp have readable toString()s; only a plain Date prints its time zone and weekday.
        if (value.getClass() == Date.class) {
            return ((Date) value).toInstant().toString();
        }
        return value.toString();
    }
}
