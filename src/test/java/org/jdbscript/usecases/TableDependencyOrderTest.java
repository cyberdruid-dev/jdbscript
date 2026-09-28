package org.jdbscript.usecases;

import org.jdbscript.*;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class TableDependencyOrderTest extends JdbAbstractTest {

    private interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(int value);
    }

    private interface IOrderRecord extends IDBRecord {
        IOrderRecord id(int value);
    }

    private interface IOrderItemRecord extends IDBRecord {
        IOrderItemRecord id(int value);
    }

    private interface ITestSchema extends IDBSchema {
        ICustomerRecord customers();
        IOrderRecord orders();
        IOrderItemRecord order_items();
    }

    /**
     * A metadata provider whose auto-detected ordering methods throw if called - proves that a
     * configured tableDependencyOrder is what actually drove the result, not auto-detection
     * happening to agree with it.
     */
    private static IMetadataProvider poisonedAutoDetectionProvider() {
        return new IMetadataProvider() {
            @Override
            public DBMSType getDbmsType() {
                return DBMSType.H2;
            }

            @Override
            public List<String> getAllTables() {
                return List.of("customers", "orders", "order_items");
            }

            @Override
            public List<String> getSortedTables() {
                throw new AssertionError("auto-detected order should not be consulted when tableDependencyOrder is set");
            }

            @Override
            public Comparator<String> getParentChildTableComparator() {
                throw new AssertionError("auto-detected comparator should not be consulted when tableDependencyOrder is set");
            }

            @Override
            public List<String> sortTablesByDependencies(Collection<String> tableNames) {
                throw new AssertionError("auto-detected sort should not be consulted when tableDependencyOrder is set");
            }
        };
    }

    @Test
    public void insertDB_should_use_the_manual_order_instead_of_auto_detection() {
        List<JDBRecord> captured = new ArrayList<>();
        IScriptExecutor mockExecutor = new SqlScriptExecutor() {
            @Override
            public void insert(JDBScript dbScript) {
                captured.addAll(dbScript.getRecords());
            }

            @Override
            public IMetadataProvider getMetadataProvider() {
                return poisonedAutoDetectionProvider();
            }
        };

        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(mockExecutor)
                .tableDependencyOrder(List.of("customers", "orders", "order_items"))
                .build();

        // Deliberately out of order in the script - only the manual order can fix this up.
        engine.insertDB(db -> {
            db.order_items().id(1001);
            db.orders().id(101);
            db.customers().id(1);
        });

        List<String> insertedOrder = captured.stream().map(JDBRecord::getTableName).distinct().toList();
        assertThat(insertedOrder).containsExactly("customers", "orders", "order_items");
    }

    @Test
    public void cleanupDB_should_use_the_reverse_of_the_manual_order() {
        List<String> cleaned = new ArrayList<>();
        IScriptExecutor mockExecutor = new SqlScriptExecutor() {
            @Override
            public void cleanupTables(List<String> tableNames) {
                cleaned.addAll(tableNames);
            }

            @Override
            public IMetadataProvider getMetadataProvider() {
                return poisonedAutoDetectionProvider();
            }
        };

        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(mockExecutor)
                .tableDependencyOrder(List.of("customers", "orders", "order_items"))
                .build();

        engine.cleanupDB();

        assertThat(cleaned).containsExactly("order_items", "orders", "customers");
    }

    @Test
    public void tableDependencyOrder_missing_an_interface_table_fails_on_first_use() {
        // Validated lazily, on first use - not eagerly at build() - to keep the engine lazy.
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(testConfiguration.getScriptExecutor())
                .tableDependencyOrder(List.of("customers", "orders"))
                .build();

        assertThatThrownBy(engine::cleanupDB)
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("order_items");
    }

    @Test
    public void tableDependencyOrder_is_case_insensitive_and_ignores_extra_tables() {
        IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> dataSource)
                .executor(testConfiguration.getScriptExecutor())
                .tableDependencyOrder(List.of("CUSTOMERS", "Orders", "order_items", "some_other_table"))
                .build();

        assertThatCode(engine::cleanupDB).doesNotThrowAnyException();
    }
}
