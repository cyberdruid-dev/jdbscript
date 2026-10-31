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
 * Demonstrates domain helpers with {@code engine.as(...)}.
 * <p>
 * Plain record chains stay the default; a helper on a sub-interface of the schema handles derived
 * or multi-table data and returns the main record, so the test sets the values it depends on by name.
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
    void helper_keeps_order_totals_consistent_with_items() {
        IJDBEngine<ICustomerOrderDSL> dslEngine = engine.as(ICustomerOrderDSL.class);

        dslEngine.insertDB(db -> {
            db.customers().id(1L).name("Alice Smith").tier("VIP");

            db.orderWithItems(101L,
                    item("Mechanical Keyboard", 1, 120.00),
                    item("Wireless Mouse", 2, 40.00)
            ).customer_id(1L).status("COMPLETED");

            db.orderWithItems(102L,
                    item("Desk Mat", 1, 25.00)
            ).customer_id(1L).status("COMPLETED");
        });

        assertEquals(225.00, reportingService.calculateTotalSpent(1L), 0.001);
        assertEquals(4, reportingService.countTotalItemsPurchased(1L));
    }

    @Test
    void only_completed_orders_are_counted() {
        IJDBEngine<ICustomerOrderDSL> dslEngine = engine.as(ICustomerOrderDSL.class);

        dslEngine.insertDB(db -> {
            db.customers().id(2L).name("Bob Jones").tier("REGULAR");

            db.orderWithItems(201L,
                    item("USB-C Hub", 1, 50.00),
                    item("HDMI Cable", 2, 15.00)
            ).customer_id(2L).status("COMPLETED");

            db.orderWithItems(202L,
                    item("4K Monitor", 1, 400.00),
                    item("Monitor Stand", 1, 80.00)
            ).customer_id(2L).status("PENDING");
        });

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
