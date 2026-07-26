# 12 — Repository Benchmark: JDBC vs JPA

## Amaç
`ProductRepository`'nin iki implementasyonunu (`JdbcProductRepository` — Spring JDBC `SimpleJdbcCall`; `JpaProductRepository` — JPA `EntityManager.createStoredProcedureQuery`) **performans açısından** karşılaştırmak. İki impl de bire bir aynı `PRODUCT_PKG` stored procedure'lerini çağırır; veritabanı tarafındaki iş aynıdır, fark yalnızca JDBC sürücüsüne kadar olan **client-side soyutlama katmanındadır**. Detaylı toggle anlatımı için bkz. `05-stored-procedure-veri-erisimi.md`.

## Benchmark Testi
`src/test/java/com/zeus/springwildflyarch/repository/RepositoryBenchmarkTest.java`

Basit zaman ölçer (JMH değil — warmup + ortalama). Tasarım kararları:

- `@ActiveProfiles("local")` → doğrudan JDBC ile Docker'daki Oracle'a bağlanır (H2 değil).
- `app.repository.impl=jpa` verilir ki **JPA bean'i aktif** olsun; `JdbcProductRepository` ise `@ConditionalOnProperty`'ye takılmadan DataSource'tan **elle** kurulur. Böylece ikisi tek context'te yan yana ölçülür.
- `@TestPropertySource` ile üç kritik override:
  - `spring.jpa.database-platform=org.hibernate.dialect.OracleDialect` — test classpath'indeki `src/test/resources/application.properties` H2 dialect'i zorlar; Oracle REF_CURSOR için Oracle dialect şart.
  - **`spring.jpa.hibernate.ddl-auto=none`** — KRİTİK. Test props'taki `create-drop` (H2 için) gerçek Oracle'a uygulanırsa **`products` tablosunu DROP eder** (bkz. aşağıdaki uyarı).
  - `spring.jpa.show-sql=false` — ölçüm gürültüsünü azalt.
- **Çağrı başına 1 transaction** (`TransactionTemplate`): üretimdeki "HTTP isteği başına 1 `@Transactional`" yaşam döngüsünü taklit eder ve her çağrıda JPA'nın açtığı REF CURSOR'ın kapanmasını sağlar. Tek uzun tx kullanılırsa cursor'lar birikir → `ORA-01000: maximum open cursors exceeded`.
- `findAll` farkının görünmesi için geçici **1000 kayıt** (`BENCH_` önekli) eklenir, ölçüm sonrası `@AfterAll` ile silinir.
- Normal `mvn test`'i (H2) bozmamak için `@EnabledIfSystemProperty(named="benchmark", matches="true")` ile gate'lenir — yani `-Dbenchmark=true` yoksa **atlanır**.

### Çalıştırma
```bash
# Oracle container (fw-batch-oracle) açık olmalı
mvn test -Dtest=RepositoryBenchmarkTest -Dbenchmark=true
```

## Sonuçlar
Oracle, tabloda ~1005 satır, çağrı başına 1 transaction, 3 koşunun tutarlı aralığı:

| İşlem | JDBC | JPA | JPA/JDBC |
|-------|------|-----|----------|
| `findAll` (1005 satır) | ~1900–2025 µs/op | ~2230–2540 µs/op | **1.14–1.33x yavaş** |
| `findById` (1 satır) | ~390–440 µs/op | ~425–460 µs/op | **≈ 1.00x (eşit)** |

### Yorum
- **`findAll` (çok satır) → JDBC ~%15–30 daha hızlı.** JPA dönen her satırı `Product` **entity** olarak hidrate edip persistence context'e ekler (managed state + dirty-check snapshot). JDBC ise `RowMapper` ile düz POJO üretir, yönetim yükü yoktur. Result set büyüdükçe makas açılır.
- **`findById` (tek satır) → fark yok.** Toplam süre Oracle round-trip'i tarafından domine edilir; client-side soyutlama farkı gürültü seviyesinde kalır.

## Avantaj / Dezavantaj

**JdbcProductRepository (SimpleJdbcCall) — varsayılan**
- ✅ Daha hızlı (özellikle listede); call'lar constructor'da bir kez kurulur, `RowMapper` statik tek instance.
- ✅ Stateless, persistence-context yükü yok; düşük bellek.
- ✅ Parametreler **isimle** eşlenir (`P_ID`) → PL/SQL imza sırası değişse de kırılmaz.
- ✅ İlk çağrıda procedure metadata'sını okuyup cache'ler.
- ❌ Sonuç eşlemesi manuel (`RowMapper`).
- ❌ Entity yönetimi / ilişki / lazy-load yok (bu projede gerekmiyor).

**JpaProductRepository (StoredProcedureQuery) — alternatif**
- ✅ Hibernate entity mapping hazır (`Product.class`); ilişki/lazy-load gerekseydi avantaj olurdu.
- ✅ Aynı `EntityManager`/transaction ekosistemiyle bütünleşik.
- ❌ Listede daha yavaş (entity hidrasyonu + persistence context).
- ❌ Her metot çağrısında query yeniden kurulur, parametreler elle register edilir.
- ❌ Parametreler **pozisyonel** → PL/SQL imza sırasına bağımlı, daha kırılgan.
- ❌ REF CURSOR'ları aktif tx olmadan kullanmak zor; uzun tx'te cursor sızıntısı riski (`ORA-01000`).

## Sonuç
Bu projenin **"SP-only, entity ilişkisi yok"** mimarisinde JPA'nın soyutlamaları kullanılmıyor — yükünü taşıyıp faydasını vermiyor. JDBC implementasyonu hem daha yalın hem ölçülebilir biçimde daha hızlı. **Varsayılanın `jdbc` olması doğru tercih.** JPA yalnızca ileride gerçek entity/ilişki yönetimine geçilirse anlam kazanır.

## ⚠️ Uyarı — Benchmark sırasında veri kaybı yaşandı
Test geliştirilirken `ddl-auto` override'ı eklenmeden önce, test classpath'indeki `create-drop` ayarı gerçek Oracle'da **`products` tablosunu ve `products_seq` sequence'ini DROP etti**. Yapı `schema.sql` + `procedures.sql` ile geri yüklendi, ancak orijinal 5 ürün kaydı (seed dosyası olmadığı için) kurtarılamadı; yerine 5 temsili kayıt (Laptop, Mouse, Keyboard, Monitor, Webcam) eklendi.

Alınan önlem: `@TestPropertySource` içinde `spring.jpa.hibernate.ddl-auto=none`. **Oracle'a bağlanan herhangi bir test mutlaka `ddl-auto=none` ile koşturulmalı.**

Öneri: tekrar yaşanmaması için kalıcı bir seed dosyası (`db/data.sql`) tutulmalı.
