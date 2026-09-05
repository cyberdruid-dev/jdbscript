package org.jdbscript.usecases;

import org.jdbscript.*;
import org.jdbscript.IDBSchema.IDBRecord;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class CleanupOrderTest extends JdbAbstractTest {

    private interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(int value);
        ICustomerRecord name(String value);
    }

    private interface IOrderRecord extends IDBRecord {
        IOrderRecord id(int value);
        IOrderRecord customer_id(int value);
        IOrderRecord order_date(String value);
    }

    private interface IOrderItemRecord extends IDBRecord {
        IOrderItemRecord id(int value);
        IOrderItemRecord order_id(int value);
        IOrderItemRecord product_name(String value);
        IOrderItemRecord quantity(int value);
    }

    private interface IBaseSchema extends IDBSchema {
        IOrderItemRecord order_items();
        IOrderRecord orders();
    }

    private interface ITestSchema extends IBaseSchema {
        ICustomerRecord customers();
    }

    private final IJDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @BeforeMethod
    public void before() {
        cleanupTables("order_items", "orders", "customers");
    }

    @Test
    public void test_insert_data() {
        engine.insertDB(db -> {
            db.customers().id(1).name("John Doe");
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
            db.order_items().id(1001).order_id(101).product_name("Laptop").quantity(1);
        });

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "John Doe")
        ));
        assertTableValues(table("orders",
                columns("id", "customer_id", "order_date"),
                row(101, 1, "2023-10-27")
        ));
        assertTableValues(table("order_items",
                columns("id", "order_id", "product_name", "quantity"),
                row(1001, 101, "Laptop", 1)
        ));
    }

    @Test
    public void test_cleanup() {
        IJDBEngine<ITestSchema> engineWithOrder = JDBEngine.builder(ITestSchema.class)
                .dataSource(()->dataSource)
                .executor(testConfiguration.getScriptExecutor())
                .feature(JDBFeature.DB2_ID_OWNED_SEQUENCE_RESTART_WITH)
                .build();

        engineWithOrder.insertDB(db -> {
            db.customers().id(1).name("John Doe");
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
            db.order_items().id(1001).order_id(101).product_name("Laptop").quantity(1);
        });

        engineWithOrder.cleanupDB();

        assertTableEmpty("order_items");
        assertTableEmpty("orders");
        assertTableEmpty("customers");
    }
}
