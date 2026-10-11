package org.jdbscript.datatypes;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.opentest4j.AssertionFailedError;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class ClobTest extends JdbAbstractTest {

    private interface IClobTestSchema extends IDBSchema {
        IClobTable clob_table();

        interface IClobTable extends IDBRecord {
            IClobTable id(int value);
            IClobTable clob_column(String value);
            IClobTable value_column(String value);
            IClobTable nclob_column(String value);
        }
    }

    private interface INumberInClobSchema extends IDBSchema {
        INumberInClobTable clob_table();

        interface INumberInClobTable extends IDBRecord {
            INumberInClobTable id(int value);
            INumberInClobTable clob_column(Integer value);
        }
    }

    private final IJDBEngine<IClobTestSchema> engine = createEngine(IClobTestSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables("clob_table");
    }

    public void assertDBHas_finds_a_row_whose_clob_equals_the_expected_value() {
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("hello");
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column("hello");
        });
    }

    public void assertDBHas_fails_when_the_clob_differs() {
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("hello");
        });

        assertThatThrownBy(() -> engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column("other");
        })).isInstanceOf(AssertionFailedError.class);
    }

    public void assertDBHasNot_passes_when_the_clob_differs_and_fails_when_it_is_equal() {
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("hello");
        });

        engine.assertDBHasNot(db -> {
            db.clob_table().id(1).clob_column("other");
        });
        assertThatThrownBy(() -> engine.assertDBHasNot(db -> {
            db.clob_table().id(1).clob_column("hello");
        })).isInstanceOf(AssertionFailedError.class);
    }

    public void null_clob_is_found_by_assertDBHas_and_rejected_by_assertDBHasNot() {
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column(null);
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column(null);
        });
        assertThatThrownBy(() -> engine.assertDBHasNot(db -> {
            db.clob_table().id(1).clob_column(null);
        })).isInstanceOf(AssertionFailedError.class);
    }

    public void only_a_row_matching_both_the_clob_and_the_other_columns_counts() {
        engine.resetDB(db -> {
            db.clob_table().id(1).value_column("shared").clob_column("hello");
            db.clob_table().id(2).value_column("shared").clob_column("other");
            db.clob_table().id(3).value_column("unique").clob_column("hello");
        });

        engine.assertDBHas(db -> {
            db.clob_table().value_column("shared").clob_column("other");
        });
        engine.assertDBHasNot(db -> {
            db.clob_table().value_column("unique").clob_column("other");
        });
    }

    public void a_record_setting_only_the_clob_finds_the_row() {
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("hello");
            db.clob_table().id(2).clob_column("other");
        });

        engine.assertDBHas(db -> {
            db.clob_table().clob_column("other");
        });
        engine.assertDBHasNot(db -> {
            db.clob_table().clob_column("missing");
        });
    }

    public void a_large_clob_matches() {
        String large = "x".repeat(40_000);
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column(large);
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column(large);
        });
    }

    public void an_nclob_matches() {
        engine.resetDB(db -> {
            db.clob_table().id(1).nclob_column("héllo ✓");
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).nclob_column("héllo ✓");
        });
        engine.assertDBHasNot(db -> {
            db.clob_table().id(1).nclob_column("other");
        });
    }

    public void an_unsupported_expected_type_on_a_clob_column_fails_descriptively() {
        // Liquibase's "clob" is a plain text type there (text, nvarchar(max), ...), compared in SQL.
        skipFor("A JDBC CLOB column", DBMSType.MYSQL, DBMSType.MARIADB, DBMSType.POSTGRESQL, DBMSType.COCKROACHDB,
                DBMSType.MSSQL, DBMSType.SQLITE, DBMSType.DUCKDB, DBMSType.SPANNER);
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("42");
        });

        assertThatThrownBy(() -> createEngine(INumberInClobSchema.class).assertDBHas(db -> {
            db.clob_table().id(1).clob_column(42);
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("clob_column")
                .hasMessageContaining("Integer");
    }

    public void on_oracle_an_empty_clob_and_null_are_the_same() {
        skipUnless("Oracle treats '' as NULL", DBMSType.ORACLE);
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("");
            db.clob_table().id(2).clob_column(null);
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column(null);
            db.clob_table().id(1).clob_column("");
            db.clob_table().id(2).clob_column("");
        });
    }

    public void elsewhere_an_empty_clob_and_null_differ() {
        skipFor("Empty CLOB distinct from NULL", DBMSType.ORACLE, null, "Oracle treats '' as NULL");
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("");
            db.clob_table().id(2).clob_column(null);
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column("");
        });
        engine.assertDBHasNot(db -> {
            db.clob_table().id(1).clob_column(null);
            db.clob_table().id(2).clob_column("");
        });
    }

    public void mysql_text_keeps_the_sql_comparison() {
        // Liquibase's "clob" is LONGTEXT here, compared in SQL with its case-insensitive collation.
        skipUnless("Case-insensitive TEXT collation", DBMSType.MYSQL, DBMSType.MARIADB);
        engine.resetDB(db -> {
            db.clob_table().id(1).clob_column("Hello");
        });

        engine.assertDBHas(db -> {
            db.clob_table().id(1).clob_column("hello");
        });
    }
}
