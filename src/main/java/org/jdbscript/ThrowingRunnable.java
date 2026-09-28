package org.jdbscript;

/**
 * Like {@link Runnable}, but allowed to throw a checked exception.
 */
@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
