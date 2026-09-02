package org.jdbscript.impl.cache;

import org.jdbscript.CacheStrategy;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.CACHE_STRATEGY_IS_NULL;

public class JDBCacheManager {
    private static JDBCacheManager instance = new JDBCacheManager();

    public static JDBCacheManager getInstance() {
        return instance;
    }

    public IJDBCache getCache(CacheStrategy strategy) {
        checkNotNull(strategy, CACHE_STRATEGY_IS_NULL);
        return switch (strategy) {
            case NONE -> new NoCache();
            case INSTANCE -> new InstanceCache();
            case GLOBAL -> new GlobalCache();
        };
    }

}
