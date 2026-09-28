CREATE TABLE migration_order (
    id INT PRIMARY KEY,
    customer_id INT,
    CONSTRAINT fk_migration_order_customer FOREIGN KEY (customer_id) REFERENCES migration_customer(id)
);
