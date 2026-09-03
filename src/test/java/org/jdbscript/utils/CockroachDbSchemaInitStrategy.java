package org.jdbscript.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs the shared Liquibase changelog, then fixes up columns Liquibase mapped incorrectly for
 * CockroachDB.
 * <p>
 * Liquibase has no CockroachDB-specific dialect registered, so a CockroachDB connection (made
 * over the PostgreSQL JDBC driver) is treated as plain PostgreSQL and its abstract "blob" column
 * type is mapped to {@code oid} (a PostgreSQL large-object reference). CockroachDB doesn't
 * implement PostgreSQL large objects, so an {@code oid} column can't actually hold blob bytes
 * there. Convert it to {@code BYTES} (CockroachDB's native binary type, aka {@code bytea}) after
 * migration, to match how {@code CockroachDBStrategy} reads and writes blob columns.
 */
class CockroachDbSchemaInitStrategy extends DefaultSchemaInitStrategy {

    @Override
    public void initSchema(DataSource dataSource) {
        super.initSchema(dataSource);
        fixupBlobColumns(dataSource);
    }

    private void fixupBlobColumns(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            // CockroachDB doesn't support an implicit OID -> BYTES cast, so ALTER ... TYPE fails;
            // drop and recreate the column instead (the table is freshly migrated and empty).
            stmt.execute("ALTER TABLE blob_table DROP COLUMN blob_column");
            stmt.execute("ALTER TABLE blob_table ADD COLUMN blob_column BYTES");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fix up CockroachDB blob column type", e);
        }
    }
}
