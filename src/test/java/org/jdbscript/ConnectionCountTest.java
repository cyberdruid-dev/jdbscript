package org.jdbscript;

import org.jdbscript.db.ITestDBSchema;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class ConnectionCountTest extends JdbAbstractTest {

    public void cold_resetDB_does_not_acquire_a_connection_per_table() {
        JDBEngine<ITestDBSchema> engine = engineBuilder(ITestDBSchema.class).cacheStrategy(CacheStrategy.INSTANCE).build();
        dataSource.resetOpenConnectionTracking();

        engine.resetDB(db -> {
            db.table_1().id(1).str_column_1("a");
        });

        assertThat(dataSource.getAcquiredConnectionCount()).isLessThanOrEqualTo(4);
    }
}
