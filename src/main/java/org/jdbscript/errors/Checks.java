package org.jdbscript.errors;

public final class Checks {

    public static void checkThat(boolean value, JdbsErrors error) {
        if(!value) {
            throw error.get();
        }
    }

    public static <T> T checkNotNull(T value, JdbsErrors error) {
        if(value == null) {
            throw error.get();
        }
        return value;
    }

    public static void checkIsNull(Object value, JdbsErrors error) {
        if(value != null) {
            throw error.get();
        }
    }
}

