package org.jdbscript.examples.quickstart;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pattern most jdbscript tests actually follow: arrange DB state with jdbscript, act by
 * calling the real system under test ({@link UserRepository}), then assert on what it returns —
 * plain JUnit/AssertJ, nothing jdbscript-specific.
 * <p>
 * jdbscript also has {@code assertDBHas}/{@code assertDBHasNot} for asserting DB state directly,
 * but reach for them rarely — only when nothing in your own code exposes what you need to check
 * (e.g. a DB trigger's side effect). Prefer asserting on your code's behavior, like both tests
 * below do.
 */
class QuickstartTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static UserRepository userRepository;

    @BeforeAll
    static void createSchema() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:quickstart;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        dataSource = new HikariDataSource(config);

        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE users (
                        id BIGINT PRIMARY KEY,
                        username VARCHAR(100),
                        email VARCHAR(255),
                        active BOOLEAN
                    )
                    """);
        }

        // .dataSource(...) also accepts a Supplier<DataSource> for lazy resolution
        // (e.g. from a Spring context) — see the README's "Other Useful Options".
        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .build();
        userRepository = new UserRepository(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void findActiveUsernames_returns_only_active_users_in_order() {
        // Arrange: seed with jdbscript. resetDB wipes the table first, so each test starts clean.
        engine.resetDB(db -> {
            db.users().id(1L).username("bob").email("bob@example.com").active(false);
            db.users().id(2L).username("alice").email("alice@example.com").active(true);
            db.users().id(3L).username("charlie").email("charlie@example.com").active(true);
        });

        // Act: call the real system under test.
        List<String> result = userRepository.findActiveUsernames();

        // Assert: on the SUT's return value, not on DB state directly.
        assertEquals(List.of("alice", "charlie"), result);
    }

    @Test
    void insertDB_adds_a_user_visible_to_the_next_call() {
        engine.resetDB(db -> {
            db.users().id(1L).username("alice").email("alice@example.com").active(true);
        });
        assertEquals(List.of("alice"), userRepository.findActiveUsernames());

        // Unlike resetDB, insertDB doesn't clean up first — it just adds to what's there.
        engine.insertDB(db -> {
            db.users().id(2L).username("dave").email("dave@example.com").active(true);
        });

        assertEquals(List.of("alice", "dave"), userRepository.findActiveUsernames());
    }
}
