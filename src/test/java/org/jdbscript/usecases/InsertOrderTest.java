package org.jdbscript.usecases;

import org.jdbscript.*;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Test
public class InsertOrderTest extends JdbAbstractTest {

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

    private interface ITestSchema extends IDBSchema {
        ICustomerRecord customers();
        IOrderRecord orders();
        IOrderItemRecord order_items();
    }

    private final IJDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @BeforeMethod
    public void before() {
        cleanupTables("order_items", "orders", "customers");
    }

    @Test
    public void test_reverse_order_insertion() {
        // Child records are added BEFORE parent records in the script
        // This would fail without automatic sorting due to FK constraints
        engine.insertDB(db -> {
            db.order_items().id(1001).order_id(101).product_name("Laptop").quantity(1);
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
            db.customers().id(1).name("John Doe");
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
    public void test_mixed_order_insertion() {
        engine.insertDB(db -> {
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
            db.customers().id(1).name("John Doe");
            db.order_items().id(1001).order_id(101).product_name("Laptop").quantity(1);
        });

        assertTableValues(table("customers",
                columns("id", "name"),
                row(1, "John Doe")
        ));
    }

    @Test
    public void test_row_order_preservation() {
        // Multiple rows for the same table should maintain their relative order
        // We use a mock executor to verify the script order without DB interference
        List<JDBRecord> capturedRecords = new ArrayList<>();
        IScriptExecutor mockExecutor = new SqlScriptExecutor() {
            @Override
            public void insert(JDBScript dbScript) {
                capturedRecords.addAll(dbScript.getRecords());
            }
            @Override
            public IMetadataProvider getMetadataProvider() {
                return new IMetadataProvider() {
                    @Override
                    public DBMSType getDbmsType() {
                        return DBMSType.H2;
                    }
                    @Override public List<String> getAllTables() { return List.of("customers", "orders", "order_items"); }
                    @Override public List<String> getSortedTables() { return List.of("customers", "orders", "order_items"); }
                    @Override public Comparator<String> getParentChildTableComparator() {
                        return Comparator.comparingInt(t -> {
                            int idx = getSortedTables().indexOf(t.toLowerCase());
                            return idx != -1 ? idx : getSortedTables().indexOf(t.toUpperCase());
                        });
                    }
                    @Override public List<String> sortTablesByDependencies(Collection<String> tableNames) {
                        return new ArrayList<>(tableNames);
                    }
                };
            }
        };

        IJDBEngine<ITestSchema> engineWithMock = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(mockExecutor)
                .build();

        engineWithMock.insertDB(db -> {
            db.customers().id(2).name("Jane Smith"); // First customer row
            db.orders().id(101).customer_id(2).order_date("2023-10-27");
            db.customers().id(1).name("John Doe");   // Second customer row
        });

        // Filter customer records and extract IDs
        List<Integer> customerIds = capturedRecords.stream()
                .filter(r -> "customers".equals(r.getTableName()))
                .map(r -> (Integer) r.getColumns().get("id"))
                .toList();

        Assert.assertEquals(customerIds, List.of(2, 1), "Rows within the same table should maintain their relative order");

        // Also verify customers come before orders in the final script
        int firstCustomerIndex = -1;
        int firstOrderIndex = -1;
        for (int i = 0; i < capturedRecords.size(); i++) {
            String tableName = capturedRecords.get(i).getTableName();
            if ("customers".equals(tableName) && firstCustomerIndex == -1) firstCustomerIndex = i;
            if ("orders".equals(tableName) && firstOrderIndex == -1) firstOrderIndex = i;
        }
        Assert.assertTrue(firstCustomerIndex < firstOrderIndex, "Parent tables (customers) should come before child tables (orders)");
    }
}
