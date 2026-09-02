package org.jdbscript.impl.cache;
 
import org.jdbscript.CacheStrategy;
import org.jdbscript.errors.JDBScriptException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.CACHE_STRATEGY_IS_NULL;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_IS_NULL;

public class JDBCacheManager {
    private static JDBCacheManager instance = new JDBCacheManager();

    private final Map<DatabaseCacheKey, InstanceCache> globalCaches = new ConcurrentHashMap<>();

    public static JDBCacheManager getInstance() {
        return instance;
    }

    public IJDBCache getCache(CacheStrategy strategy, DataSource dataSource) {
        checkNotNull(strategy, CACHE_STRATEGY_IS_NULL);
        checkNotNull(dataSource, DATASOURCE_IS_NULL);
        return switch (strategy) {
            case NONE -> new NoCache();
            case INSTANCE -> new InstanceCache();
            case GLOBAL -> getCacheByDataSource(dataSource);
        };
    }

    private IJDBCache getCacheByDataSource(DataSource dataSource) {
        DatabaseCacheKey key = getDataSourceKey(dataSource);
        return globalCaches.computeIfAbsent(key, k -> new InstanceCache());
    }

    private DatabaseCacheKey getDataSourceKey(DataSource dataSource) {
        try (Connection cnn = dataSource.getConnection()){
            return DatabaseCacheKey.from(cnn);
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to identify database for global caching.", e);
        }
    }

}
