package org.jdbscript.examples.defaults;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The system under test: ordinary production code, with no knowledge of jdbscript at all.
 */
public class ProductCatalog {

    private final DataSource dataSource;

    public ProductCatalog(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String skuFor(String productName) {
        String sql = "SELECT sku FROM products WHERE name = ?";
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setString(1, productName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
