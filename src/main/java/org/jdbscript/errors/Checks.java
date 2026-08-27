package org.jdbscript.errors;

/**
 * Internal validation and precondition checking utilities.
 */
public final class Checks {

    private Checks() {
    }

    /**
     * Asserts that a boolean condition is true, throwing the specified error if false.
     *
     * @param value the condition to check
     * @param error the error to throw if the condition is false
     */
    public static void checkThat(boolean value, JdbsErrors error) {
        if(!value) {
            throw error.get();
        }
    }

    /**
     * Asserts that an object value is not null, throwing the specified error if null.
     *
     * @param <T>   the value type
     * @param value the object to check
     * @param error the error to throw if the value is null
     * @return the non-null value
     */
    public static <T> T checkNotNull(T value, JdbsErrors error) {
        if(value == null) {
            throw error.get();
        }
        return value;
    }

    /**
     * Asserts that an object value is null, throwing the specified error if not null.
     *
     * @param value the object to check
     * @param error the error to throw if the value is not null
     */
    public static void checkIsNull(Object value, JdbsErrors error) {
        if(value != null) {
            throw error.get();
        }
    }
}

