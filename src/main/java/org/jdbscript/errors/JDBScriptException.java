package org.jdbscript.errors;

/**
 * Base unchecked exception thrown for errors during JDBScript configuration,
 * schema proxying, data conversion, or SQL execution.
 */
public class JDBScriptException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public JDBScriptException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param e   the cause
     */
    public JDBScriptException(String msg, Throwable e) {
        super(msg, e);
    }
}
