# CLAUDE.md — spring-wildfly-arch

Bu dosya projenin mimarisini ve çalışma kurallarını tanımlar. Claude Code bu projede çalışırken bu kurallara uyar.

## Ekosistem / Proje İlişkisi (ÖNEMLİ — hangi dizin açılırsa açılsın)

- **`../zeus-fw` = framework** (kurumsal Java framework'ü; Spring Boot tarzı: BOM + parent + modüller).
- **`spring-wildfly-arch` = bu proje**, zeus-fw'ı kullanan **N uygulamadan biri** (örnek/kanıt uygulaması).
  Başka uygulamalar da aynı framework'ü kullanır; **framework'te yapılan değişiklik tüm uygulamaları etkiler.**
- Kural: **framework-içi** (BOM, parent, paylaşımlı module, sürüm/CVE yönetimi) işler ve dokümanları
  **`../zeus-fw`**'da yaşar; **uygulamaya özel** işler ve dokümanlar **bu projede** (`gelistirmeler/`). Bu projeyi
  "zeus-fw'ı tüketen bir uygulama" gibi düşün; ortak/altyapısal bir şey gerekiyorsa zeus-fw tarafına bak.

## Proje Özeti

Spring Boot 4.0.7 (Java 25) tabanlı, **WAR** olarak paketlenip **WildFly 41**'e deploy edilen bir REST uygulaması. Veritabanı **Oracle** (Docker), bağlantı **WildFly JNDI datasource** (`OracleDS`) üzerinden yapılır.

**Parent: Zeus Framework.** Proje, üst dizindeki `../zeus-fw` (Zeus Framework) üzerine kuruludur; pom parent'ı `com.zeus:zeus-parent` (zinciri `zeus-fw → spring-boot-starter-parent:4.0.7`). Ortak altyapı framework modüllerinden gelir: `zeus-base` (hata yönetimi: `GlobalExceptionHandler` + `ResourceNotFoundException`), `zeus-logger` (`RequestLoggingFilter`), `zeus-database` (`StoredProcedureExecutor` / `JpaStoredProcedureExecutor`), `zeus-service` (`AbstractCrudService` + `DtoMapper`). Spring/Spring Boot ve `springdoc` sürümleri zeus BOM'dan yönetilir (pom'da yazılmaz); Oracle sürücüsü `zeus-database` üzerinden geçişli gelir, pom'da hiç bildirilmez. Detay: `gelistirmeler/13-zeus-framework-entegrasyonu.md`.

## Mimari — Katmanlı (Controller → Service → Repository)

```
HTTP → Controller → Service (interface + impl) → Repository (interface + impl) → Oracle (stored procedure)
                         ↕                              ↕
                    DTO (request/response)         Model (Product)
```

- **controller/** — Sadece HTTP. İstek/yanıt yönetir, iş mantığını servise devreder. `@RestController`.
- **service/** — İş kuralları. `ProductService` (interface) + `ProductServiceImpl` (framework `AbstractCrudService`'i extend eder; transaction + not-found→404 oradan). `ProductMapper` (`DtoMapper`) DTO ↔ model dönüşümünü yapar.
- **repository/** — Veri erişimi. `ProductRepository` (interface). **JpaRepository KULLANILMAZ.** Impl'ler framework'ün `zeus-database` helper'larını (`StoredProcedureExecutor` / `JpaStoredProcedureExecutor`) kullanır.
- **dto/** — `ProductRequest` (giriş, `@Valid`), `ProductResponse` (çıkış). Model dışarı sızdırılmaz.
- **model/** — `Product` domain nesnesi (`@Entity`, ama Spring Data repository yok).
- **İzlenebilirlik** — Her isteğe `X-Correlation-Id` atanır (varsa gelen header korunur, yoksa üretilir) ve isteğin tüm log satırlarına `[correlationId]` olarak basılır; giden HTTP/SOAP çağrılarına ve Oracle oturumuna (`v$session.client_identifier`) taşınır. Uygulama kodu taşıma yapmaz. Ana sınıf bu yüzden `ZeusServletInitializer`'ı genişletir. Detay: `../zeus-fw/gelistirmeler/18-correlation-id.md`.
- **Hata yönetimi** — `ResourceNotFoundException` + `GlobalExceptionHandler` (`@RestControllerAdvice`, `ProblemDetail`) artık **`zeus-base`'ten** gelir (uygulamada `exception/` paketi yoktur; bkz. `gelistirmeler/13-zeus-framework-entegrasyonu.md`).
- **config/** — `OpenApiConfig` (OpenAPI 3 metadata).

### AI Katmanı — zeus-ai / Spring AI 2.x

`/api/ai/**` uçları LLM destekli çalışır ve framework'ün `ZeusAiAssistant` sözleşmesine bağlanır
(uygulama kodu Spring AI API'sini import etmez; tek istisna `@Tool` anotasyonudur):
- `GET /api/ai/products/{id}/description` — sohbet · `GET .../insight` — yapılandırılmış çıktı
  (`ProductInsight` record) · `POST /api/ai/catalog/ask` — **tool calling**.
- `ai/ProductAiTools` `@Tool` metotları **mevcut `ProductRepository`'yi** çağırır → veri yine
  Oracle `PRODUCT_PKG` stored procedure'lerinden gelir. Model DB'ye doğrudan erişmez, SQL üretmez.
- Sağlayıcı OpenAI-uyumlu endpoint'tir; hedef `AI_BASE_URL` ile değişir (PROD: vLLM,
  geliştirme: OpenRouter). **Anahtar repoda tutulmaz**, `AI_API_KEY` ortam değişkeninden gelir.
- Spring AI jar'ları 3. partidir → `com.zeus` module'ünde; `zeus-ai` jar'ı WAR içinde.
  Sürüm/bağımlılık değişince module yenilenir + WildFly restart.
- Detay: `gelistirmeler/18-spring-ai-entegrasyonu.md` · sürüm gerekçesi:
  `gelistirmeler/17-spring-ai-surum-secimi.md` · framework: `../zeus-fw/gelistirmeler/15-zeus-ai.md`.

### API Dokümantasyonu — OpenAPI 3 / Swagger
springdoc-openapi v3 ile otomatik. Erişim (context `/spring-wildfly-arch`):
- OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui/index.html`
- springdoc jar'ları da `com.zeus` module'ünde (bkz. `gelistirmeler/09-openapi-swagger.md`).

### Veri Erişimi — Stored Procedure (ÖNEMLİ)

Tüm sorgular Oracle `PRODUCT_PKG` paketindeki **stored procedure'ler çağrılarak** yapılır. `JpaRepository` / otomatik CRUD **kullanılmaz**.

İki implementasyon vardır, `app.repository.impl` property'si ile seçilir (aynı anda biri aktif):

| Değer | Sınıf | Teknoloji |
|-------|-------|-----------|
| `jdbc` (varsayılan) | `JdbcProductRepository` | `zeus-database` `StoredProcedureExecutor` (Spring JDBC `SimpleJdbcCall`, önbellekli) |
| `jpa` | `JpaProductRepository` | `zeus-database` `JpaStoredProcedureExecutor` (JPA `createStoredProcedureQuery`) |

Stored procedure'ler: `src/main/resources/db/procedures.sql` (`PRODUCT_PKG`: get_all, get_by_id, create_product, update_product, delete_product — REF CURSOR + OUT parametreler).

Yeni bir veri erişim metodu eklerken: önce PL/SQL procedure'ü `procedures.sql`'e ekle, Oracle'a uygula, sonra `ProductRepository` arayüzüne metot + her iki impl'e çağrı ekle.

## İnce WAR + com.zeus Module (ÖNEMLİ mimari karar)

Paketleme **iki gruba** ayrılır (bkz. `gelistirmeler/13-zeus-framework-entegrasyonu.md`):
- **zeus-* (framework) jar'ları → WAR içine** (`WEB-INF/lib`). `zeus-parent`'taki
  `maven-war-plugin`: `<packagingExcludes>%regex[WEB-INF/lib/(?!zeus-).*\.jar]</packagingExcludes>`.
- **Tüm 3. parti kütüphaneler** (Spring, Spring Boot, Hibernate, Jackson, ...) → WAR'a paketlenmez;
  WildFly `com.zeus` **module**'ünden gelir.
- WAR ≈ **40 KB** (yalnızca 4 zeus jar + uygulama sınıfları). 3. parti `com.zeus` module'ünde (sunucuda bir kez).
- Neden 3. partinin hepsi module'de: WildFly module classloader'ı WAR sınıflarını göremez; Spring runtime'da
  neye dokunuyorsa o da module'de olmalı (yoksa NoClassDef/JSON kırılır). "Sadece Spring" çalışmaz.
- Module **paylaşımlı ve platform'a aittir**: onu üreten script `../zeus-fw/scripts/install-zeus-module.sh`'dir
  (bu projede DEĞİL). Script, tek bir uygulamanın değil, tüm uygulamaların 3. parti runtime bağımlılıklarının
  birleşimini tanımlayan `../zeus-fw/zeus-wildfly-module` aggregator modülüne karşı çözer; jar toplarken
  **zeus-* jar'larını dışlar** (WAR'da oldukları için; çift sınıf/LinkageError önlenir).
  Detay: `gelistirmeler/08-com-zeus-module.md` + `../zeus-fw/gelistirmeler/08-wildfly-module-dagitim.md`.
- module.xml her değiştiğinde **WildFly restart** şart (module tanımı cache'lenir). Yalnızca zeus/uygulama
  kodu değişince module değişmez → WAR yeniden deploy yeter, restart gerekmez.

## Build & Deploy

```bash
# Module zaten kuruluysa (genel akış):
./scripts/deploy.sh            # mvn clean package → ince WAR → WildFly'a deploy + marker bekle
./scripts/deploy.sh --no-build # mevcut target/*.war'ı deploy et

# 3. parti kütüphaneler değişince (sürüm/CVE yaması) paylaşımlı module'ü yenile (PLATFORM scripti):
( cd ../zeus-fw && ./scripts/install-zeus-module.sh )   # sonra WildFly'ı yeniden başlat
# Hedef sunucu WILDFLY_HOME ile seçilir: WILDFLY_HOME=/path/staging-wildfly ../zeus-fw/scripts/install-zeus-module.sh
```

- WAR `target/spring-wildfly-arch-0.0.1-SNAPSHOT.war` olarak üretilir, deploy'da `spring-wildfly-arch.war` adıyla kopyalanır.
- **WildFly:** `/Users/omer/workspaces/intellij/wildfly-41/wildfly-41.0.0.Final` (env: `WILDFLY_HOME`). Başlat: `$WILDFLY_HOME/bin/standalone.sh`.
- Uygulama: `http://localhost:8080/spring-wildfly-arch/` — API: `/api/products`.
- `WEB-INF/jboss-deployment-structure.xml` **app repo'sunda TUTULMAZ — framework build'de üretir**
  (zeus-fw `zeus-war-defaults` şablonu + zeus-parent profili; slot `zeus.module.slot`'tan dolar).
  İçerik: com.zeus module bağımlılığı (slot'lu) + logging/weld/batch-jberet/jsf/jaxrs subsystem
  dışlamaları + org.slf4j dışlama. İçeriği değiştirmek gerekirse şablon zeus-fw'da güncellenir;
  detay: `gelistirmeler/08-com-zeus-module.md` + `../zeus-fw/gelistirmeler/10-versiyonlu-slot-uretilen-descriptor.md`.

## Profiller & Lokal Çalıştırma

İki profil (DataSource ayrımı). Detay: `gelistirmeler/10-profiller-lokal-calistirma.md`.
- **`wildfly`** (varsayılan): JNDI datasource + JTA → WAR olarak WildFly'a deploy.
- **`local`**: doğrudan JDBC (ABC/ABC@FREEPDB1) → lokal embedded Tomcat, sabit port **2525** + context path **/spring-wildfly-arch** (WildFly ile çakışmaz, URL'ler aynı). Oracle sürücüsü **bu pom'da bildirilmez** — `zeus-database` onu compile scope'ta getirir; WAR'a ve `com.zeus` module'üne girmez (bkz. `../zeus-fw/gelistirmeler/08-wildfly-module-dagitim.md`).

Lokal embedded çalıştırma:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local   # http://localhost:2525/spring-wildfly-arch
```
IntelliJ: Spring Boot run config → Active profiles `local` (Oracle container açık olmalı).
> `spring-boot-maven-plugin` skip'i yalnızca `repackage` execution'ında — `spring-boot:run` çalışır.

## Ortam

- **Oracle (Docker):** container `fw-batch-oracle`, kullanıcı/şifre `ABC`/`ABC`, PDB `FREEPDB1`, port 1521. URL: `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`.
  - Şema uygula: `docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/schema.sql` (önce schema, sonra procedures).
- **WildFly datasource:** JNDI `java:jboss/datasources/OracleDS`, Oracle JDBC module `com.oracle.ojdbc` (ojdbc17). Tanım: WildFly `standalone.xml`.
- **WildFly yönetim konsolu:** http://localhost:9990/console/index.html — kullanıcı `admin` / şifre `Wildfly@123` (ManagementRealm). Hesap `add-user.sh` ile oluşturuldu; değiştirmek için `$WILDFLY_HOME/bin/add-user.sh -u admin -p 'YeniSifre'`.
- **Test:** `@SpringBootTest` context-load testi gömülü **H2** ile çalışır (`src/test/resources/application.properties`); WildFly/JNDI gerekmez.

## Konvansiyonlar

- Kod yorumları **Türkçe**, çevredeki stille tutarlı.
- Controller ↔ Service ↔ Repository yalnızca **interface** üzerinden bağlanır; bağımlılık constructor injection (`@RequiredArgsConstructor`).
- Dışarıya `Product` değil **DTO** döndür. Hatalar `GlobalExceptionHandler` üzerinden `ProblemDetail` olarak döner.
- Lombok kullanılır (`@Data`, `@Builder`, ...).

## Dokümantasyon Kuralı

Bu projede yapılan her bileşen/geliştirme `gelistirmeler/` klasöründe **component bazlı, numaralı** md dosyalarına yazılır: `NN-bilesen-adi.md` (ör. `01-database-oracle.md`). Yeni bir bileşen eklenince sıradaki numarayla yeni dosya açılır.

## Kısıtlar (ÖNEMLİ)

- Doküman / `.md` dosyaları **yalnızca proje dizinine** yazılır. Proje dizini dışına md yazılmaz.
- Proje dizini dışında şu iki dizine yazılabilir; başka dizinlere yazma yapılmaz:
  - **WildFly dizini** (`/Users/omer/workspaces/intellij/wildfly-41/wildfly-41.0.0.Final`) — deploy/module.
  - **Zeus Framework dizini** (`../zeus-fw`, `/Users/omer/workspaces/intellij/spring-wildfly-upgrade/zeus-fw`)
    — yalnızca **platform** değişiklikleri için (paylaşımlı module üretimi, BOM/parent, aggregator modül).
    Uygulamaya özel kod ve `.md` dokümanlar yine yalnızca proje dizinine yazılır.
