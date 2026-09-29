package org.jdbscript;

import org.jdbscript.db.ITestDBSchema;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class ChangeScriptExecutorTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private JDBEngine<ITestDBSchema> engine;
    private static boolean myScriptExecutorUsed = false;


    private static class MyScriptExecutor extends SqlScriptExecutor {

        public MyScriptExecutor() {
        }

        @Override
        public void insert(JDBScript dbScript) {
            myScriptExecutorUsed = true;
            super.insert(dbScript);
        }
    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1);
        myScriptExecutorUsed = false;
        engine = JDBEngine.builder(ITestDBSchema.class)
                .dataSource(dataSource)
                .build();
    }

    @Test
    public void builder_executor_method_should_not_accept_null() {
        assertThatThrownBy(()->JDBEngine.builder(ITestDBSchema.class).executor(null))
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void engine_uses_executor_from_builder() {
        JDBEngine<ITestDBSchema> engine = JDBEngine.builder(ITestDBSchema.class)
                .dataSource(dataSource)
                .executor(new MyScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
                .build();

        engine.resetDB((db)->{
            db.table_1().id(1).str_column_1("Hello");
        });

        assertThat(myScriptExecutorUsed)
                .describedAs("%s.execute() called", MyScriptExecutor.class)
                .isTrue();
    }

}
