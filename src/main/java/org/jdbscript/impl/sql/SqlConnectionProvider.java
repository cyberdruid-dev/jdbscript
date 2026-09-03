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
    // Reused instead of a plain ThreadLocal<Connection> so that a nested withConnection() call on
    // the same thread (e.g. a metadata lookup triggered while an insert's connection is still open)
    // reuses that connection instead of acquiring a second one from the same DataSource/pool, which
    // can exhaust/deadlock a pool sized to 1. onConnection()/commit() still run exactly once, for
    // the outermost caller only.
    private final ReentrantResource<Connection> connectionResource = new ReentrantResource<>(this::acquireConnection);

    @FunctionalInterface
    public interface JdbcConnectionConsumer {

        void accept(Connection cnn) throws Exception;

    }

    public SqlConnectionProvider(DataSource dataSource) {
        this.dataSource = checkNotNull(dataSource, DATASOURCE_IS_NULL);

    }

    private Connection acquireConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setStrategy(ISqlExecutorStrategy strategy) {
        this.strategy = strategy;
    }

    private ISqlExecutorStrategy getStrategy(Connection cnn) throws SQLException {
        if (strategy == null) {
            strategy = SqlExecutorStrategyFactory.getStrategy(DBMSType.getType(cnn.getMetaData()));
        }
        return strategy;
    }

    public void withConnection(JdbcConnectionConsumer consumer) {
        IReentrantResourceCallback<Connection> lifecycle = new IReentrantResourceCallback<>() {
            @Override
            public void afterOpen(Connection cnn) throws SQLException {
                getStrategy(cnn).onConnection(cnn);
            }

            @Override
            public void beforeClose(Connection cnn) throws SQLException {
                getStrategy(cnn).commit(cnn);
            }
        };

        try {
            connectionResource.run(consumer::accept, lifecycle);
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
