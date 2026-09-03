package org.jdbscript.examples.insertpower;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The system under test: ordinary production code, with no knowledge of jdbscript at all.
 */
public class NotificationInbox {

    private final DataSource dataSource;

    public NotificationInbox(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int countMessages() {
        String sql = "SELECT COUNT(*) FROM notifications";
        try (Connection cnn = dataSource.getConnection();
             Statement stmt = cnn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String latestMessage() {
        String sql = "SELECT message FROM notifications ORDER BY id DESC LIMIT 1";
        try (Connection cnn = dataSource.getConnection();
             Statement stmt = cnn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
