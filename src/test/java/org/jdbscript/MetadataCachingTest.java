package org.jdbscript;

import org.jdbscript.impl.IMetadataProvider;
import org.jdbscript.impl.cache.IJDBCache;
import org.jdbscript.impl.cache.InstanceCache;
import org.jdbscript.impl.cache.NoCache;
import org.jdbscript.impl.sql.SqlConnectionProvider;
import org.jdbscript.impl.sql.SqlMetadataProvider;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class MetadataCachingTest {

    private interface ITestSchema extends IDBSchema {
    }

    @Test
    public void test_cache_strategy_propagation() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");

        IScriptExecutor executor = mock(IScriptExecutor.class);
        IMetadataProvider provider = mock(IMetadataProvider.class);
        when(executor.getMetadataProvider()).thenReturn(provider);
        when(provider.getAllTables()).thenReturn(Collections.emptyList());
        when(provider.getSortedTables()).thenReturn(Collections.emptyList());

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
    public void test_no_cache_strategy_propagation() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");

        IScriptExecutor executor = mock(IScriptExecutor.class);
        IMetadataProvider provider = mock(IMetadataProvider.class);
        when(executor.getMetadataProvider()).thenReturn(provider);
        when(provider.getAllTables()).thenReturn(Collections.emptyList());
        when(provider.getSortedTables()).thenReturn(Collections.emptyList());

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
    public void test_metadata_sorter_uses_cache() throws Exception {
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
        SqlMetadataProvider sorter = new SqlMetadataProvider(provider);
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
    public void test_clear_cache_effect() throws Exception {
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
        SqlMetadataProvider sorter = new SqlMetadataProvider(provider);
        sorter.setCache(cache);

        List<String> tables = List.of("T1");
        
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T1"));
        
        cache.clear();
        
        sorter.sortTablesByDependencies(tables);
        verify(metaData, times(2)).getImportedKeys(any(), any(), eq("T1"));
    }

    @Test
    public void test_table_discovery_uses_cache_and_rediscovers_after_clear() throws Exception {
        SqlConnectionProvider provider = mock(SqlConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tablesResultSet = mock(ResultSet.class);
        ResultSet importedKeysResultSet = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(true, true, false, true, true, false);
        when(tablesResultSet.getString("TABLE_NAME")).thenReturn("T1", "T2", "T1", "T2");
        when(metaData.getImportedKeys(any(), any(), any())).thenReturn(importedKeysResultSet);
        when(importedKeysResultSet.next()).thenReturn(false);

        doAnswer(invocation -> {
            SqlConnectionProvider.JdbcConnectionConsumer consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return null;
        }).when(provider).withConnection(any());

        // A real cache (not a mock/stub) is essential here: it exercises the actual
        // ConcurrentHashMap.computeIfAbsent underneath, which throws IllegalStateException
        // ("Recursive update") if getOrCompute is ever nested inside another getOrCompute call on
        // the same cache - exactly the shape this test's two-table discovery+sort would hit if
        // table discovery were cached naively.
        IJDBCache cache = new InstanceCache();
        SqlMetadataProvider metadataProvider = new SqlMetadataProvider(provider);
        metadataProvider.setCache(cache);

        assertEquals(metadataProvider.getAllTables(), List.of("T1", "T2"));
        assertEquals(metadataProvider.getSortedTables().size(), 2);
        verify(metaData, times(1)).getTables(any(), any(), any(), any());

        // Second call must reuse the cached discovery, not re-query.
        metadataProvider.getAllTables();
        verify(metaData, times(1)).getTables(any(), any(), any(), any());

        cache.clear();

        // After clearing, discovery must run again - this is the actual bug: clearCache() only
        // clearing the pluggable IJDBCache, with allTables/globalSortedTables memoized outside of
        // it, meant a migration mid-run stayed invisible for the engine's whole lifetime.
        metadataProvider.getAllTables();
        verify(metaData, times(2)).getTables(any(), any(), any(), any());
    }

    @Test
    public void test_sorted_tables_are_cached_and_reused() throws Exception {
        SqlConnectionProvider provider = mock(SqlConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tablesResultSet = mock(ResultSet.class);
        ResultSet importedKeysResultSet = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getTables(any(), any(), any(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(true, true, false, true, true, false);
        when(tablesResultSet.getString("TABLE_NAME")).thenReturn("T1", "T2", "T1", "T2");
        when(metaData.getImportedKeys(any(), any(), any())).thenReturn(importedKeysResultSet);
        when(importedKeysResultSet.next()).thenReturn(false);

        doAnswer(invocation -> {
            SqlConnectionProvider.JdbcConnectionConsumer consumer = invocation.getArgument(0);
            consumer.accept(connection);
            return null;
        }).when(provider).withConnection(any());

        // A real cache is essential here too: getSortedTables() nests getAllTables() (its own
        // getOrCompute call) and sortTablesByDependencies's per-table getOrCompute calls inside its
        // own getOrCompute(SORTED_TABLES_KEY, ...) - this only works because InstanceCache is
        // required to support nested calls on the same thread.
        IJDBCache cache = new InstanceCache();
        SqlMetadataProvider metadataProvider = new SqlMetadataProvider(provider);
        metadataProvider.setCache(cache);

        List<String> sorted1 = metadataProvider.getSortedTables();
        List<String> sorted2 = metadataProvider.getSortedTables();

        assertSame(sorted1, sorted2, "sorted tables should be cached, not recomputed on every call");
        verify(metaData, times(1)).getTables(any(), any(), any(), any());
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T1"));
        verify(metaData, times(1)).getImportedKeys(any(), any(), eq("T2"));

        cache.clear();

        List<String> sorted3 = metadataProvider.getSortedTables();
        assertNotSame(sorted1, sorted3, "must recompute after clear()");
        verify(metaData, times(2)).getTables(any(), any(), any(), any());
    }

    @Test
    public void test_global_cache_strategy() throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:h2:mem:global_test");
        when(meta.getUserName()).thenReturn("SA");

        IScriptExecutor executor1 = mock(IScriptExecutor.class);
        IScriptExecutor executor2 = mock(IScriptExecutor.class);
        IMetadataProvider provider = mock(IMetadataProvider.class);
        when(executor1.getMetadataProvider()).thenReturn(provider);
        when(executor2.getMetadataProvider()).thenReturn(provider);
        when(provider.getAllTables()).thenReturn(Collections.emptyList());
        when(provider.getSortedTables()).thenReturn(Collections.emptyList());

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
