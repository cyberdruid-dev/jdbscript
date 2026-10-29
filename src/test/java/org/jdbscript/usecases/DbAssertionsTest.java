package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.opentest4j.AssertionFailedError;
import org.testng.annotations.Test;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@Test
public class DbAssertionsTest extends JdbAbstractTest {


    private interface ITableForAssertions extends IDBRecord {
        ITableForAssertions id(int value);
        ITableForAssertions str_column_1(String value);
        ITableForAssertions str_column_2(String value);
        ITableForAssertions int_column_1(Integer value);
        ITableForAssertions boolean_column_1(Boolean value);
        ITableForAssertions date_column_1(Date value);
        ITableForAssertions str_column_1(Status value);
    }

    private enum Status { ACTIVE, BLOCKED }

    private interface IAssertionTestSchema extends IDBSchema {

        ITableForAssertions table_for_assertions();

    }

    private interface ITableWithDefaults extends IDBRecord {
        ITableWithDefaults id(int value);
        ITableWithDefaults str_column_1(String value);
        ITableWithDefaults str_column_2(String value);

        default void defaults() {
            str_column_2("from-defaults");
        }
    }

    private interface IDefaultsSchema extends IDBSchema {
        ITableWithDefaults table_for_assertions();
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
        table_for_assertions().id(1).str_column_1("str11").str_column_2("str21")
                .boolean_column_1(true).int_column_1(101).date_column_1(date1);
        table_for_assertions().id(2).str_column_1("str12").str_column_2("str22")
                .boolean_column_1(false).int_column_1(102).date_column_1(date2);
    }};

    public static abstract class AssertionsDatasetWithNull implements IAssertionTestSchema {{
        table_for_assertions().id(1).str_column_1("str13").str_column_2(null)
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
        }, new AssertionFailedError("assertDBHas: 1 of 1 records not found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='str11', date_column_1=2011-07-16T17:18:29Z"));
    }

    public void assertDBHas__FAIL_if__row_with_multiple_column_values_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db->{
                db.table_for_assertions().str_column_1("str11").int_column_1(102);
            });
        }, new AssertionFailedError("assertDBHas: 1 of 1 records not found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='str11', int_column_1=102"));
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
        }, new AssertionFailedError("assertDBHas: 1 of 2 records not found in the database:\n"
                + "  #2 table_for_assertions: str_column_1='strXX'"));
    }

    @Test
    public void assertDBHas__reports_every_record_NOT_found() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db -> {
                db.table_for_assertions().str_column_1("strXX");
                db.table_for_assertions().str_column_1("str11");
                db.table_for_assertions().str_column_1("strYY").str_column_2(null);
            });
        }, new AssertionFailedError("assertDBHas: 2 of 3 records not found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='strXX'\n"
                + "  #3 table_for_assertions: str_column_1='strYY', str_column_2=null"));
    }

    @Test
    public void assertDBHas__FAILS_if__row_with_column__value_NOT_exists() {
        engine.resetDB(AssertionsDataset.class);

        expectFailure(()-> {
            engine.assertDBHas(db -> {
                db.table_for_assertions().str_column_1("strXX");
            });
        }, new AssertionFailedError("assertDBHas: 1 of 1 records not found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='strXX'"));
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

        expectFailureStartingWith(()->{
            engine.assertDBHasNot(db->{
                db.table_for_assertions().str_column_1("str11");
            });
        }, "assertDBHasNot: 1 of 1 records found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='str11'\n"
                + "     row: ");
    }

    @Test
    public void assertDBHasNot__FAIL_shows_the_matching_row() {
        engine.resetDB(AssertionsDataset.class);

        assertThatThrownBy(() -> engine.assertDBHasNot(db -> {
            db.table_for_assertions().int_column_1(102);
        })).isInstanceOf(AssertionFailedError.class)
                .satisfies(e -> assertThat(e.getMessage().substring(e.getMessage().indexOf("row: ")))
                        .containsIgnoringCase("id=2")
                        .containsIgnoringCase("str_column_1='str12'")
                        .containsIgnoringCase("str_column_2='str22'"));
    }

    @Test
    public void assertDBHasNot__FAIL_if_any_row_with_column_value_EXISTS() {
        engine.resetDB(AssertionsDataset.class);

        assertThatThrownBy(() -> engine.assertDBHasNot(db -> {
            db.table_for_assertions().str_column_1("strXX");
            db.table_for_assertions().str_column_1("str11");
            db.table_for_assertions().str_column_1("str12");
        })).isInstanceOf(AssertionFailedError.class)
                .hasMessageStartingWith("assertDBHasNot: 2 of 3 records found in the database:\n"
                        + "  #2 table_for_assertions: str_column_1='str11'\n"
                        + "     row: ")
                .hasMessageContaining("\n  #3 table_for_assertions: str_column_1='str12'\n     row: ")
                .hasMessageNotContaining("#1");
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

        expectFailureStartingWith(()->{
            engine.assertDBHasNot(db->{
                db.table_for_assertions().str_column_1("str13").str_column_2(null);
            });
        }, "assertDBHasNot: 1 of 1 records found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='str13', str_column_2=null\n"
                + "     row: ");
    }

    @Test
    public void assertDBHas__on_oracle_matches_a_varchar_inserted_as_empty_string() {
        skipUnless("Oracle treats '' as NULL", DBMSType.ORACLE);
        engine.resetDB(db -> {
            db.table_for_assertions().id(1).str_column_1("str14").str_column_2("");
        });

        engine.assertDBHas(db -> {
            db.table_for_assertions().str_column_1("str14").str_column_2("");
            db.table_for_assertions().str_column_1("str14").str_column_2(null);
        });
    }

    @Test
    public void assertDBHasNot__on_oracle_FAILS_for_empty_string_if_column_is_null() {
        skipUnless("Oracle treats '' as NULL", DBMSType.ORACLE);
        engine.resetDB(AssertionsDatasetWithNull.class);

        expectFailureStartingWith(() -> {
            engine.assertDBHasNot(db -> {
                db.table_for_assertions().str_column_1("str13").str_column_2("");
            });
        }, "assertDBHasNot: 1 of 1 records found in the database:\n"
                + "  #1 table_for_assertions: str_column_1='str13', str_column_2=''\n"
                + "     row: ");
    }

    @Test
    public void assertDBHas__fails_clearly_if_record_has_no_columns_set() {
        engine.resetDB(AssertionsDataset.class);

        assertThatThrownBy(() -> engine.assertDBHas(db -> {
            db.table_for_assertions();
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("table_for_assertions");
    }

    @Test
    public void assertDBHasNot__fails_clearly_if_record_has_no_columns_set() {
        engine.resetDB(AssertionsDataset.class);

        assertThatThrownBy(() -> engine.assertDBHasNot(db -> {
            db.table_for_assertions();
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("table_for_assertions");
    }

    public void assertDBHas__does_not_apply_defaults() {
        IJDBEngine<IDefaultsSchema> defaultsEngine = createEngine(IDefaultsSchema.class);
        defaultsEngine.resetDB(db -> db.table_for_assertions().id(1).str_column_1("str15").str_column_2("explicit"));

        defaultsEngine.assertDBHas(db -> db.table_for_assertions().str_column_1("str15"));
    }

    public void assertDBHas__converts_values_like_insert() {
        engine.resetDB(db -> db.table_for_assertions().id(1).str_column_1("ACTIVE"));

        engine.assertDBHas(db -> db.table_for_assertions().str_column_1(Status.ACTIVE));
        engine.assertDBHasNot(db -> db.table_for_assertions().str_column_1(Status.BLOCKED));
    }

    private void expectFailureStartingWith(Runnable block, String expectedMessageStart) {
        assertThatThrownBy(block::run)
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageStartingWith(expectedMessageStart);
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
