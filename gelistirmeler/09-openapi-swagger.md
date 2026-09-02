# 09 — OpenAPI 3 + Swagger UI

## Amaç
REST API için otomatik OpenAPI 3 dokümanı ve interaktif Swagger UI.

## Kütüphane
Spring Boot 4 için **springdoc-openapi v3** (`pom.xml`) — **sürüm yazılmaz**, zeus BOM yönetir
(`springdoc-openapi.version`, şu an `3.1.0`):
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

> springdoc **2.x Boot 4 ile ÇALIŞMAZ** (Framework 7 / Jakarta EE 11 uyumsuzluğu);
> 3.x hattı zorunludur. Bkz. `16-java25-boot4-wildfly41-yukseltme.md`.
Controller'ları otomatik tarar; ek anotasyon gerekmez. Metadata için bir bean:
`config/OpenApiConfig.java` → `OpenAPI` bean (title, description, version).

## Erişim (context: /spring-wildfly-arch)
| Ne | URL |
|----|-----|
| OpenAPI JSON | http://localhost:8080/spring-wildfly-arch/v3/api-docs |
| Swagger UI | http://localhost:8080/spring-wildfly-arch/swagger-ui/index.html |

## İnce WAR + module ile çalışma (ÖNEMLİ)
Tüm 3. parti jar'lar `com.zeus` module'ünde olduğundan, springdoc + swagger jar'ları da
module'e girmeli. Bağımlılık `../zeus-fw/zeus-wildfly-module`'e eklendikten sonra:
```bash
( cd ../zeus-fw && ./scripts/install-zeus-module.sh )   # springdoc-* + swagger-* + swagger-ui webjar module'e eklenir
# WildFly RESTART (module değişti)
./scripts/deploy.sh
```
- Swagger UI statik dosyaları `swagger-ui-5.2.0.jar` (webjar) içinden gelir; module resource'ları
  deployment'a görünür olduğundan (`meta-inf="import"`) UI sorunsuz serve edilir.
- Yeni jar'lar (springdoc-openapi-starter-common/-webmvc-api/-webmvc-ui, swagger-core/-models/-annotations-jakarta,
  swagger-ui, jackson-dataformat-yaml, commons-lang3) module'e otomatik kopyalanır.

## İlgili
- Module mekanizması: `08-com-zeus-module.md`
- Endpoint'ler: `04-katmanli-mimari.md`
