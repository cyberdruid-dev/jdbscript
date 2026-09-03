package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.opentest4j.AssertionFailedError;
import org.testng.annotations.Test;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Test
public class DbAssertionsTest extends JdbAbstractTest {


    private interface ITableForAssertions extends IDBRecord {
        ITableForAssertions str_column_1(String value);
        ITableForAssertions str_column_2(String value);
        ITableForAssertions int_column_1(Integer value);
        ITableForAssertions boolean_column_1(Boolean value);
        ITableForAssertions date_column_1(Date value);
    }

    private interface IAssertionTestSchema extends IDBSchema {

        ITableForAssertions table_for_assertions();

    }

    private final IJDBEngine<IAssertionTestSchema> engine = createEngine(IAssertionTestSchema.class);
    public static Date date1;
    public static Date date2;
    static {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2010, 5, 6, 17, 18, 29);
        calendar.set(Calendar.MILLISECOND, 0);
        date1 =  calendar.getTime();
        calendar.set(2011, 6, 16, 17, 18, 29);
        date2 =  calendar.getTime();
    }

    public static abstract class AssertionsDataset implements IAssertionTestSchema {{
        table_for_assertions().str_column_1("str11").str_column_2("str21")
                .boolean_column_1(true).int_column_1(101).date_column_1(date1);
        table_for_assertions().str_column_1("str12").str_column_2("str22")
                .boolean_column_1(false).int_column_1(102).date_column_1(date2);
    }};

    public static abstract class AssertionsDatasetWithNull implements IAssertionTestSchema {{
        table_for_assertions().str_column_1("str13").str_column_2(null)
                .boolean_column_1(true).int_column_1(103).date_column_1(date1);
    }};


    public void assertDBHas__passes_if__row_with_multiple_column_values_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHas(db -> {
            db.table_for_assertions().str_column_1("str11").int_column_1(101);
        });
    }

    public void assertDBHas__passes_if__row_with_boolean_column_values_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHas(db -> {
            db.table_for_assertions().str_column_1("str11").boolean_column_1(true);
        });
    }

    public void assertDBHas__passes_if__row_with_date_column_values_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHas(db -> {
            db.table_for_assertions().str_column_1("str11").date_column_1(date1);
        });
    }

    public void assertDBHas__passes_if__row_with_date_column_value_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db -> {
                db.table_for_assertions().str_column_1("str11").date_column_1(date2);
            });
        }, new AssertionFailedError("Expected row to exist."));
    }

    public void assertDBHas__FAIL_if__row_with_multiple_column_values_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db->{
                db.table_for_assertions().str_column_1("str11").int_column_1(102);
            });
        }, new AssertionFailedError("Expected row to exist."));
    }

    public void assertDBHas__passes_if__row_with_column_value_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHas(db->{
            db.table_for_assertions().str_column_1("str11");
        });
    }

    public void assertDBHas__passes_if__multiple_row_with_column__value_exist() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHas(db->{
            db.table_for_assertions().str_column_1("str11");
            db.table_for_assertions().str_column_1("str12");
        });
    }

    @Test
    public void assertDBHas__FAILS_if__one_of_multiple_row_with_column_value__NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db -> {
                db.table_for_assertions().str_column_1("str11");
                db.table_for_assertions().str_column_1("strXX");
            });
        }, new AssertionFailedError("Expected row to exist."));
    }

    @Test
    public void assertDBHas__FAILS_if__row_with_column__value_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db -> {
                db.table_for_assertions().str_column_1("strXX");
            });
        }, new AssertionFailedError("Expected row to exist."));
    }

    @Test
    public void assertDBHasNot__passes_if_row_with_column_value_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHasNot(db->{
            db.table_for_assertions().str_column_1("strXX");
        });
    }

    @Test
    public void assertDBHasNot__passes_if_non_of_multiple_row_with_column_value_exists() {
        engine.resetDB(AssertionsDataset.class);

        engine.assertDBHasNot(db->{
            db.table_for_assertions().str_column_1("strXX");
            db.table_for_assertions().str_column_1("strYY");
        });
    }

    @Test
    public void assertDBHasNot__FAIL_if_row_with_column_value_EXISTS() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()->{
            engine.assertDBHasNot(db->{
                db.table_for_assertions().str_column_1("str11");
            });
        }, new AssertionFailedError("Expected row to NOT exist."));
    }

    @Test
    public void assertDBHasNot__FAIL_if_any_row_with_column_value_EXISTS() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()->{
            engine.assertDBHasNot(db->{
                db.table_for_assertions().str_column_1("str11");
                db.table_for_assertions().str_column_1("strXX");
            });
        }, new AssertionFailedError("Expected row to NOT exist."));
    }

    @Test
    public void assertDBHas__passes_if__row_with_null_column_value_exists() {
        engine.resetDB(AssertionsDatasetWithNull.class);

        engine.assertDBHas(db -> {
            db.table_for_assertions().str_column_1("str13").str_column_2(null);
        });
    }

    @Test
    public void assertDBHasNot__FAIL_if_row_with_null_column_value_EXISTS() {
        engine.resetDB(AssertionsDatasetWithNull.class);

        expectFailure(()->{
            engine.assertDBHasNot(db->{
                db.table_for_assertions().str_column_1("str13").str_column_2(null);
            });
        }, new AssertionFailedError("Expected row to NOT exist."));
    }

    protected void expectFailure(Runnable block, AssertionFailedError expectedError){
        boolean failed = false;
        try {
            block.run();
        } catch(AssertionFailedError afe){
            failed = true;
            assertThat(afe.getMessage())
                    .describedAs("assertion error message")
                    .isEqualTo(expectedError.getMessage());
        }
        if(!failed) {
            fail("Expected AssertionFailedError with message %s", expectedError.getMessage());
        }

    }

}
