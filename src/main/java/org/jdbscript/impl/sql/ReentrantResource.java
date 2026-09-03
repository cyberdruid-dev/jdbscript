package org.jdbscript.impl.sql;

import java.util.function.Supplier;

/**
 * Holds at most one {@code T} per thread, created on first use via {@code supplier} and shared by
 * any nested/reentrant {@link #run} calls on that same thread instead of creating a second one.
 * The resource is only actually closed once the outermost such call on this thread returns.
 * <p>
 * Used by {@link SqlConnectionProvider} so that a nested {@code withConnection()} call on the same
 * thread (e.g. a metadata lookup triggered while an insert's connection is still open) reuses that
 * connection instead of acquiring a second one from the same {@code DataSource}/pool, which can
 * exhaust/deadlock a pool sized to 1.
 *
 * @param <T> the resource type
 */
class ReentrantResource<T extends AutoCloseable> {

    private final ThreadLocal<ResourceCount<T>> localResource = new ThreadLocal<>();
    private final Supplier<T> supplier;

    private static class ResourceCount<T extends AutoCloseable> {
        private final T resource;
        private int count = 0;

        private ResourceCount(T resource) {
            this.resource = resource;
        }
    }

    ReentrantResource(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * Gets (creating if needed) the resource for the current thread and runs {@code consumer}
     * against it. Only the outermost call on this thread actually closes the resource when it
     * returns - running {@code callback.beforeClose} first - or releases it (without ever having
     * run {@code callback.beforeClose} or closed it) if {@code consumer} or {@code callback} fails.
     *
     * @param consumer run against the resource on every call, fresh or reentrant
     * @param callback lifecycle hooks; see {@link IReentrantResourceCallback}
     */
    void run(ThrowingConsumer<T> consumer, IReentrantResourceCallback<T> callback) throws Exception {
        ResourceCount<T> resource = localResource.get();
        boolean freshlyCreated = resource == null;
        if (freshlyCreated) {
            resource = new ResourceCount<>(supplier.get());
            localResource.set(resource);
        }
        ResourceCount<T> opened = resource;
        opened.count++;
        try {
            if (freshlyCreated) {
                callback.afterOpen(opened.resource);
            }
            consumer.accept(opened.resource);
        } catch (Throwable e) {
            // Catches Throwable, not just RuntimeException: e.g. AssertionFailedError (thrown
            // deliberately by assertDBHas/assertDBHasNot on a normal, expected assertion failure)
            // extends AssertionError, not RuntimeException. This is the only release for a failed
            // acquisition, so it must run here rather than being left to a caller - otherwise a
            // freshly-created resource would get stuck in the ThreadLocal forever, un-closeable,
            // blocking every later call on this thread.
            releaseQuietly(opened, callback, e);
            throw e;
        }
        // Only reached on success, after the try/catch above - so a failure here (e.g. commit())
        // propagates directly, instead of being caught by that catch and treated as a second,
        // spurious release of the same acquisition.
        close(opened, callback);
    }

    private void releaseQuietly(ResourceCount<T> resource, IReentrantResourceCallback<T> callback, Throwable primary) {
        try {
            close(resource, callback);
        } catch (Exception releaseFailure) {
            primary.addSuppressed(releaseFailure);
        }
    }

    private void close(ResourceCount<T> resource, IReentrantResourceCallback<T> callback) throws Exception {
        resource.count--;
        if (resource.count == 0) {
            localResource.remove();
            // resource.resource.close() must run even if beforeClose() (e.g. commit()) fails -
            // otherwise a failed commit would leak the underlying connection instead of
            // returning it to the pool.
            try {
                callback.beforeClose(resource.resource);
            } catch (Throwable beforeCloseFailure) {
                try {
                    resource.resource.close();
                } catch (Exception closeFailure) {
                    beforeCloseFailure.addSuppressed(closeFailure);
                }
                throw beforeCloseFailure;
            }
            resource.resource.close();
        }
    }
}
