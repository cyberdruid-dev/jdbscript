package org.jdbscript.usecases;

import org.jdbscript.CacheStrategy;
import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class TableNameCaseTest extends JdbAbstractTest {

    private interface IOrderItemsRecord extends IDBRecord {
        IOrderItemsRecord id(int value);
        IOrderItemsRecord name(String value);
    }

    private interface IMixedCaseSchema extends IDBSchema {
        IOrderItemsRecord OrderItems();
    }

    private interface ILowerCaseSchema extends IDBSchema {
        IOrderItemsRecord orderitems();
    }

    private interface IBinaryItemsRecord extends IDBRecord {
        IBinaryItemsRecord id(int value);
        IBinaryItemsRecord data(byte[] value);
    }

    private interface IBinarySchema extends IDBSchema {
        IBinaryItemsRecord BinaryItems();
    }

    private interface IExactCaseSchema extends IDBSchema {
        IOrderItemsRecord dup();
    }

    private interface IAmbiguousSchema extends IDBSchema {
        IOrderItemsRecord Dup();
    }

    @BeforeMethod
    public void createMixedCaseTable() {
        createTable("OrderItems");
    }

    @AfterMethod(alwaysRun = true)
    public void dropMixedCaseTable() {
        dropTable("OrderItems");
    }

    public void insertDB_into_mixed_case_table() {
        engine().insertDB(db -> {
            db.OrderItems().id(1).name("first");
        });

        assertTableValues(table(quoted("OrderItems"),
                columns("id", "name"),
                row(1, "first")
        ));
    }

    public void assertDBHas_and_assertDBHasNot_on_mixed_case_table() {
        JDBEngine<IMixedCaseSchema> engine = engine();
        engine.insertDB(db -> {
            db.OrderItems().id(1).name("first");
        });

        engine.assertDBHas(db -> {
            db.OrderItems().id(1).name("first");
        });
        engine.assertDBHasNot(db -> {
            db.OrderItems().id(1).name("other");
        });
    }

    public void updateDB_on_mixed_case_table() {
        JDBEngine<IMixedCaseSchema> engine = engine();
        engine.insertDB(db -> {
            db.OrderItems().id(1).name("first");
        });

        engine.updateDB(db -> {
            db.OrderItems().id(1).name("updated");
        });

        engine.assertDBHas(db -> {
            db.OrderItems().id(1).name("updated");
        });
    }

    public void cleanup_empties_mixed_case_table() {
        JDBEngine<IMixedCaseSchema> engine = engine();
        engine.insertDB(db -> {
            db.OrderItems().id(1).name("first");
        });

        engine.resetDB(db -> {
            db.OrderItems().id(2).name("second");
        });

        assertTableValues(table(quoted("OrderItems"),
                columns("id", "name"),
                row(2, "second")
        ));
    }

    public void method_in_different_case_maps_mixed_case_table() {
        JDBEngine<ILowerCaseSchema> engine = engineBuilder(ILowerCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();

        engine.resetDB(db -> {
            db.orderitems().id(1).name("first");
        });

        engine.assertDBHas(db -> {
            db.orderitems().id(1).name("first");
        });
    }

    public void exact_case_match_wins_over_case_insensitive_one() {
        skipForCaseInsensitiveTableNames();
        createTable("dup");
        createTable("DUP");
        try {
            JDBEngine<IExactCaseSchema> engine = engineBuilder(IExactCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();

            engine.insertDB(db -> {
                db.dup().id(1).name("first");
            });

            assertTableValues(table(quoted("dup"), columns("id", "name"), row(1, "first")));
            assertTableEmpty(quoted("DUP"));
        } finally {
            dropTable("dup");
            dropTable("DUP");
        }
    }

    public void cleanup_empties_only_exact_case_match() {
        skipForCaseInsensitiveTableNames();
        createTable("dup");
        createTable("DUP");
        try {
            executeUpdate("INSERT INTO %s (id, name) VALUES (1, 'kept')", quoted("DUP"));
            JDBEngine<IExactCaseSchema> engine = engineBuilder(IExactCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();

            engine.cleanupDB();

            assertTableValues(table(quoted("DUP"), columns("id", "name"), row(1, "kept")));
        } finally {
            dropTable("dup");
            dropTable("DUP");
        }
    }

    public void ambiguous_case_insensitive_match_fails_descriptively() {
        skipForCaseInsensitiveTableNames();
        createTable("dup");
        createTable("DUP");
        try {
            JDBEngine<IAmbiguousSchema> engine = engineBuilder(IAmbiguousSchema.class).cacheStrategy(CacheStrategy.NONE).build();

            assertThatThrownBy(() -> engine.insertDB(db -> {
                db.Dup().id(1).name("first");
            }))
                    .isInstanceOf(JDBScriptException.class)
                    .hasMessageContaining("'Dup'")
                    .hasMessageContaining("dup")
                    .hasMessageContaining("DUP");
        } finally {
            dropTable("dup");
            dropTable("DUP");
        }
    }

    public void postgres_byte_array_into_bytea_column_of_mixed_case_table() {
        skipUnless("bytea detection", DBMSType.POSTGRESQL);
        executeUpdate("CREATE TABLE %s (id INT NOT NULL PRIMARY KEY, data BYTEA)", quoted("BinaryItems"));
        try {
            JDBEngine<IBinarySchema> engine = engineBuilder(IBinarySchema.class).cacheStrategy(CacheStrategy.NONE).build();

            engine.insertDB(db -> {
                db.BinaryItems().id(1).data(new byte[]{1, 2, 3});
            });

            assertTableValues(table(quoted("BinaryItems"),
                    columns("id", "data:blob"),
                    row(1, new byte[]{1, 2, 3})
            ));
        } finally {
            dropTable("BinaryItems");
        }
    }

    private String quoted(String tableName) {
        try (Connection cnn = dataSource.getConnection()) {
            String quote = cnn.getMetaData().getIdentifierQuoteString().trim();
            return quote + tableName + quote;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTable(String tableName) {
        if (testConfiguration.getDbmsType() == DBMSType.SPANNER) {
            executeUpdate("CREATE TABLE %s (id INT64 NOT NULL, name STRING(50)) PRIMARY KEY (id)", quoted(tableName));
        } else {
            executeUpdate("CREATE TABLE %s (id INT NOT NULL PRIMARY KEY, name VARCHAR(50))", quoted(tableName));
        }
    }

    private void dropTable(String tableName) {
        executeUpdate("DROP TABLE " + quoted(tableName));
    }

    private void skipForCaseInsensitiveTableNames() {
        skipFor("tables differing only in case", DBMSType.SQLITE, DBMSType.DUCKDB, DBMSType.MSSQL, DBMSType.SPANNER);
    }

    private JDBEngine<IMixedCaseSchema> engine() {
        return engineBuilder(IMixedCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();
    }
}
