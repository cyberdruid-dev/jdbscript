package org.jdbscript.impl.cache;

import org.jdbscript.JdbAbstractTest;
import org.jdbscript.impl.cache.IJDBCache.IJDBCacheKey;
import org.testng.annotations.BeforeClass;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractCacheTest extends JdbAbstractTest {
    protected JDBCacheManager cacheManager;
    protected DataSource defaultDataSource;

    @BeforeClass
    public void setUpCacheManager() {
        cacheManager = JDBCacheManager.getInstance();
        defaultDataSource = mockDataSource("jdbc:h2:mem:test", "sa", "PUBLIC");
    }

    protected record TestStringKey(String name) implements IJDBCacheKey<String> {
    }

    protected static class ComputationMock<V> implements Function<IJDBCacheKey<V>, V> {
        private final V value;
        private int calls = 0;

        public ComputationMock(V value) {
            this.value = value;
        }

        @Override
        public V apply(IJDBCacheKey<V> k) {
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

    protected static DataSource mockDataSource(String url, String user, String schema) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return mockConnection(url, user, schema);
                    }
                    if ("isWrapperFor".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
    }

    protected static Connection mockConnection(String url, String user, String schema) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getMetaData":
                            return mockMetaData(url, user);
                        case "getCatalog":
                            return schema;
                        case "getSchema":
                            return schema;
                        case "close":
                            return null;
                        case "isWrapperFor":
                            return false;
                    }
                    return null;
                }
        );
    }

    protected static DatabaseMetaData mockMetaData(String url, String user) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getURL":
                            return url;
                        case "getUserName":
                            return user;
                    }
                    return null;
                }
        );
    }
}
