package org.jdbscript.impl.javassist;

/**
 * Hands the script proxy to a generated class script subclass. Public only because the generated
 * class, defined in the script's own package, calls {@link #current()}.
 */
public final class ClassScriptContext {
    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();

    private ClassScriptContext() {
    }

    public static Object current() {
        return CURRENT.get();
    }

    static Object enter(Object proxy) {
        Object previous = CURRENT.get();
        CURRENT.set(proxy);
        return previous;
    }

    static void exit(Object previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
