package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class DataChangeListenerTest extends JdbAbstractTest {

    private static final String TABLE_NAME = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record id(int value);
        ITable1Record str_column_1(String value);
    }

    private interface ITestSchema extends IDBSchema {
        ITable1Record table_1();
    }

    private static abstract class SomeScript implements ITestSchema {{
        table_1().id(1).str_column_1("from class script");
    }}

    public static abstract class ScriptWithParamConstructor implements ITestSchema {
        public ScriptWithParamConstructor(String param) {
        }
    }

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME);
    }

    @Test
    public void test_insertDB_class_fires_listener_once() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.insertDB(SomeScript.class);

        assertThat(count).hasValue(1);
    }

    @Test
    public void test_insertDB_consumer_fires_listener_once() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.insertDB(db -> db.table_1().id(1).str_column_1("hello"));

        assertThat(count).hasValue(1);
    }

    @Test
    public void test_resetDB_class_fires_listener_once_not_twice() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.resetDB(SomeScript.class);

        assertThat(count).hasValue(1);
    }

    @Test
    public void test_resetDB_consumer_fires_listener_once_not_twice() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.resetDB(db -> db.table_1().id(1).str_column_1("hello"));

        assertThat(count).hasValue(1);
    }

    @Test
    public void test_cleanupDB_fires_listener_once() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.cleanupDB();

        assertThat(count).hasValue(1);
    }

    @Test
    public void test_multiple_listeners_fire_in_registration_order() {
        List<String> invocations = new ArrayList<>();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(() -> invocations.add("first"))
                .onDataChange(() -> invocations.add("second"))
                .build();

        engine.insertDB(db -> db.table_1().id(1).str_column_1("hello"));

        assertThat(invocations).containsExactly("first", "second");
    }

    @Test
    public void test_no_listeners_registered_does_not_throw() {
        IJDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

        assertThatCode(() -> engine.insertDB(db -> db.table_1().id(1).str_column_1("hello")))
                .doesNotThrowAnyException();
    }

    @Test
    public void test_assertDBHas_and_assertDBHasNot_do_not_fire_listener() {
        insertSeedRow(TABLE_NAME, "INSERT INTO " + TABLE_NAME + " (id, str_column_1) VALUES (1, 'seeded')");
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        engine.assertDBHas(db -> db.table_1().str_column_1("seeded"));
        engine.assertDBHasNot(db -> db.table_1().str_column_1("missing"));

        assertThat(count).hasValue(0);
    }

    @Test
    public void test_listener_not_invoked_when_operation_fails() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(count::incrementAndGet)
                .build();

        assertThatThrownBy(() -> engine.insertDB(db -> db.include(ScriptWithParamConstructor.class)))
                .isInstanceOf(JDBScriptException.class);

        assertThat(count).hasValue(0);
    }

    @Test
    public void test_listener_checked_exception_is_wrapped_in_JDBScriptException() {
        IOException cause = new IOException("cache unreachable");
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(() -> { throw cause; })
                .build();

        assertThatThrownBy(() -> engine.insertDB(db -> db.table_1().id(1).str_column_1("hello")))
                .isInstanceOf(JDBScriptException.class)
                .hasCause(cause);

        assertTableValues(table(TABLE_NAME,
                columns("str_column_1"),
                row("hello")
        ));
    }

    @Test
    public void test_listener_unchecked_exception_is_wrapped_in_JDBScriptException() {
        RuntimeException cause = new RuntimeException("boom");
        IJDBEngine<ITestSchema> engine = engineBuilder(ITestSchema.class)
                .onDataChange(() -> { throw cause; })
                .build();

        assertThatThrownBy(() -> engine.insertDB(db -> db.table_1().id(1).str_column_1("hello")))
                .isInstanceOf(JDBScriptException.class)
                .hasCause(cause);
    }

    @Test
    public void test_onDataChange_null_rejected_at_build_time() {
        JDBEngine.Builder<ITestSchema> builder = engineBuilder(ITestSchema.class);

        assertThatThrownBy(() -> builder.onDataChange(null))
                .isInstanceOf(JDBScriptException.class);
    }
}
