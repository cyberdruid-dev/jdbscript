package org.jdbscript.usecases;

import org.jdbscript.CacheStrategy;
import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PostgreSQLStrategy.getSequences()} returns bare, unqualified sequence names (see its own
 * comment on why: {@code information_schema.sequences} excludes owned/SERIAL sequences, so it reads
 * {@code pg_class} directly instead, which isn't scoped by schema). Reusing that unqualified name to
 * look up {@code last_value}/{@code setval} afterward relies entirely on {@code search_path} to
 * resolve it - if a same-named sequence sits earlier on the search_path than the one actually
 * backing the table, the reset silently targets the wrong one instead of leaving the real one
 * unsafe. Applies to CockroachDB too - {@code CockroachDBStrategy} inherits this code unchanged.
 * Reproduced against a real Postgres, not mocked, via the public {@link JDBEngine} API - this is a
 * runtime resolution bug, not something a mocked {@code Statement} could demonstrate.
 */
@Test
public class PostgresSequenceSchemaQualificationTest extends JdbAbstractTest {

    // Never named "public" or the connecting role - so creating/dropping it can't itself land on
    // the default search_path and change what schema other tests resolve their tables against
    // (learned the hard way: an earlier version of this test used a schema named after the
    // connecting role, which - by Postgres' own "$user" search_path convention - silently became
    // the new default schema for every later test in the run the moment it was created).
    private static final String OTHER_SCHEMA = "jdbscript_pg_seq_qualification_test";
    private static final String TABLE = "pg_seq_qual_test_table";
    private static final String SEQ = "pg_seq_qual_test_table_id_seq";

    public interface IQualTestTableRecord extends IDBRecord {
        IQualTestTableRecord id(int value);
        IQualTestTableRecord name(String value);
    }
    private interface ISchema extends IDBSchema {
        IQualTestTableRecord pg_seq_qual_test_table();
    }

    @BeforeMethod
    public void beforeMethod() {
        // CockroachDBStrategy inherits getSequences()/resetPostgreSequences()/isPastSafeFloor()
        // unchanged from PostgreSQLStrategy (no override), so the same bug and fix apply there too.
        skipUnless("Postgres/CockroachDB sequence schema qualification", DBMSType.POSTGRESQL, DBMSType.COCKROACHDB);
        dropTestObjects();
        executeUpdate("CREATE SCHEMA " + OTHER_SCHEMA);
        // The real sequence backing the table's PK, deliberately outside the default search_path.
        executeUpdate("CREATE SEQUENCE " + OTHER_SCHEMA + "." + SEQ);
        executeUpdate("CREATE TABLE " + TABLE
                + " (id integer primary key default nextval('" + OTHER_SCHEMA + "." + SEQ + "'), name varchar(50))");
        // A same-named decoy in the default (public) schema - what an unqualified reference to SEQ
        // actually resolves to.
        executeUpdate("CREATE SEQUENCE " + SEQ);
    }

    @AfterMethod
    public void afterMethod() {
        dropTestObjects();
    }

    private void dropTestObjects() {
        executeUpdate("DROP TABLE IF EXISTS " + TABLE);
        executeUpdate("DROP SEQUENCE IF EXISTS " + SEQ);
        executeUpdate("DROP SCHEMA IF EXISTS " + OTHER_SCHEMA + " CASCADE");
    }

    // NONE, not the createEngine() default GLOBAL cache: the table only exists for the duration of
    // this test, created fresh in beforeMethod() - a shared cache populated by earlier tests in the
    // same run wouldn't know about it yet.
    private final JDBEngine<ISchema> engine = engineBuilder(ISchema.class).cacheStrategy(CacheStrategy.NONE).build();

    private long sequenceValue(String schemaQualifiedName) {
        return withResultSet("SELECT last_value FROM " + schemaQualifiedName, (rs, columns, types) -> {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        });
    }

    @Test
    public void afterInsert_should_reset_the_sequence_the_table_actually_depends_on_not_a_same_named_one_elsewhere() {
        engine.insertDB(db -> db.pg_seq_qual_test_table().id(1).name("manual"));

        assertThat(sequenceValue(OTHER_SCHEMA + "." + SEQ))
                .describedAs("the sequence actually backing the table's id should have been reset to "
                        + "a safe value after a manual insert, not left untouched while a same-named "
                        + "decoy elsewhere gets reset instead")
                .isGreaterThanOrEqualTo(10000L);
    }
}
