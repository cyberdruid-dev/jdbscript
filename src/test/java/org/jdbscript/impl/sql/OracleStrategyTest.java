package org.jdbscript.impl.sql;

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OracleStrategyTest {

    @Test
    public void resetSequences_should_restart_standalone_sequence_at_10000() throws SQLException {
        Statement stmt = statementWithStandaloneSequence("SEQ1");

        new OracleStrategy().resetSequences(connection(stmt), List.of());

        verify(stmt).executeUpdate("ALTER SEQUENCE SEQ1 RESTART START WITH 10000");
    }

    @Test
    public void resetSequences_should_fail_clearly_when_restart_is_not_supported() throws SQLException {
        Statement stmt = statementWithStandaloneSequence("SEQ1");
        when(stmt.executeUpdate(anyString())).thenThrow(new SQLException("ORA-02286: no options specified for ALTER SEQUENCE"));

        assertThatThrownBy(() -> new OracleStrategy().resetSequences(connection(stmt), List.of()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("SEQ1")
                .hasMessageContaining("Oracle 18c")
                .hasMessageContaining("ORA-02286");
    }

    private static Statement statementWithStandaloneSequence(String sequenceName) throws SQLException {
        ResultSet noIdentityColumns = mock(ResultSet.class);
        ResultSet sequences = mock(ResultSet.class);
        when(sequences.next()).thenReturn(true, false);
        when(sequences.getString("SEQUENCE_NAME")).thenReturn(sequenceName);

        Statement stmt = mock(Statement.class);
        when(stmt.executeQuery(contains("USER_TAB_IDENTITY_COLS"))).thenReturn(noIdentityColumns);
        when(stmt.executeQuery(contains("user_sequences"))).thenReturn(sequences);
        return stmt;
    }

    private static Connection connection(Statement stmt) throws SQLException {
        Connection cnn = mock(Connection.class);
        when(cnn.createStatement()).thenReturn(stmt);
        return cnn;
    }
}
