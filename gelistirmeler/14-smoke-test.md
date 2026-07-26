# 14 — Smoke Test (scripts/smoke-test.sh)

## Amaç

Deploy'un başarılı olması ("context açıldı") davranışın doğru olduğu anlamına gelmez. Bu script,
deploy edilmiş uygulamayı **gerçek HTTP üzerinden** kısa bir CRUD senaryosuyla uçtan uca doğrular ve
şu zincirin tamamını tek koşuda çalıştırır:

**Spring MVC + Jackson + springdoc + Bean Validation + transaction + stored procedure (JNDI datasource) + `GlobalExceptionHandler`.**

Staging doğrulama geçidinin **uygulama ayağıdır** (platform ayağı: `../zeus-fw/scripts/verify-staging.sh`,
bkz. bu projede [`verify-staging` başlığı aşağıda](#staging-gate-ile-ilişki) ve zeus-fw doküman 09).

## Konum

`scripts/smoke-test.sh` (proje kökü). WAR'a paketlenmez.

## Kullanım

```bash
./scripts/smoke-test.sh                                            # lokal WildFly (8080)
./scripts/smoke-test.sh http://localhost:8180/spring-wildfly-arch  # staging (port offset'li)
SERVER_LOG=/path/server.log ./scripts/smoke-test.sh <url>          # log taraması dahil
```

- **Argüman:** base URL (varsayılan `http://localhost:8080/spring-wildfly-arch`). Sondaki `/` kırpılır;
  API yolu `<base>/api/products` olarak türetilir.
- **`SERVER_LOG` (opsiyonel):** verilirse test **başlamadan** log satır sayısı alınır, test **sonrası**
  yalnız yeni düşen satırlar `ERROR` / `NoClassDefFound|NoSuchMethod|NoSuchField|AbstractMethod|Linkage`Error
  için taranır. Bu, istek atılmayan yollardaki **sessiz linkage sorunlarını** yakalar (module class kayması gibi).

## Ne test eder (adımlar)

| # | İstek | Beklenen | Doğrulanan zincir |
|---|-------|----------|-------------------|
| 1 | `GET /v3/api-docs` | 200, gövdede `"openapi"` | Spring MVC + Jackson + springdoc |
| 2 | `GET /api/products` | 200 | repository → stored procedure → REF CURSOR |
| 3 | `POST /api/products` | 201, gövdede `"id"` | validation + transaction + `create_product` |
| 4 | `GET /api/products/{id}` | 200, ismi içerir | `get_by_id` (OUT param) |
| 5 | `PUT /api/products/{id}` | 200, güncel ismi içerir | `update_product` |
| 6 | `POST` geçersiz gövde (`name:""`, `price:-1`) | 400 | Bean Validation → `GlobalExceptionHandler` |
| 7 | `DELETE /api/products/{id}` | 204 | `delete_product` |
| 8 | `GET` silinen kayıt | 404, `"status":404` | `ResourceNotFoundException` → `ProblemDetail` |
| 9 | `SERVER_LOG` taraması (verildiyse) | yeni ERROR/LinkageError yok | tüm runtime (sessiz hatalar) |

Test kaydı benzersiz isimle oluşturulur (`smoke-<epoch>-<pid>`) ve senaryonun sonunda **silinir** →
tekrar tekrar koşulabilir, kalıntı bırakmaz. Create yanıtından `id` çıkarılamazsa kalan CRUD adımları
anlamsız olduğundan erken çıkılır.

## Çıkış kodu

- `0` — tüm adımlar geçti (`✅ SMOKE TEST GEÇTİ: <url>`).
- `1` — en az bir adım başarısız (`❌`). Her başarısız adımda beklenen/gelen HTTP kodu ve gövdenin
  ilk 300 baytı basılır.

## Staging gate ile ilişki

`../zeus-fw/scripts/verify-staging.sh` her uygulamayı deploy ettikten **sonra** o uygulamanın
`scripts/smoke-test.sh`'ını `SERVER_LOG` set edilmiş şekilde çağırır. Smoke `0` dönmezse o uygulama
gate raporunda **❌** olur ve module **prod'a promote edilmez**. Bu yüzden smoke-test.sh'ın:
- base URL'i **argümandan** okuması (staging port offset'i için) ve
- `SERVER_LOG` env'ini desteklemesi

verify-staging.sh ile sözleşmenin parçasıdır; imzası değişirse gate tarafı da güncellenmeli.

## İlgili

- Deploy: `07-deploy-script.md`
- Katmanlı mimari / CRUD akışı: `04-katmanli-mimari.md`
- Stored procedure veri erişimi: `05-stored-procedure-veri-erisimi.md`
- Hata yönetimi (ProblemDetail): `13-zeus-framework-entegrasyonu.md`
- Platform staging gate: `../zeus-fw/scripts/verify-staging.sh` + zeus-fw `gelistirmeler/09-cve-guvenlik-yamalama.md`