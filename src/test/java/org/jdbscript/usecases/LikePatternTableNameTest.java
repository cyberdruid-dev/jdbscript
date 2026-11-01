package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Metadata lookups take the table name as a LIKE pattern, so {@code like_test} also matches
 * {@code likextest}.
 */
@Test
public class LikePatternTableNameTest extends JdbAbstractTest {

    private interface ILikeTestRecord extends IDBRecord {
        ILikeTestRecord id(int value);
        ILikeTestRecord name(String value);
    }

    private interface ILikeTestSchema extends IDBSchema {
        ILikeTestRecord like_test();
    }

    private interface IWrongColumnRecord extends IDBRecord {
        IWrongColumnRecord id(int value);
        IWrongColumnRecord other(String value);
    }

    private interface IWrongColumnSchema extends IDBSchema {
        IWrongColumnRecord like_test();
    }

    @BeforeMethod
    public void before() {
        cleanupTables("like_test", "likextest");
    }

    public void a_table_name_with_underscore_does_not_pick_up_columns_of_a_similar_table() {
        IJDBEngine<ILikeTestSchema> engine = createEngine(ILikeTestSchema.class);

        engine.insertDB(db -> {
            db.like_test().id(1).name("Ann");
        });

        assertTableValues(table("like_test",
                columns("id", "name"),
                row(1, "Ann")
        ));
    }

    public void a_column_of_a_similar_table_is_reported_as_unknown() {
        IJDBEngine<IWrongColumnSchema> engine = createEngine(IWrongColumnSchema.class);

        assertThatThrownBy(() -> engine.insertDB(db -> {
            db.like_test().id(1).other("x");
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("other(): no column 'other'");
    }
}
