package org.jdbscript.examples.testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The exact same jdbscript code as 01-quickstart's QuickstartTest - schema interface, resetDB
 * call, UserRepository - now pointed at a real PostgreSQL container instead of in-memory H2.
 * Nothing jdbscript-specific changes; only the DataSource's origin does.
 */
@Testcontainers
class TestcontainersTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static UserRepository userRepository;

    @BeforeAll
    static void createSchema() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
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
        // Arrange: seed the real Postgres container with jdbscript, exactly like 01-quickstart.
        engine.resetDB(db -> {
            db.users().id(1L).username("bob").email("bob@example.com").active(false);
            db.users().id(2L).username("alice").email("alice@example.com").active(true);
            db.users().id(3L).username("charlie").email("charlie@example.com").active(true);
        });

        // Act: call the real system under test.
        List<String> result = userRepository.findActiveUsernames();

        // Assert: on the SUT's return value.
        assertEquals(List.of("alice", "charlie"), result);
    }
}
