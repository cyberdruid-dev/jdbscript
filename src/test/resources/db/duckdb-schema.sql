CREATE TABLE IF NOT EXISTS table_1 (
    str_column_1 VARCHAR(50),
    str_column_2 VARCHAR(60),
    int_column_1 INTEGER,
    long_column_1 INTEGER
);

CREATE TABLE IF NOT EXISTS table_2 (
    int_column_1 INTEGER,
    long_column_1 INTEGER
);

CREATE TABLE IF NOT EXISTS table_with_defaults (
    int_column_1 INTEGER,
    str_column_1 VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS int_table (
    int_column INTEGER
);

CREATE TABLE IF NOT EXISTS varchar_table (
    varchar_column VARCHAR(150)
);

CREATE TABLE IF NOT EXISTS boolean_table (
    boolean_column BOOLEAN
);

CREATE TABLE IF NOT EXISTS date_table (
    date_column DATE
);

CREATE TABLE IF NOT EXISTS timestamp_table (
    timestamp_column TIMESTAMP
);

CREATE TABLE IF NOT EXISTS uuid_table (
    uuid_column UUID
);

CREATE TABLE IF NOT EXISTS blob_table (
    blob_column BLOB
);

CREATE SEQUENCE IF NOT EXISTS generated_int_id_seq;
CREATE TABLE IF NOT EXISTS generated_int_id_table (
    generated_id_column INTEGER PRIMARY KEY DEFAULT nextval('generated_int_id_seq'),
    varchar_column VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS table_for_assertions (
    str_column_1 VARCHAR(50),
    str_column_2 VARCHAR(50),
    int_column_1 INTEGER,
    boolean_column_1 BOOLEAN,
    date_column_1 TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customers (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    order_date VARCHAR(20),
    CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id INTEGER PRIMARY KEY,
    order_id INTEGER NOT NULL,
    product_name VARCHAR(100),
    quantity INTEGER,
    CONSTRAINT fk_order_items_orders FOREIGN KEY (order_id) REFERENCES orders(id)
);
