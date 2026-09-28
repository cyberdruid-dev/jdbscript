package org.jdbscript.examples.classscripts;

import org.jdbscript.IDBSchema;

public interface IAppSchema extends IDBSchema {

    IUserRecord users();

    IOrderRecord orders();

    interface IUserRecord extends IDBRecord {
        IUserRecord id(Long id);

        IUserRecord username(String username);

        IUserRecord email(String email);

        IUserRecord active(Boolean active);
    }

    interface IOrderRecord extends IDBRecord {
        IOrderRecord id(Long id);

        IOrderRecord user_id(Long userId);

        IOrderRecord total_amount(Double amount);
    }
}
