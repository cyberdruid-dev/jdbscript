package org.jdbscript.examples.domaindsl;

/**
 * Domain-specific extension of {@link IAppSchema}, used via {@code engine.as(ICustomerOrderDSL.class)}.
 * <p>
 * Plain record chains are the default. A helper earns its place when it keeps derived data
 * consistent: here, an order's {@code total_amount} always matches its items. It returns the
 * order record, so the test still sets what it depends on with named setters.
 */
public interface ICustomerOrderDSL extends IAppSchema {

    record Item(String productName, int quantity, double unitPrice) {
        public static Item item(String productName, int quantity, double unitPrice) {
            return new Item(productName, quantity, unitPrice);
        }
    }

    default IOrderRecord orderWithItems(long orderId, Item... items) {
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
        return orders().id(orderId).total_amount(total);
    }
}
