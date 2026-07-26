# 09 — OpenAPI 3 + Swagger UI

## Amaç
REST API için otomatik OpenAPI 3 dokümanı ve interaktif Swagger UI.

## Kütüphane
Spring Boot 3.1 için **springdoc-openapi v2** (`pom.xml`):
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```
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
