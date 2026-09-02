package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Test
public class JDBEngineBuilderTest {

    private interface ITestSchema extends IDbSchema {
    }

    @Test
    public void should_throw_if_datasource_set_twice_as_instance() {
        DataSource ds = mock(DataSource.class);
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds);

        assertThatThrownBy(() -> builder.dataSource(ds))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("datasource already set");
    }

    @Test
    public void should_throw_if_datasource_set_twice_as_supplier() {
        Supplier<DataSource> supplier = () -> mock(DataSource.class);
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .dataSource(supplier);

        assertThatThrownBy(() -> builder.dataSource(supplier))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("datasource already set");
    }

    @Test
    public void should_throw_if_datasource_set_as_instance_then_supplier() {
        DataSource ds = mock(DataSource.class);
        Supplier<DataSource> supplier = () -> mock(DataSource.class);
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds);

        assertThatThrownBy(() -> builder.dataSource(supplier))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("datasource already set");
    }

    @Test
    public void should_throw_if_datasource_set_as_supplier_then_instance() {
        DataSource ds = mock(DataSource.class);
        Supplier<DataSource> supplier = () -> mock(DataSource.class);
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .dataSource(supplier);

        assertThatThrownBy(() -> builder.dataSource(ds))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("datasource already set");
    }

    @Test
    public void should_throw_if_executor_set_twice() {
        IScriptExecutor executor = mock(IScriptExecutor.class);
        JDBEngine.Builder<ITestSchema> builder = JDBEngine.builder(ITestSchema.class)
                .executor(executor);

        assertThatThrownBy(() -> builder.executor(executor))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("executor already set");
    }
}
