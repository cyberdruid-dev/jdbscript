package org.jdbscript.impl.cache;

import org.jdbscript.CacheStrategy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalCacheTest extends AbstractCacheTest {

    @BeforeMethod
    public void clearGlobalCache() {
        cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource).clear();
    }

    @Test
    public void CacheStrategy_GLOBAL_returns_value_without_calculation_on_second_call() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        TestStringKey key = key("key1");
        ComputationMock<String> initialLoader = new ComputationMock<>("value1");

        assertThat(cache.getOrCompute(key, initialLoader)).isEqualTo("value1");
        initialLoader.assertInvoked(1);

        assertThat(cache.getOrCompute(key, k -> "value2")).isEqualTo("value1");
        initialLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_GLOBAL_returns_value_without_calculation_for_second_cache_instance() {
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "value1");

        ComputationMock<String> secondInstanceLoader = new ComputationMock<>("value2");
        assertThat(cache2.getOrCompute(key, secondInstanceLoader)).isEqualTo("value1");
        secondInstanceLoader.assertInvoked(0);
    }

    @Test
    public void CacheStrategy_GLOBAL_invalidate_affects_all_instances() {
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "v1");

        cache2.invalidate(key);

        ComputationMock<String> recomputationLoader = new ComputationMock<>("new-v1");
        assertThat(cache1.getOrCompute(key, recomputationLoader)).isEqualTo("new-v1");
        recomputationLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_GLOBAL_clear_affects_all_instances() {
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "v1");

        cache2.clear();

        ComputationMock<String> recomputationLoader = new ComputationMock<>("new-v1");
        assertThat(cache1.getOrCompute(key, recomputationLoader)).isEqualTo("new-v1");
        recomputationLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_GLOBAL_shares_cache_for_same_database_identity() {
        DataSource sameDbDs = mockDataSource("jdbc:h2:mem:test", "sa", "PUBLIC");
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.GLOBAL, sameDbDs);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "value1");

        ComputationMock<String> secondInstanceLoader = new ComputationMock<>("value2");
        assertThat(cache2.getOrCompute(key, secondInstanceLoader)).isEqualTo("value1");
        secondInstanceLoader.assertInvoked(0);
        assertThat(cache1).isSameAs(cache2);
    }

    @Test
    public void CacheStrategy_GLOBAL_isolates_cache_for_different_database_identities() {
        DataSource differentDbDs = mockDataSource("jdbc:h2:mem:other", "sa", "PUBLIC");
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.GLOBAL, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.GLOBAL, differentDbDs);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "value1");

        ComputationMock<String> differentDbLoader = new ComputationMock<>("value2");
        assertThat(cache2.getOrCompute(key, differentDbLoader)).isEqualTo("value2");
        differentDbLoader.assertInvoked(1);
        assertThat(cache1).isNotSameAs(cache2);
    }
}
