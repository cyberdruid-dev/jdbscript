package org.jdbscript;

import org.flywaydb.core.Flyway;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationRunnerFactoryClassLoaderTest {

    @Test
    public void classExists_should_find_a_class_reachable_only_via_the_context_classloader() throws Exception {
        // Simulates an app-server-style setup where jdbscript's own classes and Flyway sit on
        // different classloaders, with Flyway reachable only via the current thread's context
        // classloader - loaderA below can't see Flyway at all, loaderB can see only Flyway.
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        URL jdbscriptClasses = MigrationRunnerFactory.class.getProtectionDomain().getCodeSource().getLocation();
        URL flywayJar = Flyway.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader loaderA = new URLClassLoader(new URL[]{jdbscriptClasses}, platform);
        URLClassLoader loaderB = new URLClassLoader(new URL[]{flywayJar}, platform);

        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loaderB);
            Class<?> factoryClass = Class.forName("org.jdbscript.MigrationRunnerFactory", true, loaderA);
            Method classExists = factoryClass.getDeclaredMethod("classExists", String.class);
            classExists.setAccessible(true);

            boolean result = (boolean) classExists.invoke(null, "org.flywaydb.core.Flyway");

            assertThat(result)
                    .describedAs("Flyway is reachable via the thread's context classloader, even "
                            + "though MigrationRunnerFactory's own classloader can't see it")
                    .isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContext);
        }
    }
}
