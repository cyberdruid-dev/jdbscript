package org.jdbscript.impl.cache;

import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeClass;
import java.util.Objects;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractCacheTest extends JdbAbstractTest {
    protected JDBCacheManager cacheManager;

    @BeforeClass
    public void setUpCacheManager() {
        cacheManager = JDBCacheManager.getInstance();
    }

    protected static class TestStringKey implements IJDBCache.IJDBCacheKey<String> {
        private final String name;

        public TestStringKey(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "TestStringKey{" + "name='" + name + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestStringKey testKey = (TestStringKey) o;
            return Objects.equals(name, testKey.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    protected static class ComputationMock<V> implements Function<IJDBCache.IJDBCacheKey<V>, V> {
        private final V value;
        private int calls = 0;

        public ComputationMock(V value) {
            this.value = value;
        }

        @Override
        public V apply(IJDBCache.IJDBCacheKey<V> k) {
            calls++;
            return value;
        }

        public void assertInvoked(int expectedCalls) {
            assertThat(calls).isEqualTo(expectedCalls);
        }
    }

    protected TestStringKey key(String name) {
        return new TestStringKey(name);
    }
}
