package org.jdbscript;

/**
 * Strategy for handling discrepancies between the database schema and the Java interface.
 */
public enum ValidationStrategy {
    /**
     * Log a warning message and continue execution.
     */
    LOG_WARN,
    /**
     * Log an error message and continue execution.
     */
    LOG_ERROR,
    /**
     * Throw an exception and stop execution.
     */
    FAIL
}
