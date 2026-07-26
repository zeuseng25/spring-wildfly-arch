-- Oracle PL/SQL paketi — PRODUCT_PKG (ABC şeması / FREEPDB1)
-- Veri erişimi JpaRepository ile DEĞİL, bu procedure'ler çağrılarak yapılır.
-- products tablosu + products_seq sequence için db/schema.sql önce uygulanmalı.
--
-- Uygula:
--   docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/procedures.sql

CREATE OR REPLACE PACKAGE product_pkg AS

    -- Tüm kayıtları REF CURSOR olarak döner
    PROCEDURE get_all(p_cur OUT SYS_REFCURSOR);

    -- Tek kaydı id ile döner
    PROCEDURE get_by_id(p_id IN NUMBER, p_cur OUT SYS_REFCURSOR);

    -- Yeni kayıt ekler; üretilen id'yi p_id OUT ile döner
    PROCEDURE create_product(
        p_name  IN  VARCHAR2,
        p_price IN  NUMBER,
        p_stock IN  NUMBER,
        p_id    OUT NUMBER
    );

    -- Kaydı günceller; etkilenen satır sayısını p_rows OUT ile döner
    PROCEDURE update_product(
        p_id    IN  NUMBER,
        p_name  IN  VARCHAR2,
        p_price IN  NUMBER,
        p_stock IN  NUMBER,
        p_rows  OUT NUMBER
    );

    -- Kaydı siler; etkilenen satır sayısını p_rows OUT ile döner
    PROCEDURE delete_product(p_id IN NUMBER, p_rows OUT NUMBER);

END product_pkg;
/

CREATE OR REPLACE PACKAGE BODY product_pkg AS

    PROCEDURE get_all(p_cur OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN p_cur FOR
            SELECT id, name, price, stock
            FROM products
            ORDER BY id;
    END get_all;

    PROCEDURE get_by_id(p_id IN NUMBER, p_cur OUT SYS_REFCURSOR) IS
    BEGIN
        OPEN p_cur FOR
            SELECT id, name, price, stock
            FROM products
            WHERE id = p_id;
    END get_by_id;

    PROCEDURE create_product(
        p_name  IN  VARCHAR2,
        p_price IN  NUMBER,
        p_stock IN  NUMBER,
        p_id    OUT NUMBER
    ) IS
    BEGIN
        p_id := products_seq.NEXTVAL;
        INSERT INTO products (id, name, price, stock)
        VALUES (p_id, p_name, p_price, p_stock);
    END create_product;

    PROCEDURE update_product(
        p_id    IN  NUMBER,
        p_name  IN  VARCHAR2,
        p_price IN  NUMBER,
        p_stock IN  NUMBER,
        p_rows  OUT NUMBER
    ) IS
    BEGIN
        UPDATE products
        SET name = p_name,
            price = p_price,
            stock = p_stock
        WHERE id = p_id;
        p_rows := SQL%ROWCOUNT;
    END update_product;

    PROCEDURE delete_product(p_id IN NUMBER, p_rows OUT NUMBER) IS
    BEGIN
        DELETE FROM products WHERE id = p_id;
        p_rows := SQL%ROWCOUNT;
    END delete_product;

END product_pkg;
/
