package org.jdbscript.examples.classscripts;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The system under test: ordinary production code, with no knowledge of jdbscript at all.
 */
public class OrderService {

    private final DataSource dataSource;

    public OrderService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public double totalSpentBy(String username) {
        String sql = """
                SELECT COALESCE(SUM(o.total_amount), 0)
                FROM orders o
                JOIN users u ON u.id = o.user_id
                WHERE u.username = ?
                """;
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
