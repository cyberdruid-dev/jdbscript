package org.jdbscript.datatypes;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.utils.TestConfiguration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.jdbscript.DbmsType.SQLITE;

@Test
public class TimestampTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "timestamp_table";

    private interface ITimestampTable extends IDBRecord {
        ITimestampTable timestamp_column(Date value);
    }
    private interface ITimestampTestSchema extends IDbSchema {

        ITimestampTable timestamp_table();

    }

    private interface IInstantTable extends IDBRecord {
        IInstantTable timestamp_column(Instant value);
    }
    private interface IInstantTestSchema extends IDbSchema {

        IInstantTable timestamp_table();

    }

    @BeforeMethod
    public void beforeMethod(){
        skipFor("timestamp", SQLITE);
        cleanupTables(TABLE_NAME);
    }

    private final IJDBEngine<ITimestampTestSchema> engine = createEngine(ITimestampTestSchema.class);
    private final IJDBEngine<IInstantTestSchema> instantEngine = createEngine(IInstantTestSchema.class);


    @Test(dataProvider = "timestamps")
    public void test_insert_date(Date timestamp) {
        engine.resetDB((db)->{
            db.timestamp_table().timestamp_column(timestamp);
        });

        assertTableValues(table(TABLE_NAME,
                columns("timestamp_column:Date"),
                row(timestamp)
        ));
    }

    @Test(dataProvider = "instants")
    public void timestamp_field_should_accept_Instant(Instant timestamp) {
        instantEngine.resetDB((db)->{
            db.timestamp_table().timestamp_column(timestamp);
        });

        assertTableValues(table(TABLE_NAME,
                columns("timestamp_column:Timestamp"),
                row(new Timestamp(timestamp.toEpochMilli()))
        ));
    }

    @Test()
    public void date_field_should_accept_null() {
        Date timestamp = timestamp(2000,0,1, 0, 0, 0);
        engine.resetDB((db)->{
            db.timestamp_table().timestamp_column(timestamp);
            db.timestamp_table().timestamp_column(null);
        });

        assertTableValues(table(TABLE_NAME,
                columns("timestamp_column:Timestamp"),
                row(new Object[]{timestamp}),
                row(new Object[]{null})
        ));
    }


    @DataProvider
    private Iterator<Instant> instants() {
        List<Instant> result = new ArrayList<>();
        timestamps().forEachRemaining(date->{
            result.add(date.toInstant());
        });
        return result.iterator();
    }

    @DataProvider
    private Iterator<Date> timestamps(){
        List<Date> dates = new ArrayList<>();
        dates.addAll(List.of(
                timestamp(1970,0,2, 0, 0, 1),
                timestamp(2000,0,1, 0, 0, 0),
                timestamp(2024,8,6, 13, 0, 18)
        ));
        return dates.iterator();
    }



    private static Date timestamp(int year, int month, int date, int hour, int minute, int seconds) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TestConfiguration.UTC);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(year, month, date, hour, minute, seconds);
        return new Date(cal.getTimeInMillis());
    }

}
