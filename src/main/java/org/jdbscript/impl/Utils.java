package org.jdbscript.impl;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

public class Utils {

    // invokeDefault needs the interface to be accessible from here; a private or package-private
    // one (e.g. nested in a test class) isn't, so go through a private lookup on the interface.
    static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return InvocationHandler.invokeDefault(proxy, method, args);
        } catch (IllegalAccessException e) {
            Class<?> declaringInterface = method.getDeclaringClass();
            return MethodHandles.privateLookupIn(declaringInterface, MethodHandles.lookup())
                    .unreflectSpecial(method, declaringInterface)
                    .bindTo(proxy)
                    .invokeWithArguments(args == null ? new Object[0] : args);
        }
    }

    public static String resolveIdentifier(String name, List<String> candidates,
                                           Function<List<String>, RuntimeException> ambiguityError) {
        if (candidates.contains(name)) {
            return name;
        }
        List<String> matches = candidates.stream()
                .filter(c -> c.equalsIgnoreCase(name))
                .toList();
        if (matches.size() > 1) {
            throw ambiguityError.apply(matches);
        }
        return matches.isEmpty() ? name : matches.get(0);
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
