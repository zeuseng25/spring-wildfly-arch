package com.zeus.springwildflyarch.repository;

import com.zeus.framework.database.sp.StoredProcedureExecutors;
import com.zeus.springwildflyarch.model.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring JDBC implementasyonu (VARSAYILAN — app.repository.impl=jdbc veya tanımsız).
 *
 * <p>Datasource'u {@link StoredProcedureExecutors}'ın isimli getter'ıyla seçer: {@code sp.getOracleDs()}.
 * Böylece hangi veritabanına gidildiği çağrı yerinde açıktır; başka bir repository {@code sp.getReportDs()}
 * ile ReportingDS'i seçer. Datasource'ları zeus-database yönetir — app'te datasource bean'i / JNDI kodu /
 * property'si YOKTUR. Oracle PRODUCT_PKG procedure'lerini çağırır; REF CURSOR → {@link RowMapper}, OUT → map.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.impl", havingValue = "jdbc", matchIfMissing = true)
public class JdbcProductRepository implements ProductRepository {

    private static final String PKG = "PRODUCT_PKG";
    private static final String CURSOR = "P_CUR";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> Product.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .price(rs.getBigDecimal("price"))
            .stock(rs.getInt("stock"))
            .build();

    private final StoredProcedureExecutors sp;

    public JdbcProductRepository(StoredProcedureExecutors sp) {
        this.sp = sp;
    }

    @Override
    public List<Product> findAll() {
        return sp.getOracleDs().query(PKG, "GET_ALL", CURSOR, ROW_MAPPER);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return sp.getOracleDs().query(PKG, "GET_BY_ID", CURSOR, ROW_MAPPER, Map.of("P_ID", id))
                .stream().findFirst();
    }

    @Override
    public Product create(Product product) {
        Map<String, Object> out = sp.getOracleDs().execute(PKG, "CREATE_PRODUCT", Map.of(
                "P_NAME", product.getName(),
                "P_PRICE", product.getPrice(),
                "P_STOCK", product.getStock()));
        product.setId(((Number) out.get("P_ID")).longValue());
        return product;
    }

    @Override
    public boolean update(Long id, Product product) {
        Map<String, Object> out = sp.getOracleDs().execute(PKG, "UPDATE_PRODUCT", Map.of(
                "P_ID", id,
                "P_NAME", product.getName(),
                "P_PRICE", product.getPrice(),
                "P_STOCK", product.getStock()));
        return ((Number) out.get("P_ROWS")).intValue() > 0;
    }

    @Override
    public boolean delete(Long id) {
        Map<String, Object> out = sp.getOracleDs().execute(PKG, "DELETE_PRODUCT", Map.of("P_ID", id));
        return ((Number) out.get("P_ROWS")).intValue() > 0;
    }
}
