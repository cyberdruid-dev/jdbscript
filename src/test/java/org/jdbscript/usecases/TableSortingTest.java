package org.jdbscript.usecases;

import org.jdbscript.JdbAbstractTest;
import org.jdbscript.impl.sql.SqlConnectionProvider;
import org.jdbscript.impl.sql.SqlMetadataProvider;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertTrue;

@Test
public class TableSortingTest extends JdbAbstractTest {

    @BeforeClass
    public void checkH2Driver() {
        skipIfNotH2(TableSortingTest.class);
    }

    private SqlMetadataProvider createProvider(String... sqls) throws SQLException {
        String dbUrl = "jdbc:h2:mem:sort_test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Connection conn = DriverManager.getConnection(dbUrl, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                stmt.execute(sql);
            }
        }
        return new SqlMetadataProvider(new SqlConnectionProvider(new SimpleDataSource(dbUrl)));
    }

    private static class SimpleDataSource implements javax.sql.DataSource {
        private final String url;
        SimpleDataSource(String url) { this.url = url; }
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, "sa", ""); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    private void assertOrder(List<String> sorted, String parent, String child) {
        int parentIdx = findIndex(sorted, parent);
        int childIdx = findIndex(sorted, child);
        assertTrue(parentIdx >= 0, "Table " + parent + " not found in " + sorted);
        assertTrue(childIdx >= 0, "Table " + child + " not found in " + sorted);
        assertTrue(parentIdx < childIdx,
            String.format("Expected %s before %s, but got order: %s", parent, child, sorted));
    }

    private int findIndex(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    @Test
    public void test_converging_dag() throws SQLException {
        // A, B -> C -> D
        // In SQL terms: C has FK to A and B; D has FK to C.
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY)",
                "CREATE TABLE C (id INT PRIMARY KEY, a_id INT REFERENCES A(id), b_id INT REFERENCES B(id))",
                "CREATE TABLE D (id INT PRIMARY KEY, c_id INT REFERENCES C(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "A", "C");
        assertOrder(sorted, "B", "C");
        assertOrder(sorted, "C", "D");
    }

    @Test
    public void test_branching_dag() throws SQLException {
        // A -> B -> C, A -> B -> D -> E
        // In SQL: B has FK to A; C has FK to B; D has FK to B; E has FK to D.
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY, a_id INT REFERENCES A(id))",
                "CREATE TABLE C (id INT PRIMARY KEY, b_id INT REFERENCES B(id))",
                "CREATE TABLE D (id INT PRIMARY KEY, b_id INT REFERENCES B(id))",
                "CREATE TABLE E (id INT PRIMARY KEY, d_id INT REFERENCES D(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "A", "B");
        assertOrder(sorted, "B", "C");
        assertOrder(sorted, "B", "D");
        assertOrder(sorted, "D", "E");
    }

    @Test
    public void test_diamond_dag() throws SQLException {
        // A -> B, A -> C, B -> D, C -> D, D -> E
        // In SQL: B, C reference A; D references B, C; E references D.
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY, a_id INT REFERENCES A(id))",
                "CREATE TABLE C (id INT PRIMARY KEY, a_id INT REFERENCES A(id))",
                "CREATE TABLE D (id INT PRIMARY KEY, b_id INT REFERENCES B(id), c_id INT REFERENCES C(id))",
                "CREATE TABLE E (id INT PRIMARY KEY, d_id INT REFERENCES D(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "A", "B");
        assertOrder(sorted, "A", "C");
        assertOrder(sorted, "B", "D");
        assertOrder(sorted, "C", "D");
        assertOrder(sorted, "D", "E");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void test_simple_cycle() throws SQLException {
        // A -> B -> A
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY, a_id INT REFERENCES A(id))",
                "ALTER TABLE A ADD COLUMN b_id INT REFERENCES B(id)"
        );

        provider.getSortedTables();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void test_complex_cycle() throws SQLException {
        // A -> B -> C -> A
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY, a_id INT REFERENCES A(id))",
                "CREATE TABLE C (id INT PRIMARY KEY, b_id INT REFERENCES B(id))",
                "ALTER TABLE A ADD COLUMN c_id INT REFERENCES C(id)"
        );

        provider.getSortedTables();
    }

    @Test
    public void test_self_referential() throws SQLException {
        // A -> A
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE A (id INT PRIMARY KEY, parent_id INT REFERENCES A(id))"
        );

        List<String> sorted = provider.getSortedTables();
        Assert.assertEquals(sorted.size(), 1);
        assertTrue(sorted.get(0).equalsIgnoreCase("A"));
    }

    @Test
    public void test_redundant_transitive_dependency() throws SQLException {
        // A -> B, B -> C, A -> C
        // In SQL: A has FK to B and C; B has FK to C.
        // Sort order: C before B, B before A.
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE C (id INT PRIMARY KEY)",
                "CREATE TABLE B (id INT PRIMARY KEY, c_id INT REFERENCES C(id))",
                "CREATE TABLE A (id INT PRIMARY KEY, b_id INT REFERENCES B(id), c_id INT REFERENCES C(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "C", "B");
        assertOrder(sorted, "B", "A");
        assertOrder(sorted, "C", "A");
    }

    @Test
    public void test_disconnected_subgraphs() throws SQLException {
        // {A -> B}, {C -> D}
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE B (id INT PRIMARY KEY)",
                "CREATE TABLE A (id INT PRIMARY KEY, b_id INT REFERENCES B(id))",
                "CREATE TABLE D (id INT PRIMARY KEY)",
                "CREATE TABLE C (id INT PRIMARY KEY, d_id INT REFERENCES D(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "B", "A");
        assertOrder(sorted, "D", "C");
    }

    @Test
    public void test_multiple_fks_between_same_tables() throws SQLException {
        // A has two FKs to B
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE B (id INT PRIMARY KEY)",
                "CREATE TABLE A (id INT PRIMARY KEY, b1_id INT REFERENCES B(id), b2_id INT REFERENCES B(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "B", "A");
    }

    @Test
    public void test_case_sensitivity() throws SQLException {
        // H2 by default converts names to uppercase unless quoted.
        // We want to see if we can handle mixed case if the DB supports it or if metadata returns it.
        SqlMetadataProvider provider = createProvider(
                "CREATE TABLE \"ParentTable\" (id INT PRIMARY KEY)",
                "CREATE TABLE \"ChildTable\" (id INT PRIMARY KEY, p_id INT REFERENCES \"ParentTable\"(id))"
        );

        List<String> sorted = provider.getSortedTables();
        assertOrder(sorted, "ParentTable", "ChildTable");
    }
}
