package org.jdbscript.examples.domaindsl;

/**
 * Domain-specific extension of {@link IAppSchema}.
 * <p>
 * Extending the base schema interface with {@code default} helper methods allows test suites
 * to create rich, multi-table aggregates and business concepts in single expressive calls
 * using {@code engine.as(ICustomerOrderDSL.class)}.
 */
public interface ICustomerOrderDSL extends IAppSchema {

    record Item(String productName, int quantity, double unitPrice) {
        public static Item item(String productName, int quantity, double unitPrice) {
            return new Item(productName, quantity, unitPrice);
        }
    }

    default ICustomerRecord addCustomer(long customerId, String name, String tier) {
        return customers()
                .id(customerId)
                .name(name)
                .email(name.toLowerCase().replace(" ", ".") + "@example.com")
                .tier(tier);
    }

    default IOrderRecord addOrderWithItems(
            long orderId,
            long customerId,
            String orderNumber,
            String status,
            Item... items
    ) {
        double total = 0.0;
        long baseItemId = orderId * 100;
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            total += item.quantity() * item.unitPrice();
            order_items()
                    .id(baseItemId + i + 1)
                    .order_id(orderId)
                    .product_name(item.productName())
                    .quantity(item.quantity())
                    .unit_price(item.unitPrice());
        }
        return orders()
                .id(orderId)
                .customer_id(customerId)
                .order_number(orderNumber)
                .status(status)
                .total_amount(total);
    }
}
