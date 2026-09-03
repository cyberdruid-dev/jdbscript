package org.jdbscript.examples.scripting;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The system under test: ordinary production code, with no knowledge of jdbscript at all.
 */
public class LeaderboardService {

    private final DataSource dataSource;

    public LeaderboardService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> topUsernames(int limit) {
        String sql = "SELECT username FROM players ORDER BY score DESC, username ASC LIMIT ?";
        try (Connection cnn = dataSource.getConnection();
             PreparedStatement stmt = cnn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int countPlayers() {
        String sql = "SELECT COUNT(*) FROM players";
        try (Connection cnn = dataSource.getConnection();
             Statement stmt = cnn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
