package org.jdbscript.impl.cache;

import org.jdbscript.CacheStrategy;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InstanceCacheTest extends AbstractCacheTest {

    @Test
    public void CacheStrategy_INSTANCE_returns_value_without_calculation_on_second_call() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE);
        TestStringKey key = key("key1");
        ComputationMock<String> initialLoader = new ComputationMock<>("value1");

        assertThat(cache.getOrCompute(key, initialLoader)).isEqualTo("value1");
        initialLoader.assertInvoked(1);

        assertThat(cache.getOrCompute(key, k -> "value2")).isEqualTo("value1");
        initialLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_INSTANCE_calculate_value_for_second_cache_instance() {
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.INSTANCE);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.INSTANCE);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "value1");

        ComputationMock<String> secondInstanceLoader = new ComputationMock<>("value2");
        assertThat(cache2.getOrCompute(key, secondInstanceLoader)).isEqualTo("value2");
        secondInstanceLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_INSTANCE_invalidate_clears_single_entry() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE);
        TestStringKey key1 = key("key1");
        TestStringKey key2 = key("key2");

        cache.getOrCompute(key1, k -> "v1");
        cache.getOrCompute(key2, k -> "v2");

        cache.invalidate(key1);

        ComputationMock<String> recomputationLoader = new ComputationMock<>("new-v1");
        assertThat(cache.getOrCompute(key1, recomputationLoader)).isEqualTo("new-v1");
        recomputationLoader.assertInvoked(1);

        assertThat(cache.getOrCompute(key2, k -> "should-be-cached")).isEqualTo("v2");
    }

    @Test
    public void CacheStrategy_INSTANCE_clear_removes_all_entries() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE);
        TestStringKey key1 = key("key1");
        TestStringKey key2 = key("key2");

        cache.getOrCompute(key1, k -> "v1");
        cache.getOrCompute(key2, k -> "v2");

        cache.clear();

        ComputationMock<String> recomputationLoader1 = new ComputationMock<>("new-v1");
        ComputationMock<String> recomputationLoader2 = new ComputationMock<>("new-v2");
        assertThat(cache.getOrCompute(key1, recomputationLoader1)).isEqualTo("new-v1");
        assertThat(cache.getOrCompute(key2, recomputationLoader2)).isEqualTo("new-v2");
        recomputationLoader1.assertInvoked(1);
        recomputationLoader2.assertInvoked(1);
    }
}
