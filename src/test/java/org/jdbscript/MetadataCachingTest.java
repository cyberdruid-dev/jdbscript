package org.jdbscript;

import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.InstanceCache;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.sql.MetadataTableSorter;
import org.jdbscript.impl.sql.SqlConnectionProvider;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class MetadataCachingTest {

    private interface ITestSchema extends IDbSchema {
    }

    @Test
    public void testCacheStrategyPropagation() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");

        IScriptExecutor executor = mock(IScriptExecutor.class);

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds)
                .executor(executor)
                .cacheStrategy(CacheStrategy.INSTANCE)
                .build();

        // Trigger cache propagation via getExecutor() which calls setExecutor()
        // cleanupDB calls getExecutor().cleanupTables(...)
        engine.cleanupDB();

        ArgumentCaptor<IJDBCache> cacheCaptor = ArgumentCaptor.forClass(IJDBCache.class);
        verify(executor).setCache(cacheCaptor.capture());
        assertTrue(cacheCaptor.getValue() instanceof InstanceCache);
    }

    @Test
    public void testNoCacheStrategyPropagation() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");

        IScriptExecutor executor = mock(IScriptExecutor.class);

        JDBEngine<ITestSchema> engine = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds)
                .executor(executor)
                .cacheStrategy(CacheStrategy.NONE)
                .build();

        engine.cleanupDB();

        ArgumentCaptor<IJDBCache> cacheCaptor = ArgumentCaptor.forClass(IJDBCache.class);
        verify(executor).setCache(cacheCaptor.capture());
        assertTrue(cacheCaptor.getValue() instanceof NoCache);
    }

    @Test
    public void testMetadataSorterUsesCache() throws Exception {
        SqlConnectionProvider provider = mock(SqlConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet resultSet = mock(ResultSet.class);
        
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getImportedKeys(any(), any(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        doAnswer(invocation -> {
            SqlConnectionProvider.JdbcConnectionConsumer consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return null;
        }).when(provider).withConnection(any());

        IJDBCache cache = spy(new InstanceCache());
        MetadataTableSorter sorter = new MetadataTableSorter(provider);
        sorter.setCache(cache);

        List<String> tables = List.of("T1");
        
        // First call - should query metadata
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T1"));
        
        // Second call - should use cache
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T1")); // Still 1
        verify(cache, atLeast(2)).getOrCompute(any(), any());
    }

    @Test
    public void testClearCacheEffect() throws Exception {
        SqlConnectionProvider provider = mock(SqlConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet resultSet = mock(ResultSet.class);
        
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getImportedKeys(any(), any(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        doAnswer(invocation -> {
            SqlConnectionProvider.JdbcConnectionConsumer consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return null;
        }).when(provider).withConnection(any());

        IJDBCache cache = new InstanceCache();
        MetadataTableSorter sorter = new MetadataTableSorter(provider);
        sorter.setCache(cache);

        List<String> tables = List.of("T1");
        
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T1"));
        
        cache.clear();
        
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(2)).getImportedKeys(any(), any(), eq("T1"));
    }

    @Test
    public void testGlobalCacheStrategy() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:global_test");
        when(meta.getUserName()).thenReturn("SA");

        IScriptExecutor executor1 = mock(IScriptExecutor.class);
        IScriptExecutor executor2 = mock(IScriptExecutor.class);

        JDBEngine<ITestSchema> engine1 = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds)
                .executor(executor1)
                .cacheStrategy(CacheStrategy.GLOBAL)
                .build();

        JDBEngine<ITestSchema> engine2 = JDBEngine.builder(ITestSchema.class)
                .dataSource(ds)
                .executor(executor2)
                .cacheStrategy(CacheStrategy.GLOBAL)
                .build();

        engine1.cleanupDB();
        engine2.cleanupDB();

        ArgumentCaptor<IJDBCache> cacheCaptor1 = ArgumentCaptor.forClass(IJDBCache.class);
        verify(executor1).setCache(cacheCaptor1.capture());
        
        ArgumentCaptor<IJDBCache> cacheCaptor2 = ArgumentCaptor.forClass(IJDBCache.class);
        verify(executor2).setCache(cacheCaptor2.capture());
        
        assertSame(cacheCaptor1.getValue(), cacheCaptor2.getValue(), "Global strategy should use the same cache instance for the same DataSource");
    }
}
