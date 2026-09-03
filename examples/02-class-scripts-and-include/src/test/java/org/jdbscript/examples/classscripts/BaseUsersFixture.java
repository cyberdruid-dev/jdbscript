package org.jdbscript.examples.classscripts;

/**
 * A reusable base dataset that every test in this area can start from, defined once as a
 * class-based script instead of being copy-pasted into every test's {@code resetDB} lambda.
 * <p>
 * jdbscript instantiates this for you — it's fine for it to be abstract, as here, since jdbscript
 * generates a concrete subclass to run it. Just declare the rows to insert in an instance
 * initializer.
 */
public abstract class BaseUsersFixture implements IAppSchema {
    {
        users().id(1L).username("admin").email("admin@example.com").active(true);
        users().id(2L).username("guest").email("guest@example.com").active(true);
    }
}
