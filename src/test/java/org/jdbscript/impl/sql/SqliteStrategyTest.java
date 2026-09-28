package org.jdbscript.impl.sql;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class SqliteStrategyTest {

    /**
     * Regression test: SQLite stores java.sql.Timestamp values as "yyyy-MM-dd HH:mm:ss[.SSS]" text
     * (24-hour clock) when not stored as epoch millis. Parsing that with a 12-hour ("hh") pattern -
     * which has no AM/PM marker in the string to resolve against - silently turns a noon timestamp
     * ("12:00:00") into midnight, since "12" on a 1-12 clock defaults to AM.
     */
    @Test
    public void getColumnValue_should_parse_noon_timestamp_correctly() throws Exception {
        SqliteStrategy strategy = new SqliteStrategy();
        ResultSet rs = stringResultSet("2024-06-01 12:00:00");

        Object value = strategy.getColumnValue(rs, 1, "Timestamp");

        assertThat(value).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) value).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2024, 6, 1, 12, 0, 0));
    }

    private static ResultSet stringResultSet(String value) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("getString".equals(method.getName())) {
                        return value;
                    }
                    return null;
                }
        );
    }
}
