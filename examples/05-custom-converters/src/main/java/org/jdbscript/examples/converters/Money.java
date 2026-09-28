package org.jdbscript.examples.converters;

/**
 * An ordinary domain value type — the kind of thing a real app would already have, not something
 * built for this example. jdbscript has never heard of it; that's the point of
 * {@link MoneyConverter}.
 */
public record Money(long cents) {

    public static Money dollars(double amount) {
        return new Money(Math.round(amount * 100));
    }
}
