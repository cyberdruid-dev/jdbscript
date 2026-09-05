package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static class CustomEnumConverter implements IJDBTypeConverter {
        @Override
        public boolean canConvert(Object value) {
            return value instanceof Enum;
        }

        @Override
        public Object convert(Object value) {
            return "CUSTOM_" + value.toString();
        }
    }

    @Test
    public void custom_converter_should_work() {
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converter(new ReversingConverter())
                .executor(testConfiguration.getScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
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
    public void custom_converter_should_add_to_defaults_not_replace_them() {
        // A custom converter registered via .converter(...) doesn't disturb default handling of
        // types it doesn't itself match - Enums still convert to their name() as usual.
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converter(new ReversingConverter())
                .executor(testConfiguration.getScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
                .build();

        engine.resetDB(db -> {
            db.table_1().str_column_1(TestEnum.VAL1);
        });

        assertTableValues(table("table_1",
                columns("str_column_1"),
                row("VAL1")
        ));
    }

    @Test
    public void custom_converter_added_without_disabling_defaults_does_not_override_a_default() {
        // .converter(...) only adds; converters run in registration order and the first match
        // wins. The built-in EnumToStringConverter is registered before anything added here, so it
        // still wins for Enums even though a custom Enum-handling converter was also registered.
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .converter(new CustomEnumConverter())
                .executor(testConfiguration.getScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
                .build();

        engine.resetDB(db -> {
            db.table_1().str_column_1(TestEnum.VAL1);
        });

        assertTableValues(table("table_1",
                columns("str_column_1"),
                row("VAL1")
        ));
    }

    @Test
    public void disableDefaultConverters_lets_a_custom_converter_replace_a_default() {
        IJDBEngine<ITestSchema> engineWithEnum = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .disableDefaultConverters()
                .converter(new CustomEnumConverter())
                .executor(testConfiguration.getScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
                .build();

        engineWithEnum.resetDB(db -> {
            db.table_1().str_column_1(TestEnum.VAL1);
        });

        assertTableValues(table("table_1",
                columns("str_column_1"),
                row("CUSTOM_VAL1")
        ));
    }

    @Test
    public void converter_should_reject_null() {
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(testConfiguration.getScriptExecutor());

        assertThatThrownBy(() -> builder.converter(null))
                .isInstanceOf(JDBScriptException.class);
    }

    public enum TestEnum {
        VAL1
    }
}
