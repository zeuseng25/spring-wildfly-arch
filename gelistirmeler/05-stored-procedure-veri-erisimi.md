# 05 — Stored Procedure ile Veri Erişimi

## Amaç
Veri erişimi `JpaRepository` ile otomatik CRUD yerine, Oracle `PRODUCT_PKG` paketindeki **stored procedure'ler çağrılarak** yapılır.

## PL/SQL Paketi — PRODUCT_PKG
`src/main/resources/db/procedures.sql`:

| Procedure | Parametreler | Sonuç |
|-----------|--------------|-------|
| `get_all` | `p_cur OUT SYS_REFCURSOR` | tüm kayıtlar |
| `get_by_id` | `p_id IN`, `p_cur OUT SYS_REFCURSOR` | tek kayıt |
| `create_product` | `p_name/p_price/p_stock IN`, `p_id OUT` | üretilen id (`products_seq`) |
| `update_product` | `p_id/p_name/p_price/p_stock IN`, `p_rows OUT` | etkilenen satır |
| `delete_product` | `p_id IN`, `p_rows OUT` | etkilenen satır |

Uygula:
```bash
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/procedures.sql
# Doğrula:
# SELECT object_name, status FROM user_objects WHERE object_name='PRODUCT_PKG';  → VALID
```

## İki İmplementasyon (toggle)
`ProductRepository` arayüzünün iki impl'i vardır; `app.repository.impl` ile seçilir (aynı anda biri aktif — `@ConditionalOnProperty`):

| `app.repository.impl` | Sınıf | Teknoloji |
|-----------------------|-------|-----------|
| `jdbc` (varsayılan) | `JdbcProductRepository` | Spring JDBC `SimpleJdbcCall` |
| `jpa` | `JpaProductRepository` | JPA `EntityManager.createStoredProcedureQuery` |

### jdbc — SimpleJdbcCall
- `withCatalogName("PRODUCT_PKG").withProcedureName("GET_ALL")`
- REF CURSOR: `returningResultSet("P_CUR", rowMapper)` → `RowMapper<Product>`
- IN/OUT: `MapSqlParameterSource`, çıktı `execute()` Map'inden (`P_ID`, `P_ROWS`).

### jpa — StoredProcedureQuery
- `em.createStoredProcedureQuery("PRODUCT_PKG.GET_ALL", Product.class)`
- Parametreler **pozisyonel** register edilir (PL/SQL sırasıyla), REF CURSOR `ParameterMode.REF_CURSOR`.
- OUT değeri `getOutputParameterValue(pos)`.

## Yeni metot ekleme adımları
1. `procedures.sql`'e PL/SQL procedure ekle, Oracle'a uygula.
2. `ProductRepository` arayüzüne metot ekle.
3. `JdbcProductRepository` ve `JpaProductRepository`'ye çağrıyı ekle.

## Doğrulama (her iki yol)
`app.repository.impl=jdbc` ve `=jpa` ile ayrı ayrı deploy edilip CRUD testi yapıldı; ikisi de aynı sonuçları verdi (POST/GET/PUT/DELETE + 404 + 400).

## İlgili
- Paket/tablo: `01-database-oracle.md`
- Katmanlar: `04-katmanli-mimari.md`
