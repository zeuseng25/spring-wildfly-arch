-- Oracle DDL — ABC şeması (FREEPDB1)
-- Manuel uygulanır; Hibernate ddl-auto=none.
-- Uygulama: docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < schema.sql

CREATE TABLE products (
    id     NUMBER(19)     NOT NULL,
    name   VARCHAR2(255)  NOT NULL,
    price  NUMBER(19, 2)  NOT NULL,
    stock  NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
);

CREATE SEQUENCE products_seq START WITH 1 INCREMENT BY 1 NOCACHE;
