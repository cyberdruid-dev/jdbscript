package org.jdbscript;

import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.JDbScript;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class ChangeScriptExecutorTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record str_column_1(String value);
    }
    private interface ITestSchema extends IDbSchema{

        ITable1Record table_1();

    }
    private JDBEngine<ITestSchema> engine;
    private static boolean myScriptExecutorUsed = false;


    private static class MyScriptExecutor extends SqlScriptExecutor {

        public MyScriptExecutor() {
        }

        @Override
        public void insert(JDbScript dbScript) {
            myScriptExecutorUsed = true;
            super.insert(dbScript);
        }
    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1);
        myScriptExecutorUsed = false;
        engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(dataSource)
                .build();
    }

    @Test
    public void builder_executor_method_should_not_accept_null() {
        assertThatThrownBy(()->JDBEngine.builder(ITestSchema.class).executor(null))
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void engine_uses_executor_from_builder() {
        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(dataSource)
                .executor(new MyScriptExecutor())
                .build();

        engine.resetDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertThat(myScriptExecutorUsed)
                .describedAs("%s.execute() called", MyScriptExecutor.class)
                .isTrue();
    }

}
