package org.jdbscript;

import java.util.function.Consumer;

/**
 * TODO: document it!
 */
public interface IDbSchema {
    void include(Consumer<? extends IDbSchema> script);

    void include(Class<? extends IDbSchema> script);

    interface IDBRecord {

    }
}
