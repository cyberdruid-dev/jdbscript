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
public class ColumnNameCaseTest extends JdbAbstractTest {

    private static final String TABLE = "column_case";
    private static final String DUP_TABLE = "column_case_dup";

    private interface IMixedCaseRecord extends IDBRecord {
        IMixedCaseRecord itemId(int value);
        IMixedCaseRecord firstName(String value);
    }

    private interface IMixedCaseSchema extends IDBSchema {
        IMixedCaseRecord column_case();
    }

    private interface ILowerCaseRecord extends IDBRecord {
        ILowerCaseRecord itemid(int value);
        ILowerCaseRecord firstname(String value);
    }

    private interface ILowerCaseSchema extends IDBSchema {
        ILowerCaseRecord column_case();
    }

    private interface IDupRecord extends IDBRecord {
        IDupRecord id(int value);
        IDupRecord val(String value);
    }

    private interface IAmbiguousRecord extends IDBRecord {
        IAmbiguousRecord id(int value);
        IAmbiguousRecord Val(String value);
    }

    private interface IAmbiguousSchema extends IDBSchema {
        IAmbiguousRecord column_case_dup();
    }

    private interface IDupSchema extends IDBSchema {
        IDupRecord column_case_dup();
    }

    @BeforeMethod
    public void createMixedCaseTable() {
        if (testConfiguration.getDbmsType() == DBMSType.SPANNER) {
            executeUpdate("CREATE TABLE %s (%s INT64 NOT NULL, %s STRING(50)) PRIMARY KEY (%s)",
                    TABLE, quoted("itemId"), quoted("firstName"), quoted("itemId"));
        } else {
            executeUpdate("CREATE TABLE %s (%s INT NOT NULL PRIMARY KEY, %s VARCHAR(50))",
                    TABLE, quoted("itemId"), quoted("firstName"));
        }
    }

    @AfterMethod(alwaysRun = true)
    public void dropMixedCaseTable() {
        executeUpdate("DROP TABLE " + TABLE);
    }

    public void insertDB_sets_mixed_case_column() {
        engine().insertDB(db -> {
            db.column_case().itemId(1).firstName("Alice");
        });

        assertTableValues(table(TABLE,
                columns("itemId", "firstName"),
                row(1, "Alice")
        ));
    }

    public void assertDBHas_and_assertDBHasNot_on_mixed_case_column() {
        JDBEngine<IMixedCaseSchema> engine = engine();
        engine.insertDB(db -> {
            db.column_case().itemId(1).firstName("Alice");
        });

        engine.assertDBHas(db -> {
            db.column_case().itemId(1).firstName("Alice");
        });
        engine.assertDBHasNot(db -> {
            db.column_case().itemId(1).firstName("Bob");
        });
    }

    public void updateDB_sets_mixed_case_column_by_mixed_case_primary_key() {
        JDBEngine<IMixedCaseSchema> engine = engine();
        engine.insertDB(db -> {
            db.column_case().itemId(1).firstName("Alice");
        });

        engine.updateDB(db -> {
            db.column_case().itemId(1).firstName("Bob");
        });

        assertTableValues(table(TABLE,
                columns("itemId", "firstName"),
                row(1, "Bob")
        ));
    }

    public void method_in_different_case_maps_mixed_case_column() {
        JDBEngine<ILowerCaseSchema> engine = engineBuilder(ILowerCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();

        engine.resetDB(db -> {
            db.column_case().itemid(1).firstname("Alice");
        });
        engine.updateDB(db -> {
            db.column_case().itemid(1).firstname("Bob");
        });

        engine.assertDBHas(db -> {
            db.column_case().itemid(1).firstname("Bob");
        });
    }

    public void exact_case_match_wins_over_case_insensitive_one() {
        skipForCaseInsensitiveColumnNames();
        createDupTable();
        try {
            JDBEngine<IDupSchema> engine = engineBuilder(IDupSchema.class).cacheStrategy(CacheStrategy.NONE).build();

            engine.insertDB(db -> {
                db.column_case_dup().id(1).val("lower");
            });

            assertTableValues(table(DUP_TABLE,
                    columns("id", "val", "VAL"),
                    row(1, "lower", null)
            ));
        } finally {
            executeUpdate("DROP TABLE " + DUP_TABLE);
        }
    }

    public void ambiguous_case_insensitive_match_fails_descriptively() {
        skipForCaseInsensitiveColumnNames();
        createDupTable();
        try {
            JDBEngine<IAmbiguousSchema> engine = engineBuilder(IAmbiguousSchema.class).cacheStrategy(CacheStrategy.NONE).build();

            assertThatThrownBy(() -> engine.insertDB(db -> {
                db.column_case_dup().id(1).Val("first");
            }))
                    .isInstanceOf(JDBScriptException.class)
                    .hasMessageContaining("'Val'")
                    .hasMessageMatching("(?is).*" + DUP_TABLE + ".*")
                    .hasMessageContaining("val")
                    .hasMessageContaining("VAL");
        } finally {
            executeUpdate("DROP TABLE " + DUP_TABLE);
        }
    }

    private String quoted(String identifier) {
        try (Connection cnn = dataSource.getConnection()) {
            String quote = cnn.getMetaData().getIdentifierQuoteString().trim();
            return quote + identifier + quote;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private JDBEngine<IMixedCaseSchema> engine() {
        return engineBuilder(IMixedCaseSchema.class).cacheStrategy(CacheStrategy.NONE).build();
    }

    private void createDupTable() {
        executeUpdate("CREATE TABLE %s (id INT NOT NULL PRIMARY KEY, %s VARCHAR(50), %s VARCHAR(50))",
                DUP_TABLE, quoted("val"), quoted("VAL"));
    }

    private void skipForCaseInsensitiveColumnNames() {
        skipFor("columns differing only in case", DBMSType.MYSQL, DBMSType.MARIADB, DBMSType.SQLITE,
                DBMSType.DUCKDB, DBMSType.MSSQL, DBMSType.SPANNER);
    }
}
