package org.jdbscript;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.utils.TestDataSource;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SchemaValidationTest extends JdbAbstractTest {

    private TestDataSource cleanDataSource;
    private HikariDataSource hikariDataSource;

    private interface ITestSchema extends IDBSchema {
        ITableRecord table1();
        ITableRecord table2();

        interface ITableRecord extends IDBRecord {}
    }

    @BeforeClass
    public void setupCleanDataSource() {
        skipIfNotH2(SchemaValidationTest.class);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:SchemaValidationTest;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        hikariDataSource = new HikariDataSource(config);
        cleanDataSource = new TestDataSource(hikariDataSource);
    }

    @AfterClass
    public void closeCleanDataSource() {
        if (hikariDataSource != null) {
            hikariDataSource.close();
        }
    }

    @BeforeMethod
    @Override
    public void resetOpenConnectionTracking() {
        if (cleanDataSource != null) {
            cleanDataSource.resetOpenConnectionTracking();
        }
    }

    @AfterMethod
    @Override
    public void assertAllConnectionsClosed() {
        if (cleanDataSource != null) {
            cleanDataSource.assertAllConnectionsClosed();
        }
    }

    @BeforeMethod(dependsOnMethods = "resetOpenConnectionTracking")
    public void setup() throws SQLException {
        try (Connection cnn = cleanDataSource.getConnection()) {
            ResultSet rs = cnn.getMetaData().getTables(null, null, null, new String[]{"TABLE"});
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!tableName.startsWith("SYSTEM_")) {
                    tables.add(tableName);
                }
            }
            for (String table : tables) {
                try {
                    cnn.createStatement().execute("DROP TABLE " + table + " CASCADE");
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    protected void executeUpdate(String sql, Object... replacements) {
        sql = sql.formatted(replacements);
        try(Connection cnn = cleanDataSource.getConnection()) {
            cnn.createStatement().execute(sql);
            if(!cnn.getAutoCommit()) {
                cnn.commit();
            }
        } catch (Exception e) {
            Assert.fail("Fail to executeUpdate('%s')".formatted(sql), e);
        }
    }

    @Override
    protected <T extends IDBSchema> JDBEngine<T> createEngine(Class<T> schemaClass) {
        return JDBEngine.builder(schemaClass)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .build();
    }

    @Test
    public void should_fail_when_table_is_missing_in_db() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        // table2 is missing

        JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

        assertThatThrownBy(() -> engine.insertDB(db -> {}))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("Table 'TABLE2' defined in interface ITestSchema but missing from DB");
    }

    @Test
    public void should_warn_on_unmapped_table_by_default() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE unmapped (id INT)");

        JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

        // Should not throw, just log warn
        assertThatCode(() -> engine.insertDB(db -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    public void should_fail_on_unmapped_table_when_configured() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE unmapped (id INT)");

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .build();

        assertThatThrownBy(() -> engine.insertDB(db -> {}))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("Table 'UNMAPPED' found in DB but missing from schema interface ITestSchema");
    }

    @Test
    public void should_suppress_unmapped_table() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE suppressed (id INT)");

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .suppressUnmappedTable("SUPPRESSED")
                .build();

        assertThatCode(() -> engine.insertDB(db -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    public void should_suppress_unmapped_table_case_insensitively() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE suppressed_table (id INT)");

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .suppressUnmappedTable("SUPPRESSED_TABLE")
                .build();

        assertThatCode(() -> engine.insertDB(db -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    public void should_suppress_default_migration_tables() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE flyway_schema_history (id INT)");
        executeUpdate("CREATE TABLE DATABASECHANGELOG (id INT)");

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .suppressDefaultUnmappedTables(true)
                .build();

        assertThatCode(() -> engine.insertDB(db -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    public void should_accumulate_suppressed_tables() {
        executeUpdate("CREATE TABLE table1 (id INT)");
        executeUpdate("CREATE TABLE table2 (id INT)");
        executeUpdate("CREATE TABLE S1 (id INT)");
        executeUpdate("CREATE TABLE S2 (id INT)");

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(() -> cleanDataSource)
                .executor(testConfiguration.getScriptExecutor())
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .suppressUnmappedTable("S1")
                .suppressUnmappedTable("S2")
                .build();

        assertThatCode(() -> engine.insertDB(db -> {}))
                .doesNotThrowAnyException();
    }
}
