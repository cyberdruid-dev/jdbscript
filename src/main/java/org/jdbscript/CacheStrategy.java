package org.jdbscript;

/**
 * Strategy for caching database metadata (e.g. table lists, column info, FK dependencies)
 * collected by a {@link JDBEngine}.
 * <p>
 * Set via {@link JDBEngine.Builder#cacheStrategy(CacheStrategy)}. Default is {@link #INSTANCE}.
 */
public enum CacheStrategy {
    /**
     * Disable caching. Metadata is recomputed on every access.
     * <p>
     * Recommended if the database schema changes between tests (e.g. dynamic migrations).
     */
    NONE,
    /**
     * Cache metadata for the lifetime of the owning {@link JDBEngine} instance.
     * <p>
     * This is the default.
     */
    INSTANCE,
    /**
     * Cache metadata globally, shared across all {@link JDBEngine} instances pointing at the
     * same database.
     * <p>
     * Recommended if the database schema is static throughout the test suite.
     */
    GLOBAL
}
