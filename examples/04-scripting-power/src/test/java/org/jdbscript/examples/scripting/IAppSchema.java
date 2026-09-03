package org.jdbscript.examples.scripting;

import org.jdbscript.IDBSchema;

public interface IAppSchema extends IDBSchema {

    IPlayerRecord players();

    interface IPlayerRecord extends IDBRecord {
        IPlayerRecord id(Integer id);

        IPlayerRecord username(String username);

        IPlayerRecord score(Integer score);
    }
}
