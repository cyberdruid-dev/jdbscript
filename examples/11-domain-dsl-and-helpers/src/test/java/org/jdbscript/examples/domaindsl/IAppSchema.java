package org.jdbscript.examples.domaindsl;

import org.jdbscript.IDBSchema;

public interface IAppSchema extends IDBSchema {

    ICustomerRecord customers();

    IOrderRecord orders();

    IOrderItemRecord order_items();

    interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(Long id);

        ICustomerRecord name(String name);

        ICustomerRecord email(String email);

        ICustomerRecord tier(String tier);
    }

    interface IOrderRecord extends IDBRecord {
        IOrderRecord id(Long id);

        IOrderRecord customer_id(Long customerId);

        IOrderRecord order_number(String orderNumber);

        IOrderRecord status(String status);

        IOrderRecord total_amount(Double totalAmount);
    }

    interface IOrderItemRecord extends IDBRecord {
        IOrderItemRecord id(Long id);

        IOrderItemRecord order_id(Long orderId);

        IOrderItemRecord product_name(String productName);

        IOrderItemRecord quantity(Integer quantity);

        IOrderItemRecord unit_price(Double unitPrice);
    }
}
