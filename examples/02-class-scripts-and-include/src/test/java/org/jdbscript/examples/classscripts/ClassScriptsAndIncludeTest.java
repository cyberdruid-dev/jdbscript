package org.jdbscript.examples.classscripts;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demonstrates running a reusable class-based script ({@link BaseUsersFixture}) directly,
 * composing it with test-specific rows via {@code db.include(...)}, and tweaking rows in-place
 * with {@code updateDB(...)}.
 */
class ClassScriptsAndIncludeTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static OrderService orderService;

    @BeforeAll
    static void createSchema() throws SQLException {
        dataSource = createDataSource();
        createTables(dataSource, SCHEMA_DDL);
        engine = JDBEngine.builder(IAppSchema.class).dataSource(dataSource).build();
        orderService = new OrderService(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void running_the_base_fixture_directly_leaves_no_orders() {
        // A class-based script can be run on its own, with no lambda at all.
        engine.resetDB(BaseUsersFixture.class);

        assertEquals(0.0, orderService.totalSpentBy("admin"));
    }

    @Test
    void include_composes_the_base_fixture_with_test_specific_rows() {
        engine.resetDB(db -> {
            // Reuse the same base dataset every test here starts from...
            db.include(BaseUsersFixture.class);
            // ...then add whatever this specific test actually needs.
            db.orders().id(100L).user_id(1L).total_amount(49.99);
            db.orders().id(101L).user_id(1L).total_amount(10.00);
        });

        assertEquals(59.99, orderService.totalSpentBy("admin"), 0.001);
        assertEquals(0.0, orderService.totalSpentBy("guest"));
    }

    @Test
    void updateDB_modifies_existing_user_attributes_without_reinserting() {
        // Arrange: start with the shared base fixture and an order for 'admin' (id=1L)
        engine.resetDB(db -> {
            db.include(BaseUsersFixture.class);
            db.orders().id(100L).user_id(1L).total_amount(50.00);
        });

        // Tweak one row in-place: update user 1's username without re-seeding the entire schema
        engine.updateDB(db -> {
            db.users().id(1L).username("superadmin");
        });

        // Act & Assert: OrderService joins on the updated username
        assertEquals(50.00, orderService.totalSpentBy("superadmin"), 0.001);
        assertEquals(0.0, orderService.totalSpentBy("admin"), 0.001);
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:classscripts;DB_CLOSE_DELAY=-1");
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
            CREATE TABLE users (
                id BIGINT PRIMARY KEY,
                username VARCHAR(100),
                email VARCHAR(255),
                active BOOLEAN
            );
            CREATE TABLE orders (
                id BIGINT PRIMARY KEY,
                user_id BIGINT REFERENCES users(id),
                total_amount DOUBLE
            )
            """;
}
