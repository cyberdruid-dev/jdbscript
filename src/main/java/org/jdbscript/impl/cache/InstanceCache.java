package org.jdbscript.impl.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class InstanceCache implements IJDBCache {
    private final Map storage = new HashMap<>();

    // synchronized, and a plain (non-concurrent) map rather than ConcurrentHashMap.computeIfAbsent:
    // a computeFunction that itself calls getOrCompute again (e.g. resolving one cached value while
    // computing another) needs that nested call to succeed instead of throwing
    // "IllegalStateException: Recursive update" - which is exactly what computeIfAbsent does when
    // it detects a reentrant call on the same map, regardless of any external synchronization the
    // caller uses. A `synchronized` method uses a monitor lock, which - unlike computeIfAbsent's
    // internal locking - is reentrant per-thread: a nested call from the same thread proceeds
    // normally, while calls from other threads still serialize as usual.
    @Override
    public synchronized <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction) {
        if (storage.containsKey(key)) {
            return (V) storage.get(key);
        }
        V computed = computeFunction.apply(key);
        if (computed != null) {
            storage.put(key, computed);
        }
        return computed;
    }

    @Override
    public synchronized <K extends IJDBCacheKey<?>> void invalidate(K key) {
        storage.remove(key);
    }

    @Override
    public synchronized void clear() {
        storage.clear();
    }
}
