package org.jdbscript.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * DuckDB isn't fully supported by Liquibase yet, so its schema is created by running a plain
 * SQL script instead of the shared changelog.
 */
class DuckdbSchemaInitStrategy implements ITestSchemaInitStrategy {
    private static final Logger log = LoggerFactory.getLogger(DuckdbSchemaInitStrategy.class);

    @Override
    public void initSchema(DataSource dataSource) {
        log.info("Skipping Liquibase for DuckDB as it is not fully supported yet. Running manual initialization.");
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("db/duckdb-schema.sql");
            if (is == null) {
                throw new IOException("Could not find db/duckdb-schema.sql");
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            int count = 0;
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                    count++;
                }
            }
            log.info("Initialized DuckDB schema with {} statements.", count);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }
}
