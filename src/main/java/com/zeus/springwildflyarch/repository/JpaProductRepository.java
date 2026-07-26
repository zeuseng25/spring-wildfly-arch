package com.zeus.springwildflyarch.repository;

import com.zeus.framework.database.sp.JpaStoredProcedureExecutor;
import com.zeus.springwildflyarch.model.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementasyonu (ALTERNATİF — app.repository.impl=jpa).
 * Aynı PRODUCT_PKG procedure'lerini framework'ün {@link JpaStoredProcedureExecutor}'ı ile
 * (EntityManager.createStoredProcedureQuery, pozisyonel parametre) çağırır.
 *
 * Not: JpaRepository KULLANILMAZ; doğrudan stored procedure çağrısı yapılır.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.impl", havingValue = "jpa")
public class JpaProductRepository implements ProductRepository {

    private final JpaStoredProcedureExecutor sp;

    public JpaProductRepository(JpaStoredProcedureExecutor sp) {
        this.sp = sp;
    }

    @Override
    public List<Product> findAll() {
        return sp.query("PRODUCT_PKG.GET_ALL", Product.class);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return sp.query("PRODUCT_PKG.GET_BY_ID", Product.class, id)
                .stream().findFirst();
    }

    @Override
    public Product create(Product product) {
        Object id = sp.executeWithOut("PRODUCT_PKG.CREATE_PRODUCT", Long.class,
                product.getName(), product.getPrice(), product.getStock());
        product.setId(((Number) id).longValue());
        return product;
    }

    @Override
    public boolean update(Long id, Product product) {
        Object rows = sp.executeWithOut("PRODUCT_PKG.UPDATE_PRODUCT", Long.class,
                id, product.getName(), product.getPrice(), product.getStock());
        return ((Number) rows).intValue() > 0;
    }

    @Override
    public boolean delete(Long id) {
        Object rows = sp.executeWithOut("PRODUCT_PKG.DELETE_PRODUCT", Long.class, id);
        return ((Number) rows).intValue() > 0;
    }
}
