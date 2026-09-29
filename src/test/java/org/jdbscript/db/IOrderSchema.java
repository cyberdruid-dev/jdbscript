package org.jdbscript.db;

import org.jdbscript.IDBSchema;

public interface IOrderSchema extends IDBSchema {
    ICustomerRecord customers();
    IOrderRecord orders();
    IOrderItemRecord order_items();
}
