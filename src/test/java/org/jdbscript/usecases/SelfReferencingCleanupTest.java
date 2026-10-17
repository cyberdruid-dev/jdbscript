package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class SelfReferencingCleanupTest extends JdbAbstractTest {

    public interface ITreeNodeRecord extends IDBRecord {
        ITreeNodeRecord id(int value);
        ITreeNodeRecord parent_id(Integer value);
    }

    private interface ITestSchema extends IDBSchema {
        ITreeNodeRecord tree_nodes();
    }

    private final IJDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @BeforeMethod
    public void before() {
        // Children first: a plain DELETE would hit the very FK behavior under test.
        for (int id = 3; id >= 1; id--) {
            executeUpdate("DELETE FROM tree_nodes WHERE id = " + id);
        }
    }

    @Test
    public void cleanup_empties_self_referencing_table() {
        skipFor("Self-referencing table cleanup", DBMSType.DUCKDB);
        // Parents get lower PKs, so a row-by-row FK check (InnoDB) meets a parent before its children.
        engine.insertDB(db -> {
            db.tree_nodes().id(1).parent_id(null);
            db.tree_nodes().id(2).parent_id(1);
            db.tree_nodes().id(3).parent_id(2);
        });

        engine.cleanupDB();

        assertTableEmpty("tree_nodes");
    }

    @Test
    public void duckdb_cleanup_of_self_referencing_table_fails_naming_the_table() {
        skipUnless("DuckDB self-referencing FK limitation", DBMSType.DUCKDB);
        engine.insertDB(db -> {
            db.tree_nodes().id(1).parent_id(null);
            db.tree_nodes().id(2).parent_id(1);
        });

        assertThatThrownBy(engine::cleanupDB)
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("tree_nodes");
    }

    @Test
    public void failed_cleanup_restores_foreign_key_checks() throws SQLException {
        skipUnless("FOREIGN_KEY_CHECKS", DBMSType.MYSQL, DBMSType.MARIADB);
        try (Connection cnn = dataSource.getConnection()) {
            SqlScriptExecutor executor = new SqlScriptExecutor();
            executor.setDataSource(unclosable(cnn));

            assertThatThrownBy(() -> executor.cleanupTables(List.of("no_such_table")));

            assertThat(foreignKeyChecks(cnn)).isEqualTo(1);
        }
    }

    private int foreignKeyChecks(Connection cnn) throws SQLException {
        try (Statement stmt = cnn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT @@FOREIGN_KEY_CHECKS")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private DataSource unclosable(Connection cnn) {
        Connection proxy = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (p, method, args) -> method.getName().equals("close") ? null : method.invoke(cnn, args));
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class},
                (p, method, args) -> method.getName().equals("getConnection") ? proxy : method.invoke(dataSource, args));
    }
}
