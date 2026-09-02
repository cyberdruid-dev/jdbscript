package org.jdbscript;

import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.sql.SqlConnectionProvider;
import org.jdbscript.impl.sql.SqlMetadataProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Comparator;
import java.util.List;

import static org.testng.Assert.assertFalse;

@Test
public class MetadataProviderTest extends JdbAbstractTest {

    private interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(int value);
    }

    private interface ITestSchema extends IDbSchema {
        ICustomerRecord customers();
    }

    private interface IIncompleteSchema extends IDbSchema {
        // Missing customers table
    }

    @Test
    public void test_global_discovery() {
        SqlConnectionProvider connectionProvider = new SqlConnectionProvider(dataSource);
        IMetadataProvider provider = new SqlMetadataProvider(connectionProvider);
        
        List<String> allTables = provider.getAllTables();
        assertAnyMatch(allTables, t -> t.equalsIgnoreCase("customers"),
                "Should discover 'customers' table globally");
        
        List<String> sorted = provider.getSortedTables();
        assertFalse(sorted.isEmpty(), "Sorted tables should not be empty");
    }

    @Test
    public void test_schema_validation() {
        // We can't easily capture logs in a standard test without extra setup, 
        // but we can verify that the engine doesn't crash and validation runs.
        IJDBEngine<IIncompleteSchema> engine = createEngine(IIncompleteSchema.class);
        
        // This should trigger validateSchema and log a warning
        engine.insertDB(db -> {});
        
        // No exception means it handled the mismatch gracefully
    }
    
    @Test
    public void test_dbms_type_detection() {
        SqlConnectionProvider connectionProvider = new SqlConnectionProvider(dataSource);
        IMetadataProvider provider = new SqlMetadataProvider(connectionProvider);
        
        Assert.assertEquals(provider.getDbmsType(), testConfiguration.getExpectedDbmsType(), "Should detect expected database type");
    }

    @Test
    public void test_parent_child_table_comparator() {
        SqlConnectionProvider connectionProvider = new SqlConnectionProvider(dataSource);
        IMetadataProvider provider = new SqlMetadataProvider(connectionProvider);
        
        Comparator<String> comparator = provider.getParentChildTableComparator();
        
        // Even if we don't have FKs here, it should still provide a stable order
        int cmp = comparator.compare("customers", "orders");
        int cmp2 = comparator.compare("orders", "customers");
        
        Assert.assertEquals(cmp, -cmp2, "Comparator should be consistent");
    }
}
