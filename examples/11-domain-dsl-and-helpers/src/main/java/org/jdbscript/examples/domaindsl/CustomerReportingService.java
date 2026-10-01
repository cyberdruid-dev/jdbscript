package org.jdbscript.examples.domaindsl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerReportingService {

    private final DataSource dataSource;

    public CustomerReportingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public double calculateTotalSpent(long customerId) {
        String sql = "SELECT COALESCE(SUM(total_amount), 0.0) FROM orders WHERE customer_id = ? AND status = 'COMPLETED'";
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setLong(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
                return 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate total spent", e);
        }
    }

    public int countTotalItemsPurchased(long customerId) {
        String sql = """
                SELECT COALESCE(SUM(oi.quantity), 0)
                FROM order_items oi
                JOIN orders o ON oi.order_id = o.id
                WHERE o.customer_id = ? AND o.status = 'COMPLETED'
                """;
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setLong(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count total items purchased", e);
        }
    }
}
