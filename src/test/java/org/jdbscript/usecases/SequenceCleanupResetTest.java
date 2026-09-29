package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class SequenceCleanupResetTest extends JdbAbstractTest {

    private final static String TABLE_NAME_GENERATED = "generated_int_id_table";

    private interface IGeneratedIntIdTableRecord extends IDBRecord {
        IGeneratedIntIdTableRecord generated_id_column(Integer value);
        IGeneratedIntIdTableRecord varchar_column(String value);
    }
    private interface ITestSchema extends IDBSchema {
        IGeneratedIntIdTableRecord generated_int_id_table();
    }

    @BeforeMethod
    public void beforeMethod() {
        skipFor("sequence reset on cleanup", DBMSType.H2, DBMSType.MSSQL, DBMSType.MYSQL, DBMSType.MARIADB,
                DBMSType.SQLITE, DBMSType.DUCKDB, DBMSType.SPANNER);
        cleanupTables(TABLE_NAME_GENERATED);
    }

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    private long nextValue(String sequenceName) {
        String sql = switch (testConfiguration.getDbmsType()) {
            case ORACLE -> "SELECT %s.NEXTVAL FROM dual";
            case POSTGRESQL, COCKROACHDB -> "SELECT nextval('%s')";
            case DB2 -> "VALUES NEXT VALUE FOR %s";
            case HSQLDB -> "CALL NEXT VALUE FOR %s";
            default -> throw new IllegalStateException("No nextval syntax for " + testConfiguration.getDbmsType());
        };
        return withResultSet(sql.formatted(sequenceName), (rs, columns, types) -> {
            rs.next();
            return rs.getLong(1);
        });
    }

    @Test
    public void first_reset_should_assign_the_predictable_floor_value() {
        engine.resetDB(db -> db.generated_int_id_table().varchar_column("a"));

        assertTableValues(table(TABLE_NAME_GENERATED,
                columns("generated_id_column", "varchar_column"),
                row(10000, "a")
        ));
    }

    @Test
    public void second_reset_cycle_should_restart_from_the_floor_not_keep_growing() {
        engine.resetDB(db -> {
            db.generated_int_id_table().varchar_column("a");
            db.generated_int_id_table().varchar_column("b");
        });

        engine.resetDB(db -> db.generated_int_id_table().varchar_column("c"));

        assertTableValues(table(TABLE_NAME_GENERATED,
                columns("generated_id_column", "varchar_column"),
                row(10000, "c")
        ));
    }

    @Test
    public void cleanup_should_reset_used_standalone_sequence_to_the_floor() {
        nextValue("standalone_seq");
        nextValue("standalone_seq");
        nextValue("standalone_seq");

        engine.cleanupDB();

        assertThat(nextValue("standalone_seq")).isEqualTo(10000L);
    }

    @Test
    public void repeated_cleanup_should_keep_standalone_sequence_at_the_floor() {
        engine.cleanupDB();
        engine.cleanupDB();

        assertThat(nextValue("standalone_seq")).isEqualTo(10000L);
    }
}
