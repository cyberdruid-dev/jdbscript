package org.jdbscript.datatypes;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.impl.conversion.EnumOrdinalConverter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

@Test
public class EnumTest extends JdbAbstractTest {

    private final static String VARCHAR_TABLE = "varchar_table";
    private final static String INT_TABLE = "int_table";

    public enum TestEnum {
        VALUE_1001,
        VALUE_1002,
        VALUE_1003

    }

    private interface IVarcharTable extends IDBRecord {
        IVarcharTable varchar_column(TestEnum value);
    }
    private interface IEnumTestSchema extends IDBSchema {

        IVarcharTable varchar_table();

    }

    private interface IIntTable extends IDBRecord {
        IIntTable int_column(TestEnum value);
    }
    private interface IOrdinalEnumTestSchema extends IDBSchema {

        IIntTable int_table();

    }

    private static abstract class OrdinalEnumClassScript implements IOrdinalEnumTestSchema {{
        int_table().int_column(TestEnum.VALUE_1003);
    }};

    private static abstract class StringEnumClassScript implements IEnumTestSchema {{
        varchar_table().varchar_column(TestEnum.VALUE_1003);
    }};


    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(VARCHAR_TABLE, INT_TABLE);
    }

    private final IJDBEngine<IEnumTestSchema> engine = createEngine(IEnumTestSchema.class);
    private final IJDBEngine<IOrdinalEnumTestSchema> ordinalEngine = createEngine(IOrdinalEnumTestSchema.class,
            List.of(new EnumOrdinalConverter())
            );

    public void test_insert_enum_as_int() {
        TestEnum value1 = TestEnum.VALUE_1001;
        TestEnum value2 = TestEnum.VALUE_1003;
        ordinalEngine.resetDB((db)->{
            db.int_table().int_column(value1);
            db.int_table().int_column(value2);
        });

        assertTableValues(table(INT_TABLE,
                columns("int_column"),
                row(value1.ordinal()),
                row(value2.ordinal())
        ));
    }

    @Test
    public void insert_enum_as_int_should_work_for_class_scripts() {
        TestEnum value1 = TestEnum.VALUE_1003;
        ordinalEngine.resetDB(OrdinalEnumClassScript.class);

        assertTableValues(table(INT_TABLE,
                columns("int_column"),
                row(value1.ordinal())
        ));
    }

    @Test
    public void insert_enum_as_String_should_work_for_class_scripts() {
        TestEnum value1 = TestEnum.VALUE_1003;
        engine.resetDB(StringEnumClassScript.class);

        assertTableValues(table(VARCHAR_TABLE,
                columns("varchar_column"),
                row(value1.toString())
        ));
    }

    public void test_insert_enum_as_String() {
        TestEnum value1 = TestEnum.VALUE_1001;
        TestEnum value2 = TestEnum.VALUE_1003;
        engine.resetDB((db)->{
            db.varchar_table().varchar_column(value1);
            db.varchar_table().varchar_column(value2);
        });

        assertTableValues(table(VARCHAR_TABLE,
                columns("varchar_column"),
                row(value1.toString()),
                row(value2.toString())
        ));
    }

    @Test(dependsOnMethods = "test_insert_enum_as_String")
    public void blob_field_should_accept_null() {
        TestEnum value1 = TestEnum.VALUE_1001;
        engine.resetDB((db)->{
            db.varchar_table().varchar_column(value1);
            db.varchar_table().varchar_column(null);
        });

        assertTableValues(table(VARCHAR_TABLE,
                columns("varchar_column"),
                row(new Object[]{value1.toString()}),
                row(new Object[]{null})
        ));
    }

}
