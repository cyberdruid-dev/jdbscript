package org.jdbscript.errors;

public class JDBScriptException extends RuntimeException {

    public JDBScriptException(String message) {
        super(message);
    }

    public JDBScriptException(String msg, Throwable e) {
        super(msg, e);
    }
}
