package org.jdbscript.impl.sql;

/**
 * Lifecycle hooks for a {@link ReentrantResource}: {@link #afterOpen} runs exactly once, right
 * after the resource is freshly created (skipped on a reentrant reuse), and {@link #beforeClose}
 * runs exactly once, right before the resource is actually closed (i.e. when the outermost caller
 * releases it).
 *
 * @param <T> the resource type
 */
interface IReentrantResourceCallback<T extends AutoCloseable> {
    void afterOpen(T resource) throws Exception;
    void beforeClose(T resource) throws Exception;
}
