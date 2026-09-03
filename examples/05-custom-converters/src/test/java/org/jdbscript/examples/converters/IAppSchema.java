package org.jdbscript.examples.converters;

import org.jdbscript.IDBSchema;

/**
 * Compare this to an XML/CSV dataset: there, every column value is already text, so "how do I
 * store my own domain type" isn't even a question you get to ask. Here, a record method can take
 * any type — {@link Money}, an enum, whatever your app already has — and a converter decides how
 * it becomes something JDBC can bind.
 */
public interface IAppSchema extends IDBSchema {

    IProductRecord products();

    interface IProductRecord extends IDBRecord {
        IProductRecord id(Integer id);

        IProductRecord name(String name);

        IProductRecord price(Money price);

        IProductRecord status(ProductStatus status);
    }
}
