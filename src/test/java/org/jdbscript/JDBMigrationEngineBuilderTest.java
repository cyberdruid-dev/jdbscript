package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.liquibase.LiquibaseMigrator;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JDBMigrationEngine.Builder} should fail fast on missing required configuration, matching
 * {@link JDBEngine.Builder}'s own convention - not defer to a {@code NullPointerException} the
 * first time {@link JDBMigrationEngine#before()}/{@code migrateTo(...)} is actually called.
 */
@Test
public class JDBMigrationEngineBuilderTest {

    private interface IFromSchema extends IDBSchema {
    }

    private interface IToSchema extends IDBSchema {
    }

    @Test
    public void build_fails_fast_when_dataSource_is_missing() {
        assertThatThrownBy(() -> JDBMigrationEngine.builder(IFromSchema.class, IToSchema.class)
                .migrator(new LiquibaseMigrator("db/changelog.yaml"))
                .build())
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void build_fails_fast_when_migrator_is_missing() {
        assertThatThrownBy(() -> JDBMigrationEngine.builder(IFromSchema.class, IToSchema.class)
                .dataSource(() -> null)
                .build())
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void build_fails_fast_when_from_schema_class_is_null() {
        assertThatThrownBy(() -> JDBMigrationEngine.builder((Class<IFromSchema>) null, IToSchema.class)
                .dataSource(() -> null)
                .migrator(new LiquibaseMigrator("db/changelog.yaml"))
                .build())
                .isInstanceOf(JDBScriptException.class);
    }

    @Test
    public void build_fails_fast_when_to_schema_class_is_null() {
        assertThatThrownBy(() -> JDBMigrationEngine.builder(IFromSchema.class, (Class<IToSchema>) null)
                .dataSource(() -> null)
                .migrator(new LiquibaseMigrator("db/changelog.yaml"))
                .build())
                .isInstanceOf(JDBScriptException.class);
    }
}
