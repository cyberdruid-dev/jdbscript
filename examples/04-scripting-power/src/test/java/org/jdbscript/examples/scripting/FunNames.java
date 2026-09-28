package org.jdbscript.examples.scripting;

import java.util.List;
import java.util.Random;

/**
 * A test-data-generation helper — not part of the system under test, just something a resetDB
 * script can call. A little homage to Docker's {@code adjective_animal} container names.
 */
final class FunNames {

    private static final List<String> ADJECTIVES = List.of(
            "turbo", "sneaky", "grumpy", "curious", "feral", "cosmic", "soggy", "spicy",
            "glorious", "reckless", "sleepy", "chaotic", "dapper", "wobbly", "fearless");

    private static final List<String> ANIMALS = List.of(
            "yak", "otter", "walrus", "gremlin", "badger", "penguin", "raccoon", "narwhal",
            "wombat", "ferret", "platypus", "possum", "capybara", "mongoose", "lemur");

    private FunNames() {
    }

    static String next(Random random) {
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + "_"
                + ANIMALS.get(random.nextInt(ANIMALS.size()));
    }
}
