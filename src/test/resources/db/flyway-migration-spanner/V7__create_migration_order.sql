CREATE TABLE migration_order (
    id INT64 NOT NULL,
    customer_id INT64,
    CONSTRAINT fk_migration_order_customer FOREIGN KEY (customer_id) REFERENCES migration_customer(id)
) PRIMARY KEY (id);
