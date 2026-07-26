package com.zeus.springwildflyarch.repository;

import com.zeus.springwildflyarch.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * Veri erişim sözleşmesi. Artık JpaRepository DEĞİL — tüm işlemler Oracle
 * PRODUCT_PKG stored procedure'leri çağrılarak yapılır.
 *
 * İki implementasyon vardır ve aynı anda yalnızca biri aktiftir
 * (app.repository.impl property'si ile seçilir):
 *  - {@link JdbcProductRepository} (varsayılan, Spring JDBC / SimpleJdbcCall)
 *  - {@link JpaProductRepository}  (JPA / EntityManager StoredProcedureQuery)
 */
public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(Long id);

    /** Yeni kayıt ekler ve id'si atanmış Product döner. */
    Product create(Product product);

    /** Günceller; bir satır etkilendiyse true. */
    boolean update(Long id, Product product);

    /** Siler; bir satır etkilendiyse true. */
    boolean delete(Long id);
}
