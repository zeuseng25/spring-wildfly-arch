# 18 — Spring AI Entegrasyonu (zeus-ai kullanımı, uçtan uca örnek)

Uygulama, framework'ün yeni `zeus-ai` modülü üzerinden LLM'e bağlandı. Framework tarafı:
`../../zeus-fw/gelistirmeler/15-zeus-ai.md`. Sürüm gerekçesi (neden 2.x, neden 1.x değil):
`17-spring-ai-surum-secimi.md`.

Bu doküman, zeus-ai'yi tüketen bir uygulamanın **referans örneğidir**.

## Ne eklendi

```
pom.xml                                            + com.zeus:zeus-ai
src/main/resources/application.properties          + spring.ai.openai.* / zeus.ai.*
src/test/resources/application.properties          + sahte anahtar (context yüklensin)
ai/ProductAiTools.java                             @Tool metotları → ProductRepository
dto/ProductInsight.java                            yapılandırılmış çıktı hedefi (record)
dto/AiAnswer.java, dto/CatalogQuestion.java        giriş/çıkış DTO'ları
service/ProductAiService(.Impl).java               iş mantığı
controller/ProductAiController.java                HTTP
```

Mevcut hiçbir sınıf değişmedi — AI katmanı, var olan Controller → Service → Repository
mimarisinin üzerine **aynı kurallarla** eklendi.

## Uçlar

| Metot | Yol | Gösterdiği |
|-------|-----|-----------|
| GET | `/api/ai/products/{id}/description` | Sohbet — ürün verisi prompt'a gömülür, düz metin döner |
| GET | `/api/ai/products/{id}/insight` | Yapılandırılmış çıktı — yanıt doğrudan `ProductInsight` record'una bağlanır |
| POST | `/api/ai/catalog/ask` | Tool calling — veri gömülmez, model tool çağırarak veriyi kendisi çeker |

## Mimari — veri yolu değişmedi (ÖNEMLİ)

```
POST /api/ai/catalog/ask
      │
      ▼
ProductAiController → ProductAiService → ZeusAiAssistant.ask(soru, productAiTools)
                                              │
                                    (model "listProducts" tool'unu çağırmaya karar verir)
                                              ▼
                                        ProductAiTools.listProducts()
                                              ▼
                                        ProductRepository  ← DEĞİŞMEDİ
                                              ▼
                                    Oracle PRODUCT_PKG.get_all (stored procedure)
```

Model veritabanına doğrudan erişmez, SQL üretmez. Yalnızca `@Tool` ile açılmış metotları
çağırabilir; o metotlar da mevcut `ProductRepository` arayüzünü kullanır — yani `jdbc`/`jpa`
implementasyon seçimi (`app.repository.impl`) AI yolunda da aynen geçerlidir. Dışarıya
`Product` değil `ProductResponse` verilir; DTO kuralı model için de bozulmaz.

## Konfigürasyon

```properties
# application.properties
spring.ai.openai.base-url=${AI_BASE_URL:https://openrouter.ai/api/v1}
spring.ai.openai.api-key=${AI_API_KEY:degistir}
spring.ai.openai.chat.model=${AI_MODEL:qwen/qwen3-30b-a3b-instruct-2507}
spring.ai.openai.chat.temperature=0.3
zeus.ai.log-conversation=false
```

Üç değer de ortam değişkeninden gelir. **Anahtar repoda tutulmaz.** Üretimde hedef vLLM'dir:

```bash
AI_BASE_URL=http://vllm.kurum.local:8000/v1 \
AI_MODEL=Qwen/Qwen3-30B-A3B-Instruct-2507 \
AI_API_KEY=<vllm anahtarı>
```

Model bilinçli olarak **açık ağırlıklı** seçildi: geliştirmede OpenRouter üzerinden çağrılan
model, üretimde aynı ağırlıklarla vLLM'de host edilecek.

### Test context'i

`src/test/resources/application.properties`'e sahte anahtar + erişilemez base-url eklendi.
Spring AI auto-config boş anahtarla ayağa kalkmaz; testte gerçek çağrı yapılmaz.
Tümüyle kapatmak isteyen `zeus.ai.enabled=false` verir.

## Çalıştırma ve canlı doğrulama

Doğrulama `local` profiliyle yapıldı (embedded Tomcat, port 2525, doğrudan JDBC → Oracle):

```bash
export AI_API_KEY=<anahtar>
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Veri (Oracle `PRODUCTS`): Laptop 25000/10, Mouse 350/100, Keyboard 750/50, Monitor 5500/20,
Webcam 1200/30.

### 1) Sohbet

```bash
curl http://localhost:2525/spring-wildfly-arch/api/ai/products/1/description
```
```json
{"answer":"İnce ve hafif dizüstü bilgisayar, 16 GB RAM ve 512 GB SSD ile dikkat çekici performans sunar. ..."}
```
> ⚠️ **Gözlem:** model, veride olmayan teknik özellikleri (RAM/SSD/ekran) uydurdu. Prompt'ta
> yalnızca ad/fiyat/stok verilmişti. Üretimde bu uçların promptu "yalnızca verilen alanları
> kullan, özellik uydurma" kısıtıyla sıkılaştırılmalı veya çıktı insan onayından geçmelidir.
> Bu, framework hatası değil prompt tasarımı sorumluluğudur.

### 2) Yapılandırılmış çıktı

```bash
curl http://localhost:2525/spring-wildfly-arch/api/ai/products/1/insight
```
```json
{
  "summary": "25000 TL fiyat etiketli, 10 adet stokta olan dizüstü bilgisayar.",
  "targetAudience": "Profesyonel kullanıcılar, yüksek performans gerektiren işler için",
  "keywords": ["laptop", "bilgisayar", "dizüstü bilgisayar", "25000 tl", "stokta var"],
  "priceAssessment": "Yüksek"
}
```
Uygulamada JSON ayrıştırma kodu yok; şema `ProductInsight` record'undan türetildi.

### 3) Tool calling

```bash
curl -X POST http://localhost:2525/spring-wildfly-arch/api/ai/catalog/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Katalogdaki en pahalı ürün hangisi ve stoğu kaç adet? Toplam kaç ürün var?"}'
```
```json
{"answer":"En pahalı ürün \"Laptop\" olup, stoğu 10 adettir. Katalogda toplam 5 ürün vardır."}
```

Log kanıtı — modelin aracı gerçekten çağırdığı ve verinin veritabanından geldiği:

```
c.z.springwildflyarch.ai.ProductAiTools : AI tool çağrıldı: listProducts -> 5 kayıt
```

Yanıttaki üç bilginin (en pahalı ürün, stok, toplam adet) hiçbiri prompt'ta yoktu.

### 4) Hata yolları

| Senaryo | Sonuç | Handler |
|---------|-------|---------|
| `GET /api/ai/products/999/description` | `404` — `{"detail":"Product bulunamadı: id=999"}` | zeus-base `GlobalExceptionHandler` |
| `POST /api/ai/catalog/ask` body `{"question":"  "}` | `400` — `{"errors":{"question":"Soru boş olamaz"}}` | zeus-base (doğrulama) |
| AI endpoint erişilemez | `502` — `{"title":"AI servisi hatası"}` | zeus-ai `ZeusAiExceptionHandler` |

502 durumunda sağlayıcıdan gelen ham hata istemciye **dönmedi**, yalnızca loga yazıldı.

## Ölçümler (canlı, OpenRouter / Qwen3-30B-A3B)

| İşlem | Süre | Token (giriş/çıkış/toplam) |
|-------|------|---------------------------|
| `ask` (açıklama) | 5.559 ms | 139 / 68 / 207 |
| `askAs` (ProductInsight) | 1.588 ms | 347 / 87 / 434 |
| `ask` + tool calling | 35.715 ms | 534 / 33 / 567 |

Tool calling süresi belirgin şekilde yüksek: model → tool → model olmak üzere **en az iki tur**
yapılır ve tool sonucu bağlama eklenir. Bu, kullanıcıya senkron dönen uçlarda tasarım kısıtıdır
(timeout ve kullanıcı beklentisi buna göre ayarlanmalı). Token sayaçları her çağrıda
`DefaultZeusAiAssistant` tarafından INFO seviyesinde loglanır — maliyet izlenebilir.

## WildFly tarafı (deploy edildi, doğrulandı)

- WAR **66 KB** kaldı: `zeus-ai-2.0.0-SNAPSHOT.jar` WAR içinde (9.7 KB), Spring AI'ın
  3. parti jar'larının hiçbiri WAR'a girmedi.
- Paylaşımlı `com.zeus` module'ü yenilendi (`../zeus-fw/scripts/install-zeus-module.sh`) →
  **157 jar** (spring-ai-*, openai-java-core, reactor-*, spring-ai-template-st, antlr*,
  spring-webflux, netty native transport'lar).
- Module sözleşmesi değiştiği için **WildFly restart** yapıldı.

Deploy, 8080 portu başka bir süreçte olduğu için **port-offset** ile yapıldı:

```bash
export AI_API_KEY=<anahtar>          # WildFly süreci bu değişkeni görmeli
./scripts/deploy.sh --no-build       # kapsam denetimi + WAR'ı deployments'a kopyalar
$WILDFLY_HOME/bin/standalone.sh -Djboss.socket.binding.port-offset=100
# HTTP 8180, yönetim 10090 → http://localhost:8180/spring-wildfly-arch/
```

WildFly üzerinde doğrulanan çıktılar (JNDI `OracleDS` + stored procedure + AI):

| Uç | Sonuç |
|----|-------|
| `GET /api/products` | 5 ürün (JNDI datasource üzerinden `PRODUCT_PKG`) |
| `GET /api/ai/products/2/description` | *"Kablosuz, ergonomik tasarımında 1000 dpi çözünürlüklü mouse... 350 TL fiyatıyla..."* — 1.416 ms / 192 token |
| `GET /api/ai/products/4/insight` | `ProductInsight` record'u dolu döndü (summary/targetAudience/keywords/priceAssessment) |
| `POST /api/ai/catalog/ask` | *"Stoğu en az olan ürün Laptop, fiyatı 25.000 TL. 3 numaralı ürünün adı Keyboard."* — 2.396 ms / 563 token, log: `AI tool çağrıldı: listProducts -> 5 kayıt` |

Yanıttaki üç bilginin (en düşük stok, fiyatı, id=3'ün adı) hiçbiri prompt'ta yoktu — model
tool'u çağırıp Oracle'daki veriyi okudu.

## Deploy sırasında çıkan iki platform hatası (ikisi de düzeltildi)

Spring AI'ın bağımlılık zinciri, daha önce hiç karşılaşılmamış iki durumu ortaya çıkardı.
Her ikisinin de düzeltmesi **framework tarafındadır** (zeus-fw), uygulamada değil:

1. **Kapsam denetimi yanlış pozitifi** — `verify-module-coverage.sh`, `dependency:list`
   çıktısındaki sürümü hep 4. alandan okuyordu. **Classifier'lı** artefaktlarda bir alan
   fazladır (`gid:aid:jar:classifier:version:scope`); reactor-netty'nin getirdiği native
   netty jar'ları (`netty-transport-native-epoll:linux-x86_64` vb.) bu yüzden "module'de yok"
   diye raporlandı — oysa module'deydiler. Script alan sayısını sayacak şekilde düzeltildi.

2. **`jakarta.websocket.Endpoint` NoClassDefFoundError** — module'e `spring-webflux` girince
   WildFly'ın POST_MODULE anotasyon taraması `StandardWebSocketHandlerAdapter`'ı link etmeye
   çalıştı; `jakarta.websocket` API'si module'ün bağımlılıklarında olmadığı için **deploy düştü**.
   `install-zeus-module.sh`'ın ürettiği module.xml'e `jakarta.websocket.api` eklendi.

   > Ders: kapsam denetimi "jar var mı?" sorusunu cevaplar, "sınıflar link olur mu?" sorusunu
   > cevaplayamaz. Module'e yeni bir Spring modülü (webflux gibi) girdiğinde, onun ihtiyaç
   > duyduğu **jakarta API module'leri** de module.xml'e eklenmelidir.

## Yeni bir AI ucu eklerken

1. Veri gerekiyorsa: `ProductAiTools`'a `@Tool` metodu ekle — **repository üzerinden**, yeni
   veri yolu açma. Yazma işlemi ise yetki kontrolünü metodun içine koy.
2. Yapılandırılmış çıktı istiyorsan hedef record'u `dto/` altına ekle ve `askAs(...)` kullan.
3. İş mantığı `ProductAiService`'e, HTTP `ProductAiController`'a. Spring AI sınıflarını
   uygulama koduna **import etme** — `@Tool` anotasyonu dışında bağımlılık `ZeusAiAssistant`'tır.
