# 19 — MCP sunucusu (uygulamanın `@Tool` metotlarını harici ajanlara açmak)

Bu doküman, `zeus-ai-mcp`'yi tüketen bir uygulamanın **referans örneğidir**. Uygulamada değişen
tek şey **bir config sınıfı ve beş property**; `ProductAiTools`, `ProductRepository` ve
`PRODUCT_PKG` yoluna **dokunulmadı**.

Framework tarafı, tasarım gerekçeleri ve bilinen boşluklar: `../zeus-fw/gelistirmeler/23-zeus-ai-mcp.md`.

## Ne değişti

| Dosya | Değişiklik |
|---|---|
| `pom.xml` | `com.zeus:zeus-ai-mcp` bağımlılığı (sürümsüz, BOM'dan) |
| `config/McpConfig.java` | **YENİ** — yayın yüzeyi: tek `ZeusMcpTools` bean'i |
| `src/main/resources/application.properties` | MCP bloğu (yetenek + sır + taşıma) |
| `src/test/resources/application.properties` | Aynı satırlar — **bu dosya main'i GÖLGELER** |
| `src/test/.../config/McpConfigTest.java` | **YENİ** — yayın yüzeyini sabitleyen test |

`ai/ProductAiTools.java` ve altındaki hiçbir katman **değişmedi**.

## Yayın yüzeyi — tek dosya, tek satır

```java
@Configuration
public class McpConfig {
    @Bean
    ZeusMcpTools zeusMcpTools(ProductAiTools productAiTools) {
        return ZeusMcpTools.of(productAiTools);
    }
}
```

Classpath taraması yoktur: bir `@Tool` metodu yazmak onu dışarı açmaz. "Bu uygulama dışarı ne
açıyor?" sorusunun cevabı bu dosyadır ve code review orada yapılır.

**Veri yolu değişmedi** — mimari sürekliliğin kanıtı:

```
Harici ajan ──JSON-RPC / streamable HTTP──> /api/mcp
                                               │ ZeusMcpTokenFilter (paylaşılan sır)
                                               │ AuditingToolCallback (audit + correlation-id)
                                               ▼
                                     ProductAiTools.findProductById()   ← DEĞİŞMEDİ
                                               ▼
                                     ProductRepository                  ← DEĞİŞMEDİ
                                               ▼
                                     Oracle PRODUCT_PKG.get_by_id       ← DEĞİŞMEDİ
```

Model veritabanına doğrudan erişmez, SQL üretmez, DB kimliği görmez; yalnızca `McpConfig`'te
sayılan nesnelerin `@Tool` metotlarını çağırabilir. `app.repository.impl` (jdbc/jpa) seçimi aynen
geçerlidir.

## Yapılandırma

```properties
# --- MCP sunucusu (zeus-ai-mcp) ---
zeus.ai.mcp.enabled=true
# Paylaşılan sır ASLA repoya yazılmaz. BOŞSA AÇILIŞ REDDEDİLİR (fail-closed).
zeus.ai.mcp.token=${ZEUS_MCP_TOKEN:}
# Taşıma: streamable HTTP. protocol AÇIKÇA yazılmalıdır — yazılmazsa Spring AI kullanım dışı
# bırakılmış SSE taşımasını yayınlar ve onun ucu ayrı bir property'den geldiği için zeus token
# filtresi onu KORUMAZ (ölçüldü; bkz. ../zeus-fw/gelistirmeler/23-zeus-ai-mcp.md).
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.streamable-http.mcp-endpoint=/api/mcp
spring.ai.mcp.server.name=spring-wildfly-arch
spring.ai.mcp.server.version=0.0.1
```

> **TUZAK — test property'leri gölgeler.** `src/test/resources/application.properties`,
> `src/main` altındaki aynı adlı dosyayı test classpath'inde **tamamen gölgeler**. `zeus-ai-mcp`
> fail-closed olduğu için bu satırlar oraya da yazılmazsa `@SpringBootTest` açılışta düşer
> (sır/taşıma bildirimi yok). Testte sahte bir sır verilir:
> ```properties
> zeus.ai.mcp.enabled=true
> zeus.ai.mcp.token=test-mcp-token
> spring.ai.mcp.server.protocol=STREAMABLE
> spring.ai.mcp.server.streamable-http.mcp-endpoint=/api/mcp
> ```

**Sır yönetimi (operasyon).** `ZEUS_MCP_TOKEN` WildFly sürecinin ortamında tanımlı olmalıdır.
Repoda yalnız `${ZEUS_MCP_TOKEN:}` yer tutucusu bulunur ve bunu platform guard'ı
(`test-mcp-opt-in-butunlugu.sh`) denetler. **Rotasyon = değişkeni değiştir + WildFly'ı yeniden
başlat**; sır bir kimlik değil taşıyıcı yetkidir, iptal etmenin başka yolu yoktur.

## Uçtan uca doğrulama (ölçülmüş çıktılarla)

```bash
export ZEUS_MCP_TOKEN="$(openssl rand -hex 16)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home

# 1) framework + paylaşımlı module (3. parti değişti → zorunlu)
cd ../zeus-fw && mvn clean install && ./scripts/install-zeus-module.sh
# → WildFly RESTART (main slot module tanımı cache'lidir)

# 2) deploy
cd ../spring-wildfly-arch && ./scripts/deploy.sh
```

> **`localhost` DEĞİL `127.0.0.1`.** WildFly yalnız IPv4 loopback'i dinler; bu makinede
> `localhost` önce `::1`'e çözülür ve 8080'i tutan bir Docker container'ı varsa istek **ona**
> gider (ölçüm sırasında gerçekten yaşandı; `../zeus-fw/gelistirmeler/17-module-yenileme-runbook.md`
> bu tuzağı zaten kaydediyor).

### Açılış kanıtı

```
ZeusMcpAutoConfiguration : Zeus AI MCP modülü yüklendi — uç: /api/mcp , paylaşılan sır: 'X-Zeus-Mcp-Token' header'ından.
ZeusMcpAutoConfiguration : Zeus AI MCP: 2 tool yayınlanıyor — [listProducts, findProductById]
McpServerAutoConfiguration : Registered tools: 2
WFLYUT0021: Registered web context: '/spring-wildfly-arch'
```

### Erişim denetimi

```bash
BASE=http://127.0.0.1:8080/spring-wildfly-arch/api/mcp
H='Accept: application/json, text/event-stream'

curl -i -s -X POST "$BASE" -H 'Content-Type: application/json' -H "$H" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | head -3
```
```
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json;charset=UTF-8
{"type":"about:blank","title":"MCP erişimi reddedildi","status":401,"detail":"Geçerli bir erişim anahtarı gerekli."}
```

**Yanlış token birebir aynı gövdeyi alır** — istemci "header mı eksik, değer mi yanlış" sorusunu
ayırt edemez; ayrım yalnız sunucu logunda (`anahtar yok` / `anahtar hatalı`).
`POST /spring-wildfly-arch/sse` → **404**: kullanım dışı SSE taşıması yayınlanmıyor.

### Protokol akışı

```bash
TOKEN=$ZEUS_MCP_TOKEN
# initialize → oturum
SID=$(curl -s -D /tmp/h -o /dev/null -X POST "$BASE" -H 'Content-Type: application/json' -H "$H" \
  -H "X-Zeus-Mcp-Token: $TOKEN" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' \
  && grep -i '^mcp-session-id:' /tmp/h | awk '{print $2}' | tr -d '\r')

curl -s -o /dev/null -X POST "$BASE" -H 'Content-Type: application/json' -H "$H" \
  -H "X-Zeus-Mcp-Token: $TOKEN" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl -s -X POST "$BASE" -H 'Content-Type: application/json' -H "$H" \
  -H "X-Zeus-Mcp-Token: $TOKEN" -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

`tools/list` (yanıt streamable HTTP'de SSE çerçeveli gelir, `data:` satırını ayrıştırın):

```json
{"name":"findProductById",
 "description":"Verilen id'ye sahip ürünü döner; ürün yoksa null döner.",
 "inputSchema":{"type":"object",
   "properties":{"id":{"type":"integer","description":"Ürünün sayısal id'si"}},
   "required":["id"]}}
{"name":"listProducts",
 "description":"Katalogdaki tüm ürünleri id, ad, fiyat ve stok bilgisiyle listeler."}
```

Açıklamalar `ProductAiTools`'un `@Tool`/`@ToolParam` metinleridir — **yeni anotasyon yazılmadı.**
Dolayısıyla `@Tool(description=...)` artık dışarıya yayınlanan bir API dokümanıdır; onu
değiştirmek sözleşmeyi değiştirmektir.

`tools/call`:

```bash
curl -s -X POST "$BASE" -H 'Content-Type: application/json' -H "$H" \
  -H "X-Zeus-Mcp-Token: $TOKEN" -H "Mcp-Session-Id: $SID" -H "X-Correlation-Id: kanit1" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"findProductById","arguments":{"id":1}}}'
```
```json
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text",
 "text":"{\"id\":1,\"name\":\"Laptop\",\"price\":25000,\"stock\":10}"}],"isError":false}}
```

### İzlenebilirlik kanıtı

Tek `X-Correlation-Id` altında üç satır — verinin gerçekten `PRODUCT_PKG`'den geldiğinin kanıtı:

```
[default task-3] [kanit1] ProductAiTools       : AI tool çağrıldı: findProductById(1)
[default task-3] [kanit1] ZeusMcpAuditor       : MCP tool çağrıldı — araç=findProductById, süre=318 ms, sonuç=BASARILI, thread=default task-3
[default task-3] [kanit1] RequestLoggingFilter : POST /spring-wildfly-arch/api/mcp -> 200 (330 ms)
```

`thread=default task-3` bir Undertow **istek worker'ıdır**: tool gövdesi istek thread'inde koşuyor,
dolayısıyla MDC'deki correlation-id (ve ileride kimlik) hayatta kalıyor. Bu, Spring AI'ın
`immediateExecution(true)` davranışına dayanan bir varsayımdır ve audit satırındaki `thread`
alanıyla üretimde sürekli denetlenir.

### Gerçek ajanla test

Herhangi bir MCP yetenekli istemciyi uca bağlayın, ör.:

```bash
claude mcp add --transport http zeus-arch \
  http://127.0.0.1:8080/spring-wildfly-arch/api/mcp \
  --header "X-Zeus-Mcp-Token: $ZEUS_MCP_TOKEN"
```
ardından "katalogdaki 1 numaralı ürün nedir?" sorun. curl dizisi bunun otomatikleştirilebilen
altkümesidir ve platform guard'ı `test-mcp-uctan-uca.sh` olarak koşar.

## Ölçümler

| | |
|---|---|
| WAR | **109 KB** — 7 zeus jar'ı (`zeus-ai-mcp` dâhil); MCP SDK ve `spring-ai-*` WAR'a **girmiyor** |
| `com.zeus` module | 180 → **189 jar** (+9, hepsi MCP) |
| `tools/call` gecikmesi | ~318 ms tool + ~330 ms toplam istek (Oracle `get_by_id`, sıcak bağlantı) |

MCP'de LLM gecikmesi **yoktur**: model çağrısını ajan kendi tarafında yapar, bu uygulama yalnız
tool'u koşturur. `/api/ai/catalog/ask` ucundaki ~35 sn'lik tool-calling süresiyle karıştırılmamalı
(bkz. `18-spring-ai-entegrasyonu.md`) — orada modeli uygulama çağırır.

## Yeni tool eklerken

1. Tool metodunu `ai/ProductAiTools`'a ekle; veriye **`ProductRepository` üzerinden** eriş — yeni
   veri yolu açma.
2. `@Tool(description=...)` ve `@ToolParam(description=...)` metinlerini **arayüz sözleşmesi gibi**
   yaz: model tool'u bu metinle seçer.
3. Dışarıya açılacaksa `config/McpConfig`'teki listeye gir — aksi hâlde yalnız süreç içi kalır.
4. `McpConfigTest`'teki beklenen tool listesini güncelle (yoksa test kırmızıya döner — kasıtlı).
5. **Yazma yapan tool** ise: yetki kontrolü metodun **içinde** olmalıdır. Bugünkü koruma yalnız
   paylaşılan sırdır (kimlik değil); tool bazlı yetki `zeus-security` ile gelecek. v1'de yayınlanan
   yüzey bilinçli olarak **salt okunurdur**.

## Bilinen boşluklar

- Metot seviyesinde yetkilendirme (`@PreAuthorize`) ve son kullanıcı token'ının taşınması **yok**.
- Paylaşılan sır bir kimlik değil **taşıyıcı yetkidir**: tüm çağıranlar aynı principal, audit
  *kimin* çağırdığını yazmaz, iptal = rotasyon + restart.
- Audit yalnız `tools/call`'u kapsar; `initialize`/`tools/list` yalnız filtre log satırında görünür.
- Protokol hata gövdeleri Spring AI transport'u tarafından `stackTrace` ile serileştirilir (sınıf
  adı, satır). Bizim `401`'imiz temizdir; uç sırla korunduğu için v1'de kabul edilmiştir.
- Roadmap'in "AI ajanından CRUD" kabul kriteri **kısmen** karşılanmıştır (yalnız okuma).

Tamamı ve gerekçeleri: `../zeus-fw/gelistirmeler/23-zeus-ai-mcp.md` → "Bilinen boşluk".
