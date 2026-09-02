package org.jdbscript.utils;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A supplier wrapper that counts how many times {@link #get()} was called.
 * Useful for testing lazy initialization.
 *
 * @param <T> the type of results supplied by this supplier
 */
public class CountingSupplier<T> implements Supplier<T> {
    private final Supplier<T> delegate;
    private final AtomicInteger count = new AtomicInteger(0);

    public CountingSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        count.incrementAndGet();
        return delegate.get();
    }

    /**
     * @return the number of times {@link #get()} has been called
     */
    public int getCount() {
        return count.get();
    }
}
