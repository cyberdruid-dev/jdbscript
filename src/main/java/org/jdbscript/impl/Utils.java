package org.jdbscript.impl;

import java.util.List;
import java.util.function.Function;

public class Utils {

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
