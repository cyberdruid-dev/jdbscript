package org.jdbscript.examples.converters;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The system under test: ordinary production code, with no knowledge of jdbscript at all. It
 * maps the raw {@code DECIMAL} column back into {@link Money} — the same repository-mapping work
 * it would do regardless of how the row got there.
 */
public class PriceCatalog {

    private final DataSource dataSource;

    public PriceCatalog(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Money priceOf(String productName) {
        String sql = "SELECT price FROM products WHERE name = ?";
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setString(1, productName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                BigDecimal price = rs.getBigDecimal(1);
                return new Money(price.movePointRight(2).longValueExact());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
