package org.jdbscript.examples.defaults;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * "Explicitly set" means: called from the script itself (the {@code resetDB}/{@code insertDB}
 * lambda, or a class-based script). A column left untouched by the script gets whatever its
 * {@code defaults(RecordTools)} method assigns — see {@link IAppSchema.IProductRecord#defaults}.
 */
class RecordToolsDefaultsTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static ProductCatalog productCatalog;

    @BeforeAll
    static void createSchema() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:recordtools;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        dataSource = new HikariDataSource(config);

        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE products (
                        id INT PRIMARY KEY,
                        sku VARCHAR(50) NOT NULL,
                        name VARCHAR(100)
                    )
                    """);
        }

        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .build();
        productCatalog = new ProductCatalog(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void id_and_sku_are_generated_when_omitted() {
        // Neither product sets id or sku — both come from defaults(), in script order.
        engine.resetDB(db -> {
            db.products().name("Widget");
            db.products().name("Gadget");
        });

        assertEquals("SKU-1", productCatalog.skuFor("Widget"));
        assertEquals("SKU-2", productCatalog.skuFor("Gadget"));
    }

    @Test
    void explicit_id_is_respected_and_sku_still_derives_from_it() {
        // id is set explicitly, so defaults() leaves it alone — but sku's template still reads
        // whatever id ends up being, generated or not.
        engine.resetDB(db -> {
            db.products().id(500).name("Custom Widget");
        });

        assertEquals("SKU-500", productCatalog.skuFor("Custom Widget"));
    }
}
