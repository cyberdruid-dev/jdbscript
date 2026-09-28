package org.jdbscript.impl.sql;

import org.opentest4j.AssertionFailedError;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReentrantResourceTest {

    private static class FakeResource implements AutoCloseable {
        final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }

    private static IReentrantResourceCallback<FakeResource> recordingCallback(List<String> events) {
        return new IReentrantResourceCallback<>() {
            @Override
            public void afterOpen(FakeResource resource) {
                events.add("afterOpen");
            }

            @Override
            public void beforeClose(FakeResource resource) {
                events.add("beforeClose");
            }
        };
    }

    @Test
    public void run_creates_once_and_runs_lifecycle_once() throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        FakeResource resource = new FakeResource();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> {
            supplierCalls.incrementAndGet();
            return resource;
        });
        List<String> events = new ArrayList<>();

        reentrant.run(r -> events.add("consumer"), recordingCallback(events));

        assertThat(supplierCalls.get()).isEqualTo(1);
        assertThat(events).containsExactly("afterOpen", "consumer", "beforeClose");
        assertThat(resource.closeCount.get()).isEqualTo(1);
    }

    @Test
    public void nested_run_reuses_resource_and_closes_only_once() throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        FakeResource resource = new FakeResource();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> {
            supplierCalls.incrementAndGet();
            return resource;
        });
        List<String> events = new ArrayList<>();
        IReentrantResourceCallback<FakeResource> callback = recordingCallback(events);

        // A true nested call, as it actually happens in practice: the outer consumer itself
        // triggers a nested withConnection()-style call (e.g. insert() triggering a metadata
        // lookup) before returning.
        reentrant.run(r -> {
            events.add("outer-before");
            reentrant.run(r2 -> events.add("inner"), callback);
            assertThat(resource.closeCount.get()).describedAs("outer still holds the resource").isZero();
            events.add("outer-after");
        }, callback);

        assertThat(resource.closeCount.get()).isEqualTo(1);
        assertThat(supplierCalls.get()).describedAs("resource created only once").isEqualTo(1);
        assertThat(events).containsExactly("afterOpen", "outer-before", "inner", "outer-after", "beforeClose");
    }

    @Test
    public void different_threads_get_independent_resources() throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> {
            supplierCalls.incrementAndGet();
            return new FakeResource();
        });
        IReentrantResourceCallback<FakeResource> callback = recordingCallback(new ArrayList<>());

        reentrant.run(r -> {
            Thread other = new Thread(() -> {
                try {
                    reentrant.run(r2 -> {}, callback);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            other.start();
            try {
                other.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }, callback);

        assertThat(supplierCalls.get()).describedAs("each thread gets its own resource").isEqualTo(2);
    }

    @Test
    public void consumer_throwing_runtime_exception_releases_the_resource_for_reuse() throws Exception {
        List<FakeResource> created = new ArrayList<>();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> {
            FakeResource r = new FakeResource();
            created.add(r);
            return r;
        });
        IReentrantResourceCallback<FakeResource> callback = recordingCallback(new ArrayList<>());

        assertThatThrownBy(() -> reentrant.run(r -> {
            throw new RuntimeException("boom");
        }, callback)).isInstanceOf(RuntimeException.class).hasMessage("boom");

        assertThat(created).hasSize(1);
        assertThat(created.get(0).closeCount.get())
                .describedAs("released even though run() itself threw").isEqualTo(1);

        // A later call must not be stuck reusing a broken/already-released entry.
        reentrant.run(r -> {}, callback);

        assertThat(created).describedAs("a fresh resource was created").hasSize(2);
        assertThat(created.get(1).closeCount.get()).isEqualTo(1);
    }

    @Test
    public void consumer_throwing_assertionError_still_releases_the_resource() throws Exception {
        // AssertionFailedError (thrown deliberately by assertDBHas/assertDBHasNot on a normal,
        // expected assertion failure) extends AssertionError, not RuntimeException - a catch that
        // only handled RuntimeException would silently skip cleanup for this case.
        List<FakeResource> created = new ArrayList<>();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> {
            FakeResource r = new FakeResource();
            created.add(r);
            return r;
        });
        IReentrantResourceCallback<FakeResource> callback = recordingCallback(new ArrayList<>());

        assertThatThrownBy(() -> reentrant.run(r -> {
            throw new AssertionFailedError("Expected row to exist.");
        }, callback)).isInstanceOf(AssertionFailedError.class);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).closeCount.get()).isEqualTo(1);
    }

    @Test
    public void beforeClose_throwing_still_closes_the_underlying_resource() {
        FakeResource resource = new FakeResource();
        ReentrantResource<FakeResource> reentrant = new ReentrantResource<>(() -> resource);
        IReentrantResourceCallback<FakeResource> callback = new IReentrantResourceCallback<>() {
            @Override
            public void afterOpen(FakeResource r) {
            }

            @Override
            public void beforeClose(FakeResource r) {
                throw new RuntimeException("commit failed");
            }
        };

        assertThatThrownBy(() -> reentrant.run(r -> {}, callback))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("commit failed");
        assertThat(resource.closeCount.get())
                .describedAs("connection must still be closed/returned to the pool despite the failed commit")
                .isEqualTo(1);
    }
}
