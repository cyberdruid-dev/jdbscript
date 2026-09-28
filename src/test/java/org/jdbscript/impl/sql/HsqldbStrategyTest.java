package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HsqldbStrategyTest {

    @Test
    public void sqlScriptExecutor_should_use_HsqldbStrategy_for_hsqldb_dbms() throws Exception {
        SqlScriptExecutor executor = new SqlScriptExecutor();
        executor.setDbmsType(DBMSType.HSQLDB);

        Field strategyField = SqlScriptExecutor.class.getDeclaredField("strategy");
        strategyField.setAccessible(true);
        Object strategy = strategyField.get(executor);

        assertThat(strategy).isInstanceOf(HsqldbStrategy.class);
    }

    @Test
    public void setUUID_should_set_null_varchar_when_null() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy(PreparedStatement.class);

        strategy.setUUID(stmt, 1, null);

        assertThat(recorder.calls).containsExactly("setNull(1, " + Types.VARCHAR + ")");
    }

    @Test
    public void setInputStream_should_set_null_blob_when_null() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy(PreparedStatement.class);

        strategy.setInputStream(stmt, 1, null);

        assertThat(recorder.calls).containsExactly("setNull(1, " + Types.BLOB + ")");
    }

    @Test
    public void resetSequences_should_reset_sequences() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        CallRecorder recorder = new CallRecorder();
        Connection cnn = recorder.createProxy(Connection.class);

        // Mock sequences
        recorder.addResultSetRow("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'", "SEQ1");
        recorder.addResultSetRow("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'", "SEQ2");

        strategy.resetSequences(cnn, List.of());

        assertThat(recorder.calls).contains(
                "executeUpdate(ALTER SEQUENCE SEQ1 RESTART WITH 10000)",
                "executeUpdate(ALTER SEQUENCE SEQ2 RESTART WITH 10000)"
        );
    }

    @Test
    public void resetSequences_should_fail_clearly_when_sequence_discovery_itself_fails() {
        // Same reasoning as DuckdbStrategyTest: an empty database still queries
        // INFORMATION_SCHEMA.SYSTEM_SEQUENCES successfully, returning zero rows - so this query
        // failing at all means something is actually wrong, not "no sequences". Swallowing it (the
        // prior behavior) meant any sequences in the schema would silently go unreset during cleanup.
        HsqldbStrategy strategy = new HsqldbStrategy();
        Connection cnn = failingConnection(sql -> {
            throw new SQLException("no such table: INFORMATION_SCHEMA.SYSTEM_SEQUENCES");
        }, sql -> 0);

        assertThatThrownBy(() -> strategy.resetSequences(cnn, List.of()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SYSTEM_SEQUENCES")
                .hasMessageContaining("no such table: INFORMATION_SCHEMA.SYSTEM_SEQUENCES");
    }

    @Test
    public void resetSequences_should_fail_clearly_when_resetting_a_specific_sequence_fails() {
        HsqldbStrategy strategy = new HsqldbStrategy();
        Connection cnn = failingConnection(
                sql -> listResultSet(List.of("SEQ1")),
                sql -> {
                    throw new SQLException("some low-level driver error");
                });

        assertThatThrownBy(() -> strategy.resetSequences(cnn, List.of()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SEQ1")
                .hasMessageContaining("some low-level driver error");
    }

    @Test
    public void resetSequences_should_reset_an_identity_column_via_alter_table() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sql.contains("SYSTEM_SEQUENCES") ? listResultSet(List.of())
                        : identityColumnResultSet(List.<String[]>of(new String[]{"GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"})),
                sql -> {
                    executed.add(sql);
                    return 0;
                });

        // Lowercase: engine-supplied tableNames don't match the catalog's casing, so this must be
        // case-insensitive.
        strategy.resetSequences(cnn, List.of("generated_int_id_table"));

        assertThat(executed).containsExactly(
                "ALTER TABLE GENERATED_INT_ID_TABLE ALTER COLUMN GENERATED_ID_COLUMN RESTART WITH 10000");
    }

    @Test
    public void resetSequences_should_not_reset_an_identity_column_outside_the_cleaned_tables() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sql.contains("SYSTEM_SEQUENCES") ? listResultSet(List.of())
                        : identityColumnResultSet(List.<String[]>of(new String[]{"GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"})),
                sql -> {
                    executed.add(sql);
                    return 0;
                });

        strategy.resetSequences(cnn, List.of("some_other_table"));

        assertThat(executed).isEmpty();
    }

    @Test
    public void resetSequences_should_fail_clearly_when_identity_column_discovery_itself_fails() {
        HsqldbStrategy strategy = new HsqldbStrategy();
        Connection cnn = failingConnection(sql -> {
            if (sql.contains("SYSTEM_SEQUENCES")) {
                return listResultSet(List.of());
            }
            throw new SQLException("no such table: INFORMATION_SCHEMA.COLUMNS");
        }, sql -> 0);

        assertThatThrownBy(() -> strategy.resetSequences(cnn, List.of()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("INFORMATION_SCHEMA.COLUMNS");
    }

    @Test
    public void resetSequences_should_fail_clearly_when_resetting_an_identity_column_fails() {
        HsqldbStrategy strategy = new HsqldbStrategy();
        Connection cnn = failingConnection(
                sql -> sql.contains("SYSTEM_SEQUENCES") ? listResultSet(List.of())
                        : identityColumnResultSet(List.<String[]>of(new String[]{"GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"})),
                sql -> {
                    throw new SQLException("some low-level driver error");
                });

        assertThatThrownBy(() -> strategy.resetSequences(cnn, List.of("generated_int_id_table")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("GENERATED_INT_ID_TABLE")
                .hasMessageContaining("GENERATED_ID_COLUMN")
                .hasMessageContaining("some low-level driver error");
    }

    private static ResultSet identityColumnResultSet(List<String[]> rows) {
        AtomicInteger index = new AtomicInteger(-1);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return index.incrementAndGet() < rows.size();
                        case "getString":
                            String[] row = rows.get(index.get());
                            return switch ((String) args[0]) {
                                case "TABLE_NAME" -> row[0];
                                case "COLUMN_NAME" -> row[1];
                                default -> throw new IllegalArgumentException("Unexpected column: " + args[0]);
                            };
                        case "close":
                            return null;
                    }
                    return null;
                }
        );
    }

    private interface QueryHandler {
        ResultSet executeQuery(String sql) throws SQLException;
    }

    private interface UpdateHandler {
        int executeUpdate(String sql) throws SQLException;
    }

    private static Connection failingConnection(QueryHandler queryHandler, UpdateHandler updateHandler) {
        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "executeQuery":
                            return queryHandler.executeQuery((String) args[0]);
                        case "executeUpdate":
                            return updateHandler.executeUpdate((String) args[0]);
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

    private static class CallRecorder implements InvocationHandler {
        private final List<String> calls = new ArrayList<>();
        private final List<Object[]> resultSetRows = new ArrayList<>();
        private String lastQuery = null;

        public void addResultSetRow(String query, Object... row) {
            this.lastQuery = query;
            this.resultSetRows.add(row);
        }

        public <T> T createProxy(Class<T> clazz) {
            return (T) Proxy.newProxyInstance(
                    clazz.getClassLoader(),
                    new Class<?>[]{clazz},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            if ("createStatement".equals(methodName)) {
                return createProxy(Statement.class);
            }

            if ("executeQuery".equals(methodName)) {
                calls.add(methodName + "(" + args[0] + ")");
                return createProxy(ResultSet.class);
            }

            if ("next".equals(methodName)) {
                return !resultSetRows.isEmpty();
            }

            if ("getString".equals(methodName)) {
                Object[] row = resultSetRows.remove(0);
                return row[0];
            }

            if ("close".equals(methodName)) {
                return null;
            }

            if ("executeUpdate".equals(methodName)) {
                StringBuilder sb = new StringBuilder(methodName).append("(");
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(args[i]);
                    }
                }
                sb.append(")");
                calls.add(sb.toString());
                return 0;
            }

            StringBuilder sb = new StringBuilder(methodName).append("(");
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(args[i]);
                }
            }
            sb.append(")");
            calls.add(sb.toString());
            return null;
        }
    }
}
