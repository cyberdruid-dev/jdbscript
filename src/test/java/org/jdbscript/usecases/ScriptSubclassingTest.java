package org.jdbscript.usecases;

import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.jdbscript.errors.JDBScriptException;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

@Test
public class ScriptSubclassingTest extends JdbAbstractTest {
    protected static final Logger log = LoggerFactory.getLogger(ScriptSubclassingTest.class);

    private final static String TABLE_NAME_1 = "table_1";

    public static abstract class TestDbScriptClass implements ITestDBSchema {{
        log.debug("Start: {}", this.getClass());
        table_1().id(1).str_column_1("one").str_column_2("two");
        table_1().id(2).str_column_1("three").str_column_2("four");
    }};

    // PrivateTestDbScriptClass is instantiated more than once per table state in
    // multiple_calls_to_insertDB_with_same_script_class_should_insert_multiple_times below, so its
    // id can't be a fixed literal - it'd collide with itself on the second call.
    static final AtomicInteger privateTestDbScriptClassId = new AtomicInteger(1);

    private static abstract class PrivateTestDbScriptClass implements ITestDBSchema {{
        log.debug("Start: {}", this.getClass());
        table_1().id(privateTestDbScriptClassId.getAndIncrement()).str_column_1("three").str_column_2("four");
    }
    public PrivateTestDbScriptClass() {};
    };

    private static abstract class PrivateTestDbScriptClassWithPrivateConstructor implements ITestDBSchema {{
        log.debug("Start: {}", this.getClass());
        table_1().id(1).str_column_1("three").str_column_2("four");
    }
        private PrivateTestDbScriptClassWithPrivateConstructor() {};
    };

    private abstract class NonStaticPrivateTestDbScriptClassWithPrivateConstructor implements ITestDBSchema {{
        log.debug("Start: {}", this.getClass());
        table_1().id(1).str_column_1("three").str_column_2("four");
    }
        private NonStaticPrivateTestDbScriptClassWithPrivateConstructor() {};
    };

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1);
    }

    private final JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);


    @Test
    public void call_insertDB_with_abstract_class_implementing_db_schema_should_insert_rows(){
        engine.insertDB(TestDbScriptClass.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("one",      "two"),
                row("three",    "four")
        ));
    }

    @Test
    public void call_insertDB_should_work_with_private_classes(){
        engine.insertDB(PrivateTestDbScriptClass.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("three",    "four")
        ));
    }

    @Test(dependsOnMethods = "call_insertDB_should_work_with_private_classes")
    public void call_insertDB_should_work_with_private_classes_and_private_constructor(){
        engine.insertDB(PrivateTestDbScriptClassWithPrivateConstructor.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("three",    "four")
        ));
    }

    @Test(dependsOnMethods = "call_insertDB_should_work_with_private_classes_and_private_constructor")
    public void call_insertDB_should_throw_error_if_inner_class_is_not_static(){
        Assertions.assertThatThrownBy(()->engine.insertDB(NonStaticPrivateTestDbScriptClassWithPrivateConstructor.class))
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void call_resetDB_should_work_with_private_classes(){
        insertSeedRow(TABLE_NAME_1,
                "INSERT INTO %s (id,str_column_1,str_column_2) VALUES(999,'old value1','old value 2')", TABLE_NAME_1);

        engine.resetDB(PrivateTestDbScriptClass.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("three",    "four")
        ));
    }

    @Test
    public void multiple_calls_to_insertDB_with_same_script_class_should_insert_multiple_times(){
        engine.insertDB(PrivateTestDbScriptClass.class);
        engine.insertDB(PrivateTestDbScriptClass.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("three",    "four"),
                row("three",    "four")
        ));
    }

}
