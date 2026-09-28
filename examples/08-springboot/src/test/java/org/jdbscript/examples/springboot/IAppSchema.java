package org.jdbscript.examples.springboot;

import org.jdbscript.IDBSchema;

/**
 * Identical shape to 01-quickstart's schema interface - test-only fixture-setup code, unrelated
 * to {@link UserRepository}, the actual (Spring-managed) system under test.
 */
public interface IAppSchema extends IDBSchema {

    IUserRecord users();

    interface IUserRecord extends IDBRecord {
        IUserRecord id(Long id);

        IUserRecord username(String username);

        IUserRecord email(String email);

        IUserRecord active(Boolean active);
    }
}
