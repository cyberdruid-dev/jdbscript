DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100),
    email VARCHAR(255),
    active BOOLEAN
);
