package org.jdbscript.datatypes;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.DbmsType;
import org.jdbscript.utils.TestConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.*;

import static org.jdbscript.DbmsType.SQLITE;

@Test
public class DateTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "date_table";

    private interface IDateTable extends IDBRecord {
        IDateTable date_column(Date value);
    }
    private interface IDateTestSchema extends IDbSchema {

        IDateTable date_table();

    }

    private interface ISqlDateTable extends IDBRecord {
        ISqlDateTable date_column(java.sql.Date value);
    }
    private interface ISqlDateTestSchema extends IDbSchema {

        ISqlDateTable date_table();

    }
    private interface ILocalDateTable extends IDBRecord {
        ILocalDateTable date_column(LocalDate value);
    }
    private interface ILocalDateTestSchema extends IDbSchema {

        ILocalDateTable date_table();

    }

    @BeforeMethod
    public void beforeMethod(){
        skipFor("date", SQLITE);
        cleanupTables(TABLE_NAME);
    }

    private final IJDBEngine<IDateTestSchema> engine = createEngine(IDateTestSchema.class);
    private final IJDBEngine<ISqlDateTestSchema> sqlDateEngine = createEngine(ISqlDateTestSchema.class);
    private final IJDBEngine<ILocalDateTestSchema> localDateEngine = createEngine(ILocalDateTestSchema.class);


    @Test(dataProvider = "dates")
    public void test_insert_date(Date date) {
        engine.resetDB((db)->{
            db.date_table().date_column(date);
        });

        assertTableValues(table(TABLE_NAME,
                columns("date_column:LocalDate"),
                row(asLocalDate(date))
        ));
    }

    @Test(dataProvider = "sqlDates")
    public void date_columns_should_accept_java_sql_Date(java.sql.Date date) {
        sqlDateEngine.resetDB((db)->{
            db.date_table().date_column(date);
        });

        assertTableValues(table(TABLE_NAME,
                columns("date_column:LocalDate"),
                row(asLocalDate(date))
        ));
    }

    @Test(dataProvider = "localDates")
    public void date_columns_should_accept_LocalDate(LocalDate date) {
        localDateEngine.resetDB((db)->{
            db.date_table().date_column(date);
        });

        assertTableValues(table(TABLE_NAME,
                columns("date_column:LocalDate"),
                row(date)
        ));
    }

    @Test()
    public void date_field_should_accept_null() {
        Date date1 = date(2000,0,1);
        engine.resetDB((db)->{
            db.date_table().date_column(date1);
            db.date_table().date_column(null);
        });

        assertTableValues(table(TABLE_NAME,
                columns("date_column:LocalDate"),
                row(new Object[]{asLocalDate(date1)}),
                row(new Object[]{null})
        ));
    }

    @DataProvider
    private Iterator<LocalDate> localDates() {
        List<LocalDate> result = new ArrayList<>();
        dates().forEachRemaining(date->{
            LocalDate localDate = asLocalDate(date);
            result.add(localDate);
        });
        return result.iterator();
    }

    @DataProvider
    private Iterator<java.sql.Date> sqlDates() {
        List<java.sql.Date> result = new ArrayList<>();
        dates().forEachRemaining(date->{
            result.add(new java.sql.Date(date.getTime()));
        });
        return result.iterator();
    }

    @DataProvider
    private Iterator<Date> dates(){
        DbmsType dbms = this.testConfiguration.getDbmsType();
        List<Date> dates = new ArrayList<>();
        dates.addAll(List.of(
                date(1925,5,5),
                date(1970,0,1),
                date(1969,11,31),
                date(2000,0,1),
                date(2024,8,6)
        ));
        return dates.iterator();
    }



    private static Date date(int year, int month, int date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TestConfiguration.UTC);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(year, month, date, 0, 0, 0);
        return new Date(cal.getTimeInMillis());
    }

}
