package org.jdbscript.examples.quickstart;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The "system under test": ordinary production code, with no knowledge of jdbscript at all.
 * jdbscript's job stops at seeding the {@code users} table before a test runs — everything from
 * here on is just plain JDBC.
 */
public class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> findActiveUsernames() {
        List<String> usernames = new ArrayList<>();
        String sql = "SELECT username FROM users WHERE active = TRUE ORDER BY username";
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return usernames;
    }
}
