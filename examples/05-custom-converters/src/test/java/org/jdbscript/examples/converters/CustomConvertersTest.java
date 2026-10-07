package org.jdbscript.examples.converters;

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
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomConvertersTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static PriceCatalog priceCatalog;

    @BeforeAll
    static void createSchema() throws SQLException {
        dataSource = createDataSource();
        createTables(dataSource, SCHEMA_DDL);

        // .converter(...) adds to the built-in converters (enum-to-string, java.util.Date,
        // java.time.Instant) rather than replacing them, so ProductStatus below still converts via
        // the default EnumToStringConverter - only Money needs a converter of its own here.
        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .converter(new MoneyConverter())
                .build();
        priceCatalog = new PriceCatalog(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void a_custom_converter_teaches_jdbscript_a_domain_type() {
        engine.resetDB(db -> {
            db.products().id(1).name("Widget").price(Money.dollars(19.99)).status(ProductStatus.ACTIVE);
        });

        assertEquals(Money.dollars(19.99), priceCatalog.priceOf("Widget"));
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:converters;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        return new HikariDataSource(config);
    }

    private static void createTables(DataSource dataSource, String ddl) throws SQLException {
        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    private static final String SCHEMA_DDL = """
            CREATE TABLE products (
                id INT PRIMARY KEY,
                name VARCHAR(100),
                price DECIMAL(10,2),
                status VARCHAR(50)
            )
            """;
}
