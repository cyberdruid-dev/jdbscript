package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.jdbscript.JDBFeature;
import org.jdbscript.JDBFeatureSet;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Db2StrategyTest {

    @Test
    public void sqlScriptExecutor_should_use_Db2Strategy_for_db2_dbms() throws Exception {
        SqlScriptExecutor executor = new SqlScriptExecutor();
        executor.setDbmsType(DBMSType.DB2);

        Field strategyField = SqlScriptExecutor.class.getDeclaredField("strategy");
        strategyField.setAccessible(true);
        Object strategy = strategyField.get(executor);

        assertThat(strategy).isInstanceOf(Db2Strategy.class);
    }

    @Test
    public void setInputStream_should_set_null_blob_when_null() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy();

        strategy.setInputStream(stmt, 1, null);

        assertThat(recorder.calls).containsExactly("setNull(1, " + Types.BLOB + ")");
    }

    @Test
    public void setInputStream_should_set_blob_when_not_null() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy();
        InputStream in = new ByteArrayInputStream(new byte[]{1, 2, 3});

        strategy.setInputStream(stmt, 1, in);

        assertThat(recorder.calls).containsExactly("setBlob(1, " + in + ")");
    }

    @Test
    public void setByteArray_should_set_null_blob_when_null() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy();

        strategy.setByteArray(stmt, 2, null);

        assertThat(recorder.calls).containsExactly("setNull(2, " + Types.BLOB + ")");
    }

    @Test
    public void setByteArray_should_set_blob_stream_when_not_null() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy();
        byte[] bytes = new byte[]{1, 2, 3};

        strategy.setByteArray(stmt, 2, bytes);

        assertThat(recorder.calls).hasSize(1);
        assertThat(recorder.calls.get(0)).startsWith("setBlob(2, ");
    }

    @Test
    public void setUUID_should_set_string() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        PreparedStatement stmt = recorder.createProxy();
        UUID uuid = UUID.fromString("00000011-0012-0013-0014-000000000015");

        strategy.setUUID(stmt, 3, uuid);

        assertThat(recorder.calls).containsExactly("setString(3, " + uuid + ")");
    }

    @Test
    public void setObject_should_set_date_when_target_is_date_and_value_is_local_date() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.setParameterType(1, Types.DATE);
        PreparedStatement stmt = recorder.createProxy();
        LocalDate localDate = LocalDate.of(2024, 8, 6);

        strategy.setObject(stmt, 1, localDate);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setDate(1, " + java.sql.Date.valueOf(localDate) + ")"
        );
    }

    @Test
    public void setObject_should_set_date_when_target_is_date_and_value_is_timestamp() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.setParameterType(1, Types.DATE);
        PreparedStatement stmt = recorder.createProxy();
        Timestamp timestamp = new Timestamp(1000000000L);

        strategy.setObject(stmt, 1, timestamp);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setDate(1, " + new java.sql.Date(timestamp.getTime()) + ")"
        );
    }

    @Test
    public void setObject_should_set_date_when_target_is_date_and_value_is_util_date() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.setParameterType(1, Types.DATE);
        PreparedStatement stmt = recorder.createProxy();
        Date date = new Date(1000000000L);

        strategy.setObject(stmt, 1, date);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setDate(1, " + new java.sql.Date(date.getTime()) + ")"
        );
    }

    @Test
    public void setObject_should_set_timestamp_when_target_is_timestamp_and_value_is_util_date() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.setParameterType(1, Types.TIMESTAMP);
        PreparedStatement stmt = recorder.createProxy();
        Date date = new Date(1000000000L);

        strategy.setObject(stmt, 1, date);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setTimestamp(1, " + new Timestamp(date.getTime()) + ")"
        );
    }

    @Test
    public void setObject_should_set_null_with_parameter_type_when_value_is_null() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.setParameterType(1, Types.DATE);
        PreparedStatement stmt = recorder.createProxy();

        strategy.setObject(stmt, 1, null);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setNull(1, " + Types.DATE + ")"
        );
    }

    @Test
    public void setObject_should_set_date_for_local_date_when_no_metadata() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.failMetadata = true;
        PreparedStatement stmt = recorder.createProxy();
        LocalDate localDate = LocalDate.of(2024, 8, 6);

        strategy.setObject(stmt, 1, localDate);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setDate(1, " + java.sql.Date.valueOf(localDate) + ")"
        );
    }

    @Test
    public void setObject_should_set_timestamp_for_instant_when_no_metadata() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.failMetadata = true;
        PreparedStatement stmt = recorder.createProxy();
        Instant instant = Instant.now();

        strategy.setObject(stmt, 1, instant);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setTimestamp(1, " + Timestamp.from(instant) + ")"
        );
    }

    @Test
    public void setObject_should_set_timestamp_for_util_date_when_no_metadata() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        CallRecorder recorder = new CallRecorder();
        recorder.failMetadata = true;
        PreparedStatement stmt = recorder.createProxy();
        Date date = new Date();

        strategy.setObject(stmt, 1, date);

        assertThat(recorder.calls).containsExactly(
                "getParameterMetaData()",
                "setTimestamp(1, " + new Timestamp(date.getTime()) + ")"
        );
    }

    @Test
    public void afterInsert_should_fail_clearly_when_sequence_discovery_itself_fails() {
        // Same reasoning as DuckdbStrategyTest/HsqldbStrategyTest: an empty database still queries
        // SYSCAT.SEQUENCES successfully, returning zero rows - so this query failing at all means
        // something is actually wrong, not "no sequences". Swallowing it (the prior behavior) meant
        // any sequences in the schema would silently go unreset after insert.
        Db2Strategy strategy = new Db2Strategy();
        Connection cnn = failingConnection(sql -> {
            throw new SQLException("no such table: SYSCAT.SEQUENCES");
        }, sql -> 0);

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SYSCAT.SEQUENCES")
                .hasMessageContaining("no such table: SYSCAT.SEQUENCES");
    }

    @Test
    public void afterInsert_should_fail_clearly_when_resetting_a_regular_sequence_fails() {
        Db2Strategy strategy = new Db2Strategy();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.regular("SEQ1"))),
                sql -> {
                    throw new SQLException("some low-level driver error");
                });

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SEQ1")
                .hasMessageContaining("some low-level driver error");
    }

    @Test
    public void afterInsert_should_reset_a_regular_sequence_via_alter_sequence() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.regular("SEQ1"))),
                recording(executed));

        strategy.afterInsert(cnn);

        assertThat(executed).containsExactly("ALTER SEQUENCE SEQ1 RESTART WITH 10000");
    }

    @Test
    public void afterInsert_with_no_feature_set_should_fail_clearly_for_an_identity_owned_sequence() {
        // DB2_ID_OWNED_SEQUENCE_ERROR is the default - see JDBFeature - so leaving this
        // unconfigured must fail loudly rather than silently leave the identity column unreset,
        // the same way an unset feature never silently degrades to "do nothing" anywhere else.
        Db2Strategy strategy = new Db2Strategy();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.identityOwned("SQL123", "GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"))),
                sql -> 0);

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("GENERATED_INT_ID_TABLE")
                .hasMessageContaining("GENERATED_ID_COLUMN")
                .hasMessageContaining("JDBFeature");
    }

    @Test
    public void afterInsert_with_NOT_MODIFIED_feature_should_leave_identity_owned_sequence_untouched() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        strategy.setFeatures(JDBFeatureSet.of(JDBFeature.DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED));
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.identityOwned("SQL123", "GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"))),
                recording(executed));

        strategy.afterInsert(cnn);

        assertThat(executed).isEmpty();
    }

    @Test
    public void afterInsert_with_RESTART_WITH_feature_should_alter_table_for_identity_owned_sequence() throws SQLException {
        Db2Strategy strategy = new Db2Strategy();
        strategy.setFeatures(JDBFeatureSet.of(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH));
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.identityOwned("SQL123", "GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"))),
                recording(executed));

        strategy.afterInsert(cnn);

        assertThat(executed).containsExactly(
                "ALTER TABLE GENERATED_INT_ID_TABLE ALTER COLUMN GENERATED_ID_COLUMN RESTART WITH 10000");
    }

    @Test
    public void afterInsert_with_RESTART_WITH_feature_should_fail_clearly_when_the_alter_table_itself_fails() {
        Db2Strategy strategy = new Db2Strategy();
        strategy.setFeatures(JDBFeatureSet.of(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH));
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.identityOwned("SQL123", "GENERATED_INT_ID_TABLE", "GENERATED_ID_COLUMN"))),
                sql -> {
                    throw new SQLException("some low-level driver error");
                });

        assertThatThrownBy(() -> strategy.afterInsert(cnn))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("GENERATED_INT_ID_TABLE")
                .hasMessageContaining("GENERATED_ID_COLUMN")
                .hasMessageContaining("some low-level driver error");
    }

    @Test
    public void afterInsert_should_reset_a_regular_sequence_regardless_of_the_identity_feature() throws SQLException {
        // A regular, user-created sequence is unaffected by the DB2_ID_OWNED_SEQUENCE_* feature -
        // that feature only governs sequences DB2 itself created for an identity column.
        Db2Strategy strategy = new Db2Strategy();
        strategy.setFeatures(JDBFeatureSet.of(JDBFeature.DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED));
        List<String> executed = new ArrayList<>();
        Connection cnn = failingConnection(
                sql -> sequenceResultSet(List.of(SeqRow.regular("SEQ1"))),
                recording(executed));

        strategy.afterInsert(cnn);

        assertThat(executed).containsExactly("ALTER SEQUENCE SEQ1 RESTART WITH 10000");
    }

    private interface QueryHandler {
        ResultSet executeQuery(String sql) throws SQLException;
    }

    private interface UpdateHandler {
        int executeUpdate(String sql) throws SQLException;
    }

    private static UpdateHandler recording(List<String> executed) {
        return sql -> {
            executed.add(sql);
            return 0;
        };
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

    /** A row of the SYSCAT.SEQUENCES/SYSCAT.COLIDENTATTRIBUTES join Db2Strategy queries. */
    private record SeqRow(String name, String seqType, String tableName, String columnName) {
        static SeqRow regular(String name) {
            return new SeqRow(name, "S", null, null);
        }

        static SeqRow identityOwned(String name, String tableName, String columnName) {
            return new SeqRow(name, "I", tableName, columnName);
        }
    }

    private static ResultSet sequenceResultSet(List<SeqRow> rows) {
        AtomicInteger index = new AtomicInteger(-1);
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return index.incrementAndGet() < rows.size();
                        case "getString":
                            SeqRow row = rows.get(index.get());
                            return switch ((String) args[0]) {
                                case "SEQNAME" -> row.name();
                                case "SEQTYPE" -> row.seqType();
                                case "TABNAME" -> row.tableName();
                                case "COLNAME" -> row.columnName();
                                default -> throw new IllegalArgumentException("Unexpected column: " + args[0]);
                            };
                        case "close":
                            return null;
                    }
                    return null;
                }
        );
    }

    private static class CallRecorder implements InvocationHandler {
        private final List<String> calls = new ArrayList<>();
        private int parameterType = Types.OTHER;
        private boolean failMetadata = false;

        public void setParameterType(int columnIndex, int type) {
            this.parameterType = type;
        }

        public PreparedStatement createProxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getParameterMetaData".equals(method.getName())) {
                calls.add("getParameterMetaData()");
                if (failMetadata) {
                    throw new SQLException("Metadata not available");
                }
                return Proxy.newProxyInstance(
                        ParameterMetaData.class.getClassLoader(),
                        new Class<?>[]{ParameterMetaData.class},
                        (metaProxy, metaMethod, metaArgs) -> {
                            if ("getParameterType".equals(metaMethod.getName())) {
                                return parameterType;
                            }
                            return null;
                        }
                );
            }

            StringBuilder sb = new StringBuilder(method.getName()).append("(");
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
