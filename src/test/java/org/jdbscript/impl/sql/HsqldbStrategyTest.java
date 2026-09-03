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

import static org.assertj.core.api.Assertions.assertThat;

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
    public void afterInsert_should_reset_sequences() throws SQLException {
        HsqldbStrategy strategy = new HsqldbStrategy();
        CallRecorder recorder = new CallRecorder();
        Connection cnn = recorder.createProxy(Connection.class);
        
        // Mock sequences
        recorder.addResultSetRow("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'", "SEQ1");
        recorder.addResultSetRow("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SYSTEM_SEQUENCES WHERE SEQUENCE_SCHEMA = 'PUBLIC'", "SEQ2");

        strategy.afterInsert(cnn);

        assertThat(recorder.calls).contains(
                "executeUpdate(ALTER SEQUENCE SEQ1 RESTART WITH 10000)",
                "executeUpdate(ALTER SEQUENCE SEQ2 RESTART WITH 10000)"
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
