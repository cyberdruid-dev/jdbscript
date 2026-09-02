package org.jdbscript.impl.cache;

import java.util.function.Function;

class NoCache implements IJDBCache {

    @Override
    public <V, K extends IJDBCacheKey<V>> V getOrCompute(K key, Function<K, V> computeFunction) {
        return computeFunction.apply(key);
    }

    @Override
    public <K extends IJDBCacheKey<?>> void invalidate(K key) {
    }

    @Override
    public void clear() {
    }
}
