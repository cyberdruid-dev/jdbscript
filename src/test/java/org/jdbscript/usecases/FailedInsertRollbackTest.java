package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class FailedInsertRollbackTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record id(int value);
        ITable1Record str_column_1(String value);
    }
    private interface ITestSchema extends IDBSchema {
        ITable1Record table_1();
    }

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        skipFor("rollback of a failed insertDB", DBMSType.DUCKDB, null, "runs in auto-commit mode");
        cleanupTables(TABLE_NAME_1);
    }

    public void failed_insert_should_roll_back_earlier_inserts_of_the_same_call() {
        assertThatThrownBy(() -> engine.insertDB(db -> {
            db.table_1().id(1).str_column_1("one");
            db.table_1().id(1).str_column_1("duplicate key");
        })).isInstanceOf(RuntimeException.class);

        assertTableEmpty(TABLE_NAME_1);
    }
}
