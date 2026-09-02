package org.jdbscript;

import org.jdbscript.utils.CountingSupplier;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Test
public class LazyEngineTest {

    private interface ITestSchema extends IDbSchema {
    }

    @Test
    public void should_not_access_datasource_during_engine_creation() {
        CountingSupplier<DataSource> supplier = new CountingSupplier<>(() -> mock(DataSource.class));

        JDBEngine.builder(ITestSchema.class)
                .dataSource(supplier)
                .build();

        assertThat(supplier.getCount()).as("DataSource supplier should not have been called during build()").isEqualTo(0);
    }

    @Test
    public void should_access_datasource_when_operation_is_performed() {
        CountingSupplier<DataSource> supplier = new CountingSupplier<>(() -> mock(DataSource.class));

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(supplier)
                .build();

        assertThat(supplier.getCount()).isEqualTo(0);

        try {
            engine.cleanupDB();
        } catch (Exception e) {
            // Expected to fail as mock DataSource returns null for connection, but we just care about the supplier call
        }

        assertThat(supplier.getCount()).as("DataSource supplier should have been called during cleanupDB()").isEqualTo(1);
    }
}
