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
        engine = new JDBEngine(dataSource, ITestSchema.class);
    }

    @Test
    public void engine_executor_method_should_not_accept_null() {
        assertThatThrownBy(()->engine.setExecutor(null))
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void engine_executor_can_only_be_called_once() {
        engine.setExecutor(new SqlScriptExecutor());

        assertThatThrownBy(()->engine.setExecutor(new MyScriptExecutor()))
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void engine_executor_method_sets_new_executor() {
        engine.setExecutor(new MyScriptExecutor());

        engine.resetDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertThat(myScriptExecutorUsed)
                .describedAs("%s.execute() called", MyScriptExecutor.class)
                .isTrue();
    }

}
