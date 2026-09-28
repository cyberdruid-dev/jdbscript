package org.jdbscript.examples.testcontainers;

import org.jdbscript.IDBSchema;

/**
 * Identical shape to 01-quickstart's schema interface. The point of this example is that
 * jdbscript's API doesn't change when the database behind it does - only how the
 * {@link javax.sql.DataSource} is created changes (see {@link TestcontainersTest}).
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
