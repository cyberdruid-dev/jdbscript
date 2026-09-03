package org.jdbscript.impl.sql;

import org.jdbscript.DBMSType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JDBErrors.DATASOURCE_IS_NULL;

public class SqlConnectionProvider {
    private static final Logger log = LoggerFactory.getLogger(SqlConnectionProvider.class);

    private final DataSource dataSource;
    private ISqlExecutorStrategy strategy;

    @FunctionalInterface
    public interface JdbcConnectionConsumer {

        void accept(Connection cnn) throws Exception;

    }

    public SqlConnectionProvider(DataSource dataSource) {
        this.dataSource = checkNotNull(dataSource, DATASOURCE_IS_NULL);

    }

    public void setStrategy(ISqlExecutorStrategy strategy) {
        this.strategy = strategy;
    }

    private ISqlExecutorStrategy getStrategy(Connection cnn) throws SQLException {
        if (strategy == null) {
            return SqlExecutorStrategyFactory.getStrategy(DBMSType.getType(cnn.getMetaData()));
        }
        return strategy;
    }

    public void withConnection(JdbcConnectionConsumer consumer) {
        try(Connection cnn = dataSource.getConnection()) {
            ISqlExecutorStrategy currentStrategy = getStrategy(cnn);
            currentStrategy.onConnection(cnn);
            consumer.accept(cnn);
            currentStrategy.commit(cnn);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void withPreparedStatements(JdbcSessionConsumer consumer) {
        withConnection(cnn -> {
            Map<String, PreparedStatement> stmts = new HashMap<>();
            try {
                consumer.accept(cnn, sql -> {
                    try {
                        return stmts.computeIfAbsent(sql, k -> {
                            try {
                                return cnn.prepareStatement(k);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof SQLException sqle) {
                            throw sqle;
                        }
                        throw e;
                    }
                });
            } finally {
                closeAll(stmts);
            }
        });
    }

    private void closeAll(Map<String, PreparedStatement> stmts) {
        for (PreparedStatement stmt : stmts.values()) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.warn("Failed to close statement", e);
            }
        }
    }

    public interface PreparedStatementProvider {
        PreparedStatement get(String sql) throws SQLException;
    }

    @FunctionalInterface
    public interface JdbcSessionConsumer {
        void accept(Connection cnn, PreparedStatementProvider stmtProvider) throws Exception;
    }

}
