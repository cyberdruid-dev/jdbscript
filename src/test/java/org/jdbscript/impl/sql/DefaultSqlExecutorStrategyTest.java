package org.jdbscript.impl.sql;

import org.testng.annotations.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultSqlExecutorStrategyTest {

    @Test
    public void getColumnValue_should_return_null_for_a_null_boolean_column() throws SQLException {
        // rs.getBoolean() returns false (not null) for a SQL NULL - the only way to tell the two
        // apart is rs.wasNull() right after. Every other branch of getColumnValue() uses a
        // reference-typed getter (getString/getDate/getBytes) that already returns null on its own;
        // "boolean" is the only branch that needs, and was missing, that wasNull() check.
        DefaultSqlExecutorStrategy strategy = new DefaultSqlExecutorStrategy();
        ResultSet rs = booleanResultSet(false, true);

        Object value = strategy.getColumnValue(rs, 1, "boolean");

        assertThat(value).isNull();
    }

    @Test
    public void getColumnValue_should_return_false_for_a_non_null_false_boolean_column() throws SQLException {
        DefaultSqlExecutorStrategy strategy = new DefaultSqlExecutorStrategy();
        ResultSet rs = booleanResultSet(false, false);

        Object value = strategy.getColumnValue(rs, 1, "boolean");

        assertThat(value).isEqualTo(false);
    }

    @Test
    public void getColumnValue_should_return_true_for_a_non_null_true_boolean_column() throws SQLException {
        DefaultSqlExecutorStrategy strategy = new DefaultSqlExecutorStrategy();
        ResultSet rs = booleanResultSet(true, false);

        Object value = strategy.getColumnValue(rs, 1, "boolean");

        assertThat(value).isEqualTo(true);
    }

    private static ResultSet booleanResultSet(boolean getBooleanReturn, boolean wasNull) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBoolean":
                            return getBooleanReturn;
                        case "wasNull":
                            return wasNull;
                    }
                    return null;
                }
        );
    }
}
