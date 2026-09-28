package org.jdbscript.examples.insertpower;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A static dataset format gives you one fixed snapshot per test. A jdbscript script is code, so
 * {@code insertDB} can add to what's there partway through a test — letting the test model a
 * sequence of events over time, not just a single state to assert against.
 */
class InsertPowerTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static NotificationInbox inbox;

    @BeforeAll
    static void createSchema() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:insertpower;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        dataSource = new HikariDataSource(config);

        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE notifications (
                        id INT PRIMARY KEY,
                        message VARCHAR(200)
                    )
                    """);
        }

        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .build();
        inbox = new NotificationInbox(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void insertDB_lets_a_test_simulate_new_data_arriving_mid_scenario() {
        // Arrange the starting state.
        engine.resetDB(db -> {
            db.notifications().id(1).message("Welcome!");
            db.notifications().id(2).message("Your order shipped.");
        });

        assertEquals(2, inbox.countMessages());
        assertEquals("Your order shipped.", inbox.latestMessage());

        // Simulate a new notification arriving while the scenario is "running" — insertDB adds
        // without wiping, so this reads as an event happening mid-test, not a second, unrelated
        // test with its own fixture.
        engine.insertDB(db -> {
            db.notifications().id(3).message("Price drop on an item you viewed!");
        });

        assertEquals(3, inbox.countMessages());
        assertEquals("Price drop on an item you viewed!", inbox.latestMessage());
    }
}
