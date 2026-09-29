package org.jdbscript.db;

import org.jdbscript.IDBSchema.IDBRecord;

public interface IOrderItemRecord extends IDBRecord {
    IOrderItemRecord id(int value);
    IOrderItemRecord order_id(int value);
    IOrderItemRecord product_name(String value);
    IOrderItemRecord quantity(int value);
}
