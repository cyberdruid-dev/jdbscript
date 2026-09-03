package org.jdbscript.utils;

import org.jdbscript.DBMSType;

/**
 * Selects the {@link ITestSchemaInitStrategy} for a given {@link DBMSType}, mirroring
 * {@code SqlExecutorStrategyFactory} on the main source side.
 */
class TestSchemaInitStrategyFactory {

    static ITestSchemaInitStrategy getStrategy(DBMSType dbmsType) {
        return switch (dbmsType) {
            case DUCKDB -> new DuckdbSchemaInitStrategy();
            case COCKROACHDB -> new CockroachDbSchemaInitStrategy();
            default -> new DefaultSchemaInitStrategy();
        };
    }
}
