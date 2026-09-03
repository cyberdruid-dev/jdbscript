package org.jdbscript.impl.cache;

import org.jdbscript.DbmsType;
import org.jdbscript.impl.sql.SqlConnectionProvider;
import org.jdbscript.impl.sql.SqlMetadataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DbmsTypeCachingTest {

    @Test
    public void test_dbms_type_is_cached() throws Exception {
        // Mock connection and metadata
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:h2:mem:test");

        // Mock connection provider to track calls
        SqlConnectionProvider connectionProvider = mock(SqlConnectionProvider.class);
        AtomicInteger connectionCalls = new AtomicInteger(0);
        doAnswer(invocation -> {
            connectionCalls.incrementAndGet();
            SqlConnectionProvider.JdbcConnectionConsumer consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return null;
        }).when(connectionProvider).withConnection(any());

        SqlMetadataProvider provider = new SqlMetadataProvider(connectionProvider);
        InstanceCache cache = new InstanceCache();
        provider.setCache(cache);

        // First call should trigger detection
        DbmsType type1 = provider.getDbmsType();
        assertThat(type1).isEqualTo(DbmsType.H2);
        assertThat(connectionCalls.get()).isEqualTo(1);

        // Second call should use instance-local field
        DbmsType type2 = provider.getDbmsType();
        assertThat(type2).isEqualTo(DbmsType.H2);
        assertThat(connectionCalls.get()).isEqualTo(1);

        // New provider with SAME cache should use cached value
        SqlMetadataProvider provider2 = new SqlMetadataProvider(connectionProvider);
        provider2.setCache(cache);
        DbmsType type3 = provider2.getDbmsType();
        assertThat(type3).isEqualTo(DbmsType.H2);
        assertThat(connectionCalls.get()).isEqualTo(1);
    }
}
