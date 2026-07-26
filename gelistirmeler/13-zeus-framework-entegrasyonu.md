# 13 — Zeus Framework Entegrasyonu

## Amaç

`spring-wildfly-arch`'ı bağımsız bir Spring Boot projesinden, üst dizindeki **Zeus Framework** (`../zeus-fw`) üzerine kurulu bir uygulamaya dönüştürmek. Artık parent `spring-boot-starter-parent` değil **`zeus-parent`**; ortak altyapı (hata yönetimi, istek loglama) framework modüllerinden gelir.

## Parent Değişikliği

`pom.xml` parent'ı:

```xml
<parent>
    <groupId>com.zeus</groupId>
    <artifactId>zeus-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath/>
</parent>
```

`zeus-parent` zinciri `zeus-fw → spring-boot-starter-parent:3.1.3` olduğundan Spring Boot/Spring (6.0.11) yönetimi ve plugin yapılandırması yine miras alınır. Kazanımlar:
- `java.version`, `spring-framework.version` artık pom'da **yok** (framework'ten gelir).
- `springdoc`, `ojdbc11` **sürümleri yazılmaz** (zeus BOM yönetir).
- `maven-war-plugin` / `spring-boot-maven-plugin` / `maven-compiler-plugin` konfigürasyonları `zeus-parent` pluginManagement'tan gelir; pom'da yalnızca plugin'ler **devreye alınır** (config yok).

## Kullanılan Zeus Modülleri

`zeus-base`, `zeus-logger`, `zeus-database`, `zeus-service` (sürümsüz; zeus BOM yönetir). Bunlar `@AutoConfiguration` ile kendiliğinden devreye girer.

- **zeus-base** → `GlobalExceptionHandler` + `ResourceNotFoundException`. Uygulamadaki `exception/` paketi **silindi**; `ProductServiceImpl` artık `com.zeus.framework.base.ResourceNotFoundException` kullanır. Handler, auto-config tarafından bean olarak kaydedilir (bileşen taramasına gerek yok).
- **zeus-logger** → `RequestLoggingFilter` her isteği otomatik loglar.
- **zeus-database** → `StoredProcedureExecutor` (+`JdbcStoredProcedureExecutor` önbellekli) ve `JpaStoredProcedureExecutor`. `JdbcProductRepository` ve `JpaProductRepository` artık PRODUCT_PKG procedure'lerini bu helper'larla çağırır (elle `SimpleJdbcCall`/`StoredProcedureQuery` kurulumu yok).
- **zeus-service** → `AbstractCrudService` (transaction + DTO dönüşümü + not-found→404) ve `DtoMapper`. `ProductServiceImpl` bu tabanı extend eder; `ProductMapper` `DtoMapper`'ı implement eder.

## Paketleme Politikası (ÖNEMLİ — değişti)

Önceki kural "tüm jar'lar WAR dışı" idi. Yeni kural:

| Grup | Yer |
|------|-----|
| **zeus-* jar'ları** | WAR içinde (`WEB-INF/lib`) |
| **3. parti jar'lar** (Spring, Hibernate, ...) | WildFly `com.zeus` module |

- `zeus-parent`'taki `maven-war-plugin`: `<packagingExcludes>%regex[WEB-INF/lib/(?!zeus-).*\.jar]</packagingExcludes>` → zeus-* hariç tüm jar'ları WAR'dan dışlar. Sonuç WAR ≈ **40 KB** (4 zeus jar + sınıflar).
- `../zeus-fw/scripts/install-zeus-module.sh` (platform scripti): `EXCLUDE_REGEX`'e `zeus-(base|logger|database|service|redis|batch)` eklendi → zeus-* jar'ları module'e **konmaz** (WAR'da oldukları için; çift sınıf/LinkageError önlenir).

Bu sayede yalnızca zeus kodu değişince WAR yeniden deploy yeter; module (3. parti) değişmediği için WildFly restart gerekmez.

## Build & Deploy Sırası

```bash
# 1) Framework'ü kur (değiştiyse):
cd ../zeus-fw && mvn clean install

# 2) Paylaşımlı 3. parti module'ü yenile (yalnızca 3. parti bağımlılık değişince) + WildFly restart:
cd ../zeus-fw && ./scripts/install-zeus-module.sh

# 3) Uygulamayı derle/deploy et (zeus kodu/uygulama değişince bu yeter):
./scripts/deploy.sh
```

## Doğrulama

- `mvn clean package` → WAR `WEB-INF/lib` yalnızca `zeus-*.jar` içerir (3. parti yok).
- `mvn test` → context yüklenir; loglarda "Zeus Base/Logger/Database/Service modülü yüklendi" görünür.
- Lokal: `mvn spring-boot:run -Dspring.profiles.active=local` → her istekte `RequestLoggingFilter` log satırı.

## İlgili

- `../zeus-fw/gelistirmeler/00-Genel-Mimari.md` · `01-zeus-base.md` · `02-zeus-logger.md`
- `08-com-zeus-module.md` (3. parti module) · `07-deploy-script.md`
