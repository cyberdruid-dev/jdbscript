package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class DefaultMethodsTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "table_with_defaults";

    public interface IDefaultMethodsRecord extends IDBRecord {
        IDefaultMethodsRecord int_column_1(Integer value);
        IDefaultMethodsRecord str_column_1(String value);

        // Type conversion
        default IDefaultMethodsRecord int_column_1(Instant value) {
            return int_column_1((int) value.getEpochSecond());
        }

        // Multi-column update
        default IDefaultMethodsRecord setBoth(Integer i, String s) {
            return int_column_1(i).str_column_1(s);
        }

        // defaults() calling other default methods
        default void defaults() {
            setBoth(100, "from-defaults");
        }
    }

    public interface IDefaultMethodsSchema extends IDBSchema {
        IDefaultMethodsRecord table_with_defaults();

        default IDefaultMethodsRecord table_with_both(Integer i, String s) {
            return table_with_defaults().setBoth(i, s);
        }
    }

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME);
    }

    private final JDBEngine<IDefaultMethodsSchema> engine = createEngine(IDefaultMethodsSchema.class);

    @Test
    public void type_conversion_should_work() {
        Instant now = Instant.ofEpochSecond(123456789);
        engine.resetDB(db -> {
            db.table_with_defaults().int_column_1(now);
        });

        assertTableValues(table(TABLE_NAME,
                columns("int_column_1"),
                row(123456789)
        ));
    }

    @Test
    public void multi_column_update_should_work() {
        engine.resetDB(db -> {
            db.table_with_defaults().setBoth(42, "hello");
        });

        assertTableValues(table(TABLE_NAME,
                columns("int_column_1", "str_column_1"),
                row(42, "hello")
        ));
    }

    @Test
    public void schema_default_method_should_work() {
        engine.resetDB(db -> {
            db.table_with_both(99, "schema-default");
        });

        assertTableValues(table(TABLE_NAME,
                columns("int_column_1", "str_column_1"),
                row(99, "schema-default")
        ));
    }

    @Test
    public void defaults_calling_other_default_methods_should_work() {
        engine.resetDB(db -> {
            db.table_with_defaults();
        });

        assertTableValues(table(TABLE_NAME,
                columns("int_column_1", "str_column_1"),
                row(100, "from-defaults")
        ));
    }

    @Test
    public void object_methods_should_work_on_record_proxy() {
        engine.resetDB(db -> {
            IDefaultMethodsRecord record = db.table_with_defaults();
            assertThat(record.toString()).contains("table_with_defaults");
            assertThat(record.hashCode()).isNotZero();
            assertThat(record.equals(record)).isTrue();
            assertThat(record.equals("not a record")).isFalse();
        });
    }

    @Test
    public void object_methods_should_work_on_schema_proxy() {
        engine.resetDB(proxy -> {
            assertThat(proxy.toString()).contains("IDefaultMethodsSchema");
            assertThat(proxy.hashCode()).isNotZero();
            assertThat(proxy.equals(proxy)).isTrue();
        });
    }
}
