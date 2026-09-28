package org.jdbscript.examples.springboot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The "system under test": an ordinary Spring-managed repository, with no knowledge of jdbscript
 * at all - it just runs a query through the {@link JdbcTemplate} Spring gave it.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findActiveUsernames() {
        return jdbcTemplate.queryForList(
                "SELECT username FROM users WHERE active = TRUE ORDER BY username",
                String.class);
    }
}
