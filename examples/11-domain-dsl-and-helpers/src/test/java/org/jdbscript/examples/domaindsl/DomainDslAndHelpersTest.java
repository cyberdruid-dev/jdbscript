package org.jdbscript.examples.domaindsl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import static org.jdbscript.examples.domaindsl.ICustomerOrderDSL.Item.item;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demonstrates building type-safe Domain DSLs with {@code engine.as(...)}.
 * <p>
 * Extending the base schema interface with domain helper methods allows test authors to create
 * multi-table aggregates (e.g. customers, orders, and nested order items) in a single declarative
 * statement without repetitive boilerplate.
 */
class DomainDslAndHelpersTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static CustomerReportingService reportingService;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        dataSource = createDataSource();
        createTables(dataSource, SCHEMA_DDL);
        engine = JDBEngine.builder(IAppSchema.class).dataSource(dataSource).build();
        reportingService = new CustomerReportingService(dataSource);
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
    }

    @BeforeEach
    void cleanDatabase() {
        engine.cleanupDB();
    }

    @Test
    void domain_dsl_creates_multi_table_aggregates_cleanly() {
        // engine.as(...) provides a view with domain-specific helper methods
        IJDBEngine<ICustomerOrderDSL> dslEngine = engine.as(ICustomerOrderDSL.class);

        // Arrange: set up a customer with two orders and multiple items in a clean, declarative block
        dslEngine.insertDB(db -> {
            db.addCustomer(1L, "Alice Smith", "VIP");

            db.addOrderWithItems(
                    101L, 1L, "ORD-101", "COMPLETED",
                    item("Mechanical Keyboard", 1, 120.00),
                    item("Wireless Mouse", 2, 40.00)
            );

            db.addOrderWithItems(
                    102L, 1L, "ORD-102", "COMPLETED",
                    item("Desk Mat", 1, 25.00)
            );
        });

        // Act & Assert on the real system under test
        assertEquals(225.00, reportingService.calculateTotalSpent(1L), 0.001);
        assertEquals(4, reportingService.countTotalItemsPurchased(1L));
    }

    @Test
    void domain_dsl_handles_filtered_order_statuses() {
        IJDBEngine<ICustomerOrderDSL> dslEngine = engine.as(ICustomerOrderDSL.class);

        // Arrange: one completed order and one pending order (each with multiple items)
        dslEngine.insertDB(db -> {
            db.addCustomer(2L, "Bob Jones", "REGULAR");

            db.addOrderWithItems(
                    201L, 2L, "ORD-201", "COMPLETED",
                    item("USB-C Hub", 1, 50.00),
                    item("HDMI Cable", 2, 15.00)
            );

            db.addOrderWithItems(
                    202L, 2L, "ORD-202", "PENDING",
                    item("4K Monitor", 1, 400.00),
                    item("Monitor Stand", 1, 80.00)
            );
        });

        // Act & Assert: reporting service only considers COMPLETED orders
        assertEquals(80.00, reportingService.calculateTotalSpent(2L), 0.001);
        assertEquals(3, reportingService.countTotalItemsPurchased(2L));
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:domaindsl;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        return new HikariDataSource(config);
    }

    private static void createTables(DataSource dataSource, String ddl) throws SQLException {
        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    private static final String SCHEMA_DDL = """
            CREATE TABLE customers (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100),
                email VARCHAR(255),
                tier VARCHAR(50)
            );
            CREATE TABLE orders (
                id BIGINT PRIMARY KEY,
                customer_id BIGINT REFERENCES customers(id),
                order_number VARCHAR(50),
                status VARCHAR(50),
                total_amount DOUBLE
            );
            CREATE TABLE order_items (
                id BIGINT PRIMARY KEY,
                order_id BIGINT REFERENCES orders(id),
                product_name VARCHAR(100),
                quantity INT,
                unit_price DOUBLE
            )
            """;
}
