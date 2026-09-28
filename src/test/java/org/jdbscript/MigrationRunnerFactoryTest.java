package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.flyway.FlywayMigrator;
import org.jdbscript.liquibase.LiquibaseMigrator;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code decide(...)} is the pure decision logic behind {@link MigrationRunnerFactory#detect}, fed
 * fake classpath booleans - this module always has both Liquibase and Flyway on its test
 * classpath, so the "exactly one tool present" branches are otherwise unreachable here (see
 * {@code examples/09-liquibase-data-migration}/{@code examples/10-flyway-data-migration} for that
 * proof against a real single-tool classpath).
 */
@Test
public class MigrationRunnerFactoryTest {

    @Test
    public void decide_picks_liquibase_when_only_liquibase_is_present() {
        assertThat(MigrationRunnerFactory.decide("some/path", true, false))
                .isInstanceOf(LiquibaseMigrator.class);
    }

    @Test
    public void decide_picks_flyway_when_only_flyway_is_present() {
        assertThat(MigrationRunnerFactory.decide("some/path", false, true))
                .isInstanceOf(FlywayMigrator.class);
    }

    @Test
    public void decide_fails_with_a_clear_error_when_both_tools_are_present() {
        assertThatThrownBy(() -> MigrationRunnerFactory.decide("some/path", true, true))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("Liquibase")
                .hasMessageContaining("Flyway")
                .hasMessageContaining(".migrator(");
    }

    @Test
    public void decide_fails_with_a_clear_error_when_neither_tool_is_present() {
        assertThatThrownBy(() -> MigrationRunnerFactory.decide("some/path", false, false))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining(".migrator(");
    }

    @Test
    public void classExists_finds_a_class_that_is_on_the_classpath() {
        assertThat(MigrationRunnerFactory.classExists("java.lang.String")).isTrue();
    }

    @Test
    public void classExists_does_not_find_a_class_that_is_not_on_the_classpath() {
        assertThat(MigrationRunnerFactory.classExists("no.such.Class")).isFalse();
    }
}
