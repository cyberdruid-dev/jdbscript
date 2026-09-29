package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.RecordTools;
import org.jdbscript.db.IOrderRecord;
import org.jdbscript.db.IOrderSchema;
import org.jdbscript.db.ITable1Record;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class SchemaViewTest extends JdbAbstractTest {

    public interface ICustomerScript extends IOrderSchema {
        default IOrderRecord addCustomerWithOrder(int id) {
            customers().id(id).name("customer " + id);
            return orders().id(id * 10).customer_id(id);
        }
    }

    public interface IOrderItemScript extends ICustomerScript {
        default void addItem(int orderId, String product) {
            order_items().id(orderId + 1).order_id(orderId).product_name(product).quantity(1);
        }
    }

    private interface IPrivateScript extends IOrderSchema {
        default void addCustomer(int id) {
            customers().id(id).name("private " + id);
        }
    }

    public interface IExtraTableScript extends IOrderSchema {
        ITable1Record table_1();
    }

    public static abstract class CustomerClassScript implements ICustomerScript {{
        addCustomerWithOrder(2);
    }}

    public static abstract class SharedClassScript implements IOrderSchema {{
        customers().id(6).name("shared");
    }}

    public static abstract class PlainClassScript implements IOrderSchema {{
        customers().id(3).name("plain class");
    }}

    public interface IIdRecord extends IDBRecord {
        IIdRecord id(int value);
        IIdRecord str_column_1(String value);
        default void defaults(RecordTools tools) {
            id(tools.nextIntId("id", 1));
        }
    }
    public interface IIdSchema extends IDBSchema {
        IIdRecord table_with_defaults();
    }
    public interface IIdScript extends IIdSchema {
        default void addRow(String value) {
            table_with_defaults().str_column_1(value);
        }
    }

    private final JDBEngine<IOrderSchema> engine = createEngine(IOrderSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables("order_items", "orders", "customers", "table_with_defaults");
    }

    public void view_lambda_should_call_default_helpers_of_the_sub_interface() {
        engine.as(ICustomerScript.class).resetDB(db -> db.addCustomerWithOrder(1));

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "customer 1")
        ));
        assertTableValues(table("orders",
                columns("id", "customer_id"),
                row(10, 1)
        ));
    }

    public void setters_chained_on_a_helper_result_should_be_applied() {
        engine.as(ICustomerScript.class).resetDB(db -> db.addCustomerWithOrder(1).order_date("2026-01-01"));

        assertTableValues(table("orders",
                columns("id", "order_date"),
                row(10, "2026-01-01")
        ));
    }

    public void view_should_share_id_counters_with_its_engine() {
        JDBEngine<IIdSchema> idEngine = createEngine(IIdSchema.class);
        IJDBEngine<IIdScript> view = idEngine.as(IIdScript.class);

        idEngine.resetDB(db -> {
            db.table_with_defaults().str_column_1("plain");
            db.table_with_defaults().str_column_1("plain");
        });
        view.insertDB(db -> db.addRow("view"));

        assertTableValues(table("table_with_defaults",
                columns("id", "str_column_1"),
                row(1, "plain"),
                row(2, "plain"),
                row(3, "view")
        ));

        view.cleanupDB();
        view.insertDB(db -> db.addRow("after cleanup"));
        idEngine.insertDB(db -> db.table_with_defaults().str_column_1("plain after cleanup"));

        assertTableValues(table("table_with_defaults",
                columns("id", "str_column_1"),
                row(1, "after cleanup"),
                row(2, "plain after cleanup")
        ));
    }

    public void view_should_update_and_assert() {
        IJDBEngine<ICustomerScript> view = engine.as(ICustomerScript.class);
        view.resetDB(db -> db.addCustomerWithOrder(1));
        view.assertDBHas(db -> db.addCustomerWithOrder(1));
        view.assertDBHasNot(db -> db.addCustomerWithOrder(2));

        view.updateDB(db -> db.customers().id(1).name("changed"));

        view.assertDBHas(db -> db.customers().id(1).name("changed"));
    }

    public void view_cleanupDB_should_clean_every_table_of_the_engine_schema() {
        engine.resetDB(db -> {
            db.customers().id(1).name("one");
            db.orders().id(10).customer_id(1);
            db.order_items().id(11).order_id(10).product_name("book").quantity(1);
        });

        engine.as(ICustomerScript.class).cleanupDB();

        assertTableEmpty("order_items");
        assertTableEmpty("orders");
        assertTableEmpty("customers");
    }

    public void view_operations_should_fire_data_change_listeners() {
        AtomicInteger count = new AtomicInteger();
        IJDBEngine<ICustomerScript> view = engineBuilder(IOrderSchema.class)
                .onDataChange(count::incrementAndGet)
                .build()
                .as(ICustomerScript.class);

        view.resetDB(db -> db.addCustomerWithOrder(1));
        view.updateDB(db -> db.customers().id(1).name("changed"));
        view.cleanupDB();

        assertThat(count).hasValue(3);
    }

    public void view_should_run_class_scripts_of_the_sub_interface() {
        engine.as(ICustomerScript.class).resetDB(CustomerClassScript.class);

        assertTableValues(table("customers",
                columns("id", "name"),
                row(2, "customer 2")
        ));
    }

    public void view_lambda_should_include_class_scripts_and_plain_lambdas() {
        Consumer<IOrderSchema> plainLambda = db -> db.customers().id(4).name("plain lambda");

        engine.as(ICustomerScript.class).resetDB(db -> {
            db.include(PlainClassScript.class);
            db.include(plainLambda);
            db.addCustomerWithOrder(1);
        });

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "customer 1"),
                row(3, "plain class"),
                row(4, "plain lambda")
        ));
    }

    public void view_of_a_view_should_see_helpers_of_both_interfaces() {
        engine.as(ICustomerScript.class).as(IOrderItemScript.class).resetDB(db -> {
            db.addCustomerWithOrder(1);
            db.addItem(10, "book");
        });

        assertTableValues(table("order_items",
                columns("order_id", "product_name"),
                row(10, "book")
        ));
    }

    public void view_should_reject_a_sub_interface_that_adds_a_table() {
        assertThatThrownBy(() -> engine.as(IExtraTableScript.class))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("IExtraTableScript")
                .hasMessageContaining("table_1()")
                .hasMessageContaining("IOrderSchema");
    }

    public void view_should_work_with_a_non_public_sub_interface() {
        engine.as(IPrivateScript.class).resetDB(db -> db.addCustomer(5));

        assertTableValues(table("customers",
                columns("id", "name"),
                row(5, "private 5")
        ));
    }

    public void class_script_should_work_from_the_plain_engine_after_a_view_used_it() {
        engine.as(ICustomerScript.class).resetDB(db -> db.include(SharedClassScript.class));

        engine.resetDB(SharedClassScript.class);

        assertTableValues(table("customers",
                columns("id", "name"),
                row(6, "shared")
        ));
    }
}
