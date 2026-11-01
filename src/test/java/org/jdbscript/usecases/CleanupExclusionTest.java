package org.jdbscript.usecases;

import ch.qos.logback.classic.Level;
import org.jdbscript.DBMSType;
import org.jdbscript.ExcludedTableWriteStrategy;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.RecordTools;
import org.jdbscript.db.ICustomerRecord;
import org.jdbscript.db.IOrderSchema;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.utils.LogCapture;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class CleanupExclusionTest extends JdbAbstractTest {

    private interface IGeneratedIdRecord extends IDBRecord {
        IGeneratedIdRecord varchar_column(String value);
    }

    private interface IGeneratedIdSchema extends IDBSchema {
        IGeneratedIdRecord generated_int_id_table();
    }

    private interface ICountedOrderRecord extends IDBRecord {
        ICountedOrderRecord id(int value);
        ICountedOrderRecord customer_id(int value);
        ICountedOrderRecord order_date(String value);

        default void defaults(RecordTools tools) {
            id(tools.nextIntId("order_id", 200));
            customer_id(1);
            order_date("2023-10-27");
        }
    }

    private interface ICountedOrderSchema extends IDBSchema {
        ICustomerRecord customers();
        ICountedOrderRecord orders();
    }

    private final IJDBEngine<IOrderSchema> seedingEngine = createEngine(IOrderSchema.class);

    @BeforeMethod
    public void before() {
        cleanupTables("order_items", "orders", "customers", "generated_int_id_table");
        seedingEngine.insertDB(db -> {
            db.customers().id(1).name("Ann");
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
        });
    }

    public void resetDB_keeps_the_rows_of_an_excluded_table() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("customers").build();

        engine.resetDB(db -> {
            db.orders().id(102).customer_id(1).order_date("2023-10-28");
        });

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann")
        ));
        assertTableValues(table("orders",
                columns("id"),
                row(102)
        ));
    }

    public void cleanupDB_does_not_restart_the_identity_of_an_excluded_table() {
        skipFor("Native auto-increment", DBMSType.SPANNER);
        IJDBEngine<IGeneratedIdSchema> plainEngine = createEngine(IGeneratedIdSchema.class);
        plainEngine.resetDB(db -> {
            db.generated_int_id_table().varchar_column("a");
            db.generated_int_id_table().varchar_column("b");
        });
        long highestIdBefore = generatedId("b");
        IJDBEngine<IGeneratedIdSchema> engine = engineBuilder(IGeneratedIdSchema.class)
                .excludeFromCleanup("generated_int_id_table").build();

        engine.cleanupDB();
        plainEngine.insertDB(db -> {
            db.generated_int_id_table().varchar_column("c");
        });

        assertThat(generatedId("c")).isGreaterThan(highestIdBefore);
    }

    public void cleanupDB_does_not_restart_the_identity_of_a_table_outside_the_schema() {
        skipFor("Native auto-increment", DBMSType.SPANNER);
        IJDBEngine<IGeneratedIdSchema> plainEngine = createEngine(IGeneratedIdSchema.class);
        plainEngine.resetDB(db -> {
            db.generated_int_id_table().varchar_column("a");
            db.generated_int_id_table().varchar_column("b");
        });
        long highestIdBefore = generatedId("b");

        seedingEngine.cleanupDB();
        plainEngine.insertDB(db -> {
            db.generated_int_id_table().varchar_column("c");
        });

        assertThat(generatedId("c")).isGreaterThan(highestIdBefore);
    }

    public void insertDB_into_an_excluded_table_fails_by_default() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("customers").build();

        assertThatThrownBy(() -> engine.insertDB(db -> {
            db.customers().id(2).name("Bob");
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("customers")
                .hasMessageContaining("excluded from cleanup");
        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann")
        ));
    }

    public void updateDB_of_an_excluded_table_fails_by_default() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("customers").build();

        assertThatThrownBy(() -> engine.updateDB(db -> {
            db.customers().id(1).name("Changed");
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("customers")
                .hasMessageContaining("excluded from cleanup");
        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann")
        ));
    }

    public void resetDB_writing_to_an_excluded_table_fails_before_cleanup() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("customers").build();

        assertThatThrownBy(() -> engine.resetDB(db -> {
            db.customers().id(2).name("Bob");
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("excluded from cleanup");
        assertTableValues(table("orders",
                columns("id"),
                row(101)
        ));
    }

    public void a_failed_resetDB_does_not_restart_id_counters() {
        IJDBEngine<ICountedOrderSchema> engine = engineBuilder(ICountedOrderSchema.class)
                .excludeFromCleanup("customers").build();
        engine.resetDB(db -> {
            db.orders();
            db.orders();
            db.orders();
        });

        assertThatThrownBy(() -> engine.resetDB(db -> {
            db.customers().id(2).name("Bob");
            db.orders();
        })).isInstanceOf(JDBScriptException.class);
        engine.insertDB(db -> {
            db.orders();
        });

        assertTableValues(table("orders",
                columns("id"),
                row(200),
                row(201),
                row(202),
                row(203)
        ));
    }

    public void writes_to_an_excluded_table_are_logged_and_allowed_with_LOG_WARN() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class)
                .excludeFromCleanup("customers")
                .excludedTableWriteStrategy(ExcludedTableWriteStrategy.LOG_WARN)
                .build();

        try (LogCapture log = new LogCapture(JDBEngine.class)) {
            engine.insertDB(db -> {
                db.customers().id(2).name("Bob");
            });

            assertThat(log.messagesAtLeast(Level.WARN))
                    .anyMatch(m -> m.contains("customers") && m.contains("excluded from cleanup"));
        }
        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann"),
                row(2, "Bob")
        ));
    }

    public void an_excluded_table_can_be_asserted_against() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("customers").build();

        engine.assertDBHas(db -> {
            db.customers().id(1).name("Ann");
        });
        engine.assertDBHasNot(db -> {
            db.customers().id(2);
        });
    }

    public void excluded_table_names_match_ignoring_case() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("CUSTOMERS").build();

        engine.cleanupDB();

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann")
        ));
    }

    public void an_excluded_table_not_declared_in_the_schema_fails_on_first_use() {
        IJDBEngine<IOrderSchema> engine = engineBuilder(IOrderSchema.class).excludeFromCleanup("custmers").build();

        assertThatThrownBy(engine::cleanupDB)
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("custmers") //misspelled
                .hasMessageContaining("IOrderSchema");
        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "Ann")
        ));
    }

    private long generatedId(String varcharValue) {
        return withResultSet("SELECT generated_id_column FROM generated_int_id_table WHERE varchar_column = '%s'"
                .formatted(varcharValue), (rs, columns, types) -> {
            rs.next();
            return rs.getLong(1);
        });
    }
}
