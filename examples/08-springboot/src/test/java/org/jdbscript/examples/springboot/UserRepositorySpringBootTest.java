package org.jdbscript.examples.springboot;

import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * jdbscript needs nothing from Spring beyond the {@link DataSource} bean the test context already
 * wires up - here, an embedded H2 auto-configured by spring-boot-starter-jdbc, with its schema
 * loaded from src/test/resources/schema.sql. No Spring-specific integration code exists on
 * jdbscript's side; {@code engine.resetDB(...)} runs explicitly per test, the same way every other
 * example does, rather than relying on Spring's test-transaction rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserRepositorySpringBootTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    private IJDBEngine<IAppSchema> engine;

    @BeforeEach
    void seedDatabase() {
        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .build();

        engine.resetDB(db -> {
            db.users().id(1L).username("bob").email("bob@example.com").active(false);
            db.users().id(2L).username("alice").email("alice@example.com").active(true);
            db.users().id(3L).username("charlie").email("charlie@example.com").active(true);
        });
    }

    @Test
    void findActiveUsernames_returns_only_active_users_in_order() {
        // Act: call the real (Spring-managed) system under test.
        List<String> result = userRepository.findActiveUsernames();

        // Assert: on the SUT's return value.
        assertEquals(List.of("alice", "charlie"), result);
    }
}
