CREATE SEQUENCE IF NOT EXISTS table_1_id_seq;
CREATE TABLE IF NOT EXISTS table_1 (
    id BIGINT PRIMARY KEY DEFAULT nextval('table_1_id_seq'),
    str_column_1 VARCHAR(50),
    str_column_2 VARCHAR(60),
    int_column_1 INTEGER,
    long_column_1 INTEGER
);

CREATE SEQUENCE IF NOT EXISTS table_2_id_seq;
CREATE TABLE IF NOT EXISTS table_2 (
    id BIGINT PRIMARY KEY DEFAULT nextval('table_2_id_seq'),
    int_column_1 INTEGER,
    long_column_1 INTEGER
);

CREATE SEQUENCE IF NOT EXISTS table_with_defaults_id_seq;
CREATE TABLE IF NOT EXISTS table_with_defaults (
    id BIGINT PRIMARY KEY DEFAULT nextval('table_with_defaults_id_seq'),
    int_column_1 INTEGER,
    str_column_1 VARCHAR(100)
);

CREATE SEQUENCE IF NOT EXISTS int_table_id_seq;
CREATE TABLE IF NOT EXISTS int_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('int_table_id_seq'),
    int_column INTEGER
);

CREATE SEQUENCE IF NOT EXISTS varchar_table_id_seq;
CREATE TABLE IF NOT EXISTS varchar_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('varchar_table_id_seq'),
    varchar_column VARCHAR(150)
);

CREATE SEQUENCE IF NOT EXISTS boolean_table_id_seq;
CREATE TABLE IF NOT EXISTS boolean_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('boolean_table_id_seq'),
    boolean_column BOOLEAN
);

CREATE SEQUENCE IF NOT EXISTS date_table_id_seq;
CREATE TABLE IF NOT EXISTS date_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('date_table_id_seq'),
    date_column DATE
);

CREATE SEQUENCE IF NOT EXISTS timestamp_table_id_seq;
CREATE TABLE IF NOT EXISTS timestamp_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('timestamp_table_id_seq'),
    timestamp_column TIMESTAMP
);

CREATE SEQUENCE IF NOT EXISTS uuid_table_id_seq;
CREATE TABLE IF NOT EXISTS uuid_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('uuid_table_id_seq'),
    uuid_column UUID
);

CREATE SEQUENCE IF NOT EXISTS blob_table_id_seq;
CREATE TABLE IF NOT EXISTS blob_table (
    id BIGINT PRIMARY KEY DEFAULT nextval('blob_table_id_seq'),
    blob_column BLOB
);

CREATE SEQUENCE IF NOT EXISTS generated_int_id_seq;
CREATE TABLE IF NOT EXISTS generated_int_id_table (
    generated_id_column INTEGER PRIMARY KEY DEFAULT nextval('generated_int_id_seq'),
    varchar_column VARCHAR(50)
);

CREATE SEQUENCE IF NOT EXISTS table_for_assertions_id_seq;
CREATE TABLE IF NOT EXISTS table_for_assertions (
    id BIGINT PRIMARY KEY DEFAULT nextval('table_for_assertions_id_seq'),
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
