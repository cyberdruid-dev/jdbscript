package org.jdbscript.impl.cache;

import org.jdbscript.CacheStrategy;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class InstanceCacheTest extends AbstractCacheTest {

    @Test
    public void CacheStrategy_INSTANCE_returns_value_without_calculation_on_second_call() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
        TestStringKey key = key("key1");
        ComputationMock<String> initialLoader = new ComputationMock<>("value1");

        assertThat(cache.getOrCompute(key, initialLoader)).isEqualTo("value1");
        initialLoader.assertInvoked(1);

        assertThat(cache.getOrCompute(key, k -> "value2")).isEqualTo("value1");
        initialLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_INSTANCE_calculate_value_for_second_cache_instance() {
        IJDBCache cache1 = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
        IJDBCache cache2 = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
        TestStringKey key = key("key1");

        cache1.getOrCompute(key, k -> "value1");

        ComputationMock<String> secondInstanceLoader = new ComputationMock<>("value2");
        assertThat(cache2.getOrCompute(key, secondInstanceLoader)).isEqualTo("value2");
        secondInstanceLoader.assertInvoked(1);
    }

    @Test
    public void CacheStrategy_INSTANCE_invalidate_clears_single_entry() {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
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
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
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

    @Test
    public void getOrCompute_supports_a_nested_call_on_the_same_thread() {
        // A computeFunction that itself calls getOrCompute again (e.g. SqlMetadataProvider
        // resolving one cached value while computing another) must not throw - a raw
        // ConcurrentHashMap.computeIfAbsent would reject this as a "Recursive update".
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
        TestStringKey outer = key("outer");
        TestStringKey inner = key("inner");

        String result = cache.getOrCompute(outer, k -> cache.getOrCompute(inner, k2 -> "inner-value"));

        assertThat(result).isEqualTo("inner-value");
        assertThat(cache.getOrCompute(inner, k -> "should-be-cached")).isEqualTo("inner-value");
        assertThat(cache.getOrCompute(outer, k -> "should-be-cached")).isEqualTo("inner-value");
    }

    @Test
    public void getOrCompute_computes_only_once_under_concurrent_access() throws InterruptedException {
        IJDBCache cache = cacheManager.getCache(CacheStrategy.INSTANCE, defaultDataSource);
        TestStringKey key = key("racey");
        AtomicInteger computeCalls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Runnable task = () -> {
            ready.countDown();
            await(go);
            cache.getOrCompute(key, k -> {
                computeCalls.incrementAndGet();
                return "value";
            });
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        ready.await();
        go.countDown();
        t1.join();
        t2.join();

        assertThat(computeCalls.get()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
