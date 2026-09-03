package org.jdbscript.examples.quickstart;

import org.jdbscript.IDBSchema;

/**
 * The schema interface: one method per table you want jdbscript to know about, each returning
 * an {@link IDBRecord} sub-interface that models that table's columns as fluent setters.
 * <p>
 * This is test-only fixture-setup code — it has nothing to do with {@link UserRepository}, the
 * actual system under test, which just runs plain SQL against whatever tables already exist.
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
