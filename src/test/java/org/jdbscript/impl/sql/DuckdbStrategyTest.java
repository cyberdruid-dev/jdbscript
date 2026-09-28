package org.jdbscript.impl.sql;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DuckdbStrategyTest {

    private interface QueryHandler {
        ResultSet executeQuery(String sql) throws SQLException;
    }

    @Test
    public void afterInsert_should_fail_clearly_when_sequence_discovery_itself_fails() {
        // An empty database (zero sequences) still queries pg_catalog.pg_sequences successfully,
        // returning zero rows - verified against the real DuckDB driver. So this query failing at
        // all means the view itself is unavailable (e.g. an older duckdb_jdbc driver), not "no
        // sequences" - and if the schema actually uses sequences, they'd go silently unreset. That
        // must surface loudly, not be treated as "nothing to do".
        DuckdbStrategy strategy = new DuckdbStrategy();
        Connection cnn = connection(sql -> {
            throw new SQLException("no such table: pg_catalog.pg_sequences");
        });

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("pg_catalog.pg_sequences")
                .hasMessageContaining("no such table: pg_catalog.pg_sequences");
    }

    @Test
    public void afterInsert_should_fail_clearly_when_resetting_a_specific_sequence_fails() {
        DuckdbStrategy strategy = new DuckdbStrategy();
        Connection cnn = connection(sql -> {
            if (sql.contains("pg_sequences")) {
                return listResultSet(List.of("SEQ1"));
            }
            throw new SQLException("some low-level driver error");
        });

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SEQ1")
                .hasMessageContaining("some low-level driver error");
    }

    private static Connection connection(QueryHandler queryHandler) {
        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "executeQuery":
                            return queryHandler.executeQuery((String) args[0]);
                        case "close":
                            return null;
                    }
                    return null;
                }
        );
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        return statement;
                    }
                    return null;
                }
        );
    }

    private static ResultSet listResultSet(List<String> values) {
        AtomicInteger index = new AtomicInteger(-1);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return index.incrementAndGet() < values.size();
                        case "getString":
                            return values.get(index.get());
                        case "close":
                            return null;
                    }
                    return null;
                }
        );
    }
}
