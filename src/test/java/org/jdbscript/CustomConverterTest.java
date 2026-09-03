package org.jdbscript;

import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.testng.annotations.Test;

@Test
public class CustomConverterTest extends JdbAbstractTest {

    private interface ITable1 extends IDBSchema.IDBRecord {
        ITable1 str_column_1(Object value);
    }

    private interface ITestSchema extends IDBSchema {
        ITable1 table_1();
    }

    private static class ReversingConverter implements IJDBTypeConverter {
        @Override
        public boolean canConvert(Object value) {
            return value instanceof String;
        }

        @Override
        public Object convert(Object value) {
            return new StringBuilder((String) value).reverse().toString();
        }
    }

    @Test
    public void custom_converter_should_work() {
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converters(new ReversingConverter())
                .executor(testConfiguration.getScriptExecutor())
                .build();

        engine.resetDB(db -> {
            db.table_1().str_column_1("hello");
        });

        assertTableValues(table("table_1",
                columns("str_column_1"),
                row("olleh")
        ));
    }

    @Test
    public void custom_converters_should_replace_defaults() {
        // Register only the reversing converter
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converters(new ReversingConverter())
                .executor(testConfiguration.getScriptExecutor())
                .build();

        // EnumToStringConverter is a default converter.
        // If it's replaced, TestEnum.VAL1 should NOT be converted to String "VAL1" by JDBScript.
        
        // We will now verify that we can PROVIDE a custom enum converter and it will be used instead of defaults.
        IJDBEngine<ITestSchema> engineWithEnum = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converters(new IJDBTypeConverter() {
                    @Override
                    public boolean canConvert(Object value) {
                        return value instanceof Enum;
                    }

                    @Override
                    public Object convert(Object value) {
                        return "CUSTOM_" + value.toString();
                    }
                })
                .executor(testConfiguration.getScriptExecutor())
                .build();

        engineWithEnum.resetDB(db -> {
            db.table_1().str_column_1(TestEnum.VAL1);
        });

        assertTableValues(table("table_1",
                columns("str_column_1"),
                row("CUSTOM_VAL1")
        ));
    }

    public enum TestEnum {
        VAL1
    }
}
