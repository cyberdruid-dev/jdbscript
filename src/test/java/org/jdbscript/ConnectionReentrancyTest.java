package org.jdbscript;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IDBSchema.IDBRecord;
import org.testng.annotations.Test;

import java.sql.Connection;

/**
 * Regression test: the very first insert/assert/cleanup call on a fresh engine used to lazily
 * resolve the DBMS type/strategy from *inside* the already-open connection used for that
 * operation. If that lazy resolution grabbed a second connection from the same DataSource instead
 * of reusing the open one, a pool sized to 1 (common for lean test fixtures) would exhaust and time
 * out instead of completing.
 */
@Test
public class ConnectionReentrancyTest {

    private interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(int value);
        ICustomerRecord name(String value);
    }

    private interface ITestSchema extends IDBSchema {
        ICustomerRecord customers();
    }

    @Test(timeOut = 15000)
    public void insertDB_should_not_exhaust_a_single_connection_pool_on_first_use() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:ConnectionReentrancyTest;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(3000);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection cnn = dataSource.getConnection()) {
                cnn.createStatement().execute("CREATE TABLE customers (id INT, name VARCHAR(50))");
            }

            IJDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                    .dataSource(dataSource)
                    .build();

            // Fresh engine: DBMS type/strategy is not resolved yet, forcing the lazy lookup to
            // happen while this insert's own connection is still checked out.
            engine.insertDB(db -> db.customers().id(1).name("Ann"));
        }
    }
}
