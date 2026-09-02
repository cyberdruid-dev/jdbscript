package org.jdbscript.impl.cache;

import org.jdbscript.CacheStrategy;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NoCacheTest extends AbstractCacheTest {

    @Test
    public void CacheStrategy_NONE_computes_value_every_time() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.NONE);
        TestStringKey key = key("key1");
        ComputationMock<String> firstLoader = new ComputationMock<>("value1");
        ComputationMock<String> secondLoader = new ComputationMock<>("value2");

        assertThat(cache.getOrCompute(key, firstLoader)).isEqualTo("value1");
        firstLoader.assertInvoked(1);

        assertThat(cache.getOrCompute(key, secondLoader)).isEqualTo("value2");
        secondLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_NONE_invalidate_is_noop() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.NONE);
        cache.invalidate(key("key1"));
        // No observable state to verify, but ensures no exceptions
    }

    @Test
    public void CacheStrategy_NONE_clear_is_noop() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.NONE);
        cache.clear();
        // No observable state to verify, but ensures no exceptions
    }
}
