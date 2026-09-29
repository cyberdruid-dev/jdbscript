package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.RecordTools;
import org.jdbscript.db.ITestDBSchema;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class UpdateDBTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";
    private final static String TABLE_NAME_2 = "table_2";
    private final static String TABLE_WITH_DEFAULTS = "table_with_defaults";
    private final static String COMPOSITE_PK_TABLE = "composite_pk_table";
    private final static String NO_PK_TABLE = "no_pk_table";

    private interface ICompositePkRecord extends IDBRecord {
        ICompositePkRecord key_1(int value);
        ICompositePkRecord key_2(String value);
        ICompositePkRecord value_column(String value);
    }
    private interface ITestSchema extends ITestDBSchema {
        ICompositePkRecord composite_pk_table();
    }

    public interface ITableWithDefaultsRecord extends IDBRecord {
        ITableWithDefaultsRecord id(int value);
        ITableWithDefaultsRecord int_column_1(Integer value);
        ITableWithDefaultsRecord str_column_1(String value);
        default void defaults(RecordTools tools) {
            int_column_1(10);
            str_column_1("default value");
        }
    }
    private interface IDefaultsTestSchema extends IDBSchema {
        ITableWithDefaultsRecord table_with_defaults();
    }

    private interface INoPkRecord extends IDBRecord {
        INoPkRecord id(int value);
        INoPkRecord value_column(String value);
    }
    private interface INoPkSchema extends IDBSchema {
        INoPkRecord no_pk_table();
    }

    private enum Status { ACTIVE, DISABLED }

    private interface IStatusRecord extends IDBRecord {
        IStatusRecord id(int value);
        IStatusRecord str_column_1(Status value);
    }
    private interface IStatusSchema extends IDBSchema {
        IStatusRecord table_1();
    }

    private static class StatusConverter implements IJDBTypeConverter {
        @Override
        public boolean canConvert(Object value) {
            return value instanceof Status;
        }

        @Override
        public Object convert(Object value) {
            return "db_" + ((Status) value).name().toLowerCase();
        }
    }

    public static abstract class UpdateScript implements ITestSchema {{
        table_1().id(1).str_column_1("from class");
    }}

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);
    private final JDBEngine<IDefaultsTestSchema> defaultsEngine = createEngine(IDefaultsTestSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME_1, TABLE_NAME_2, TABLE_WITH_DEFAULTS, COMPOSITE_PK_TABLE);
    }

    public void update_should_change_set_column_and_keep_unset_ones() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one").str_column_2("two"));

        engine.updateDB(db -> db.table_1().id(1).str_column_1("changed"));

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1", "str_column_2"),
                row(1, "changed", "two")
        ));
    }

    public void update_should_change_only_the_matched_row() {
        engine.resetDB(db -> {
            db.table_1().id(1).str_column_1("one");
            db.table_1().id(2).str_column_1("two");
        });

        engine.updateDB(db -> db.table_1().id(1).str_column_1("changed"));

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "changed"),
                row(2, "two")
        ));
    }

    public void update_should_change_several_columns_of_one_row() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one").str_column_2("two").int_column_1(1));

        engine.updateDB(db -> db.table_1().id(1).str_column_1("changed 1").int_column_1(42));

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1", "str_column_2", "int_column_1"),
                row(1, "changed 1", "two", 42)
        ));
    }

    public void update_should_apply_every_record_of_the_script() {
        engine.resetDB(db -> {
            db.table_1().id(1).str_column_1("one");
            db.table_1().id(2).str_column_1("two");
            db.table_2().id(1).int_column_1(1);
        });

        engine.updateDB(db -> {
            db.table_1().id(1).str_column_1("changed one");
            db.table_1().id(2).str_column_1("changed two");
            db.table_2().id(1).int_column_1(42);
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "changed one"),
                row(2, "changed two")
        ));
        assertTableValues(table(TABLE_NAME_2,
                columns("id", "int_column_1"),
                row(1, 42)
        ));
    }

    public void update_should_set_column_to_null_when_set_to_null() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one").int_column_1(1));

        engine.updateDB(db -> db.table_1().id(1).str_column_1(null).int_column_1(null));

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1", "int_column_1"),
                row(1, null, null)
        ));
    }

    public void update_should_not_apply_defaults() {
        defaultsEngine.resetDB(db -> db.table_with_defaults().id(1).int_column_1(1).str_column_1("custom"));

        defaultsEngine.updateDB(db -> db.table_with_defaults().id(1).int_column_1(2));

        assertTableValues(table(TABLE_WITH_DEFAULTS,
                columns("id", "int_column_1", "str_column_1"),
                row(1, 2, "custom")
        ));
    }

    public void update_should_match_all_columns_of_a_composite_primary_key() {
        engine.resetDB(db -> {
            db.composite_pk_table().key_1(1).key_2("a").value_column("1a");
            db.composite_pk_table().key_1(1).key_2("b").value_column("1b");
            db.composite_pk_table().key_1(2).key_2("a").value_column("2a");
        });

        engine.updateDB(db -> db.composite_pk_table().key_1(1).key_2("b").value_column("changed"));

        assertTableValues(table(COMPOSITE_PK_TABLE,
                columns("key_1", "key_2", "value_column"),
                row(1, "a", "1a"),
                row(1, "b", "changed"),
                row(2, "a", "2a")
        ));
    }

    public void update_should_convert_set_values_with_registered_converters() {
        JDBEngine<IStatusSchema> statusEngine = engineBuilder(IStatusSchema.class)
                .disableDefaultConverters()
                .converter(new StatusConverter())
                .build();
        statusEngine.resetDB(db -> db.table_1().id(1).str_column_1(Status.ACTIVE));

        statusEngine.updateDB(db -> db.table_1().id(1).str_column_1(Status.DISABLED));

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "db_disabled")
        ));
    }

    public void update_should_run_a_class_script() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one"));

        engine.updateDB(UpdateScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "from class")
        ));
    }

    public void update_should_apply_included_scripts() {
        engine.resetDB(db -> {
            db.table_1().id(1).str_column_1("one");
            db.table_1().id(2).str_column_1("two");
        });

        engine.updateDB(db -> {
            db.include(UpdateScript.class);
            db.table_1().id(2).str_column_1("from lambda");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "from class"),
                row(2, "from lambda")
        ));
    }

    public void update_with_empty_script_should_change_nothing() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one"));

        engine.updateDB(db -> {});

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "one")
        ));
    }

    public void update_should_fail_when_no_row_matches() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one"));

        assertThatThrownBy(() -> engine.updateDB(db -> db.table_1().id(99).str_column_1("changed")))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("table_1")
                .hasMessageContaining("id=99");
    }

    public void failed_update_should_roll_back_earlier_updates_of_the_same_call() {
        skipFor("rollback of a failed updateDB", DBMSType.DUCKDB, null, "runs in auto-commit mode");
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one"));

        assertThatThrownBy(() -> engine.updateDB(db -> {
            db.table_1().id(1).str_column_1("changed");
            db.table_1().id(99).str_column_1("changed");
        })).isInstanceOf(JDBScriptException.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "one")
        ));
    }

    public void update_should_fail_for_a_table_without_primary_key() {
        skipFor("table without primary key", DBMSType.SPANNER, null, "every Spanner table has a primary key");
        cleanupTables(NO_PK_TABLE);
        executeUpdate("INSERT INTO no_pk_table (id, value_column) VALUES (1, 'one')");
        JDBEngine<INoPkSchema> noPkEngine = createEngine(INoPkSchema.class);

        assertThatThrownBy(() -> noPkEngine.updateDB(db -> db.no_pk_table().id(1).value_column("changed")))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("no_pk_table")
                .hasMessageContaining("primary key");
    }

    public void update_should_fail_when_not_all_primary_key_columns_are_set() {
        engine.resetDB(db -> db.composite_pk_table().key_1(1).key_2("a").value_column("1a"));

        assertThatThrownBy(() -> engine.updateDB(db -> db.composite_pk_table().key_1(1).value_column("changed")))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("composite_pk_table")
                .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("key_2"));
    }

    public void update_should_fail_when_only_primary_key_columns_are_set() {
        engine.resetDB(db -> db.table_1().id(1).str_column_1("one"));

        assertThatThrownBy(() -> engine.updateDB(db -> db.table_1().id(1)))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("table_1")
                .hasMessageContaining("nothing to update");
    }
}
