package org.jdbscript.db;

import org.jdbscript.IDBSchema.IDBRecord;

public interface IOrderRecord extends IDBRecord {
    IOrderRecord id(int value);
    IOrderRecord customer_id(int value);
    IOrderRecord order_date(String value);
}
