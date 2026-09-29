package org.jdbscript.db;

import org.jdbscript.IDBSchema.IDBRecord;

public interface ICustomerRecord extends IDBRecord {
    ICustomerRecord id(int value);
    ICustomerRecord name(String value);
}
