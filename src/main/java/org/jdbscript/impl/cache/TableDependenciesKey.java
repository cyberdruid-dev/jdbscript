package org.jdbscript.impl.cache;

import java.util.Set;

/**
 * Cache key for table dependencies.
 */
public record TableDependenciesKey(String tableName) implements IJDBCache.IJDBCacheKey<Set<String>> {
}
