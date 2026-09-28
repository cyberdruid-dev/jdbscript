package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code DuckdbStrategy.afterInsert()}'s sequence-reset against a live DuckDB, since the
 * mechanism it's checking (whether a sequence is already safely past the floor) is only meaningful
 * against DuckDB's own {@code duckdb_sequences()} - mocking that out wouldn't prove the real
 * behavior. {@link org.jdbscript.impl.sql.DuckdbStrategyTest} already covers the error-wrapping
 * branches with mocks; this proves the actual sequence value against real DuckDB.
 */
@Test
public class DuckdbSequenceResetTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    @BeforeMethod
    public void beforeMethod() {
        skipUnless("DuckDB sequence reset after insert", DBMSType.DUCKDB);
        cleanupTables(TABLE_NAME_1);
    }

    private final JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);

    private long currentSequenceValue() {
        return withResultSet(
                "SELECT last_value FROM duckdb_sequences() WHERE sequence_name = 'table_1_id_seq'",
                (rs, columns, types) -> {
                    assertThat(rs.next()).describedAs("table_1_id_seq should exist").isTrue();
                    return rs.getLong(1);
                });
    }

    @Test
    public void afterInsert_should_not_re_advance_a_sequence_already_past_the_safe_floor() {
        engine.insertDB(db -> db.table_1().id(1).str_column_1("first"));
        long afterFirstInsert = currentSequenceValue();
        assertThat(afterFirstInsert)
                .describedAs("first insert should push the sequence past the safe floor")
                .isGreaterThanOrEqualTo(10000L);

        engine.insertDB(db -> db.table_1().id(2).str_column_1("second"));
        long afterSecondInsert = currentSequenceValue();

        assertThat(afterSecondInsert)
                .describedAs("the sequence was already safely past the floor, so a second insert "
                        + "shouldn't advance it any further")
                .isEqualTo(afterFirstInsert);
    }
}
