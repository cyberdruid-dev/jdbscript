package org.jdbscript;

/**
 * What {@code insertDB}, {@code resetDB} and {@code updateDB} do when a script writes to a table
 * excluded from cleanup (see {@link JDBEngine.Builder#excludeFromCleanup(String...)}): such writes
 * survive {@code cleanupDB} and can leak into later tests.
 */
public enum ExcludedTableWriteStrategy {
    /**
     * Write without reporting, apart from a DEBUG log message.
     */
    IGNORE,
    /**
     * Log a warning message and write.
     */
    LOG_WARN,
    /**
     * Throw an exception before anything is written.
     */
    FAIL
}
