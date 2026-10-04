# 20 — Araştırma ajanı (`zeus-ai-agent` kanıt kullanımı)

Bu doküman, `zeus-ai-agent`'ı tüketen bir uygulamanın **referans örneğidir**. `GET
/api/agent/products/analysis` ucu, mevcut `ai/ProductAiTools`'u kullanarak çok adımlı bir katalog
analizi koşusu tetikler. `ProductAiTools`, `ProductRepository` ve `PRODUCT_PKG` yoluna
**dokunulmadı**.

Framework tarafı, tasarım gerekçeleri ve bilinen boşluklar: `../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`.

## Ne değişti

| Dosya | Değişiklik |
|---|---|
| `pom.xml` | `com.zeus:zeus-ai-agent` bağımlılığı (sürümsüz, BOM'dan) |
| `ai/ProductAnalysisAgent.java` | **YENİ** — sistem promptu + `ZeusAgent.run(...)` çağrısı |
| `dto/AgentRunResponse.java` | **YENİ** — `answer`/`workspace`/`steps`/`promptTokens`/`completionTokens`/`durationMs`/`stopReason` |
| `controller/ProductAgentController.java` | **YENİ** — `GET /api/agent/products/analysis` |
| `src/main/resources/application.properties` | Araştırma ajanı bloğu (`zeus.ai.agent.*`) |
| `src/test/resources/application.properties` | `zeus.ai.agent.enabled=true` — **bu dosya main'i GÖLGELER** |

`ai/ProductAiTools.java`, `repository/`, `service/` katmanlarının hiçbiri **değişmedi**.

## Veri yolu — yeni yol açılmadı

```
GET /api/agent/products/analysis
          │
          ▼
ProductAnalysisAgent.analyze(question)
          │  AgentSpec.of(SISTEM_PROMPTU, productAiTools)
          ▼
ZeusAgent.run(...)                         ← zeus-ai-agent (DefaultZeusAgent)
          │  model art arda tool çağırır, ara bulguları çalışma alanına yazar
          ▼
ProductAiTools.listProducts()              ← DEĞİŞMEDİ (ai katmanının mevcut tool'u)
          ▼
ProductRepository                          ← DEĞİŞMEDİ
          ▼
Oracle PRODUCT_PKG.get_all                 ← DEĞİŞMEDİ
```

Ajanın eklediği tek şey **çok adımlılık**: model veriyi çeker, bulgularını `/bulgular.md`'ye yazar
(sohbete dökmez), sonunda kısa bir özet döner. Model veritabanına doğrudan erişmez, SQL üretmez;
yalnızca `ProductAnalysisAgent`'ın verdiği `productAiTools`'u (+ framework'ün çalışma alanı
tool'larını: `ls`/`readFile`/`writeFile`/`editFile`/`grep`) çağırabilir.

## `AgentSpec` kullanımı

```java
private static final String SISTEM_PROMPTU = """
        Sen bir katalog analiz ajanısın. Görevini adım adım yürüt:
        1. Önce listProducts ile katalogu çek.
        2. Bulgularını /bulgular.md dosyasına yaz (writeFile) — uzun metni sohbete dökme.
        3. Son olarak kısa bir özet cevap ver.
        Yalnızca sana verilen araçlardan gelen veriyi kullan; ürün özelliği UYDURMA.
        """;

private final ZeusAgent agent;
private final ProductAiTools productAiTools;

public AgentResult<String> analyze(String question) {
    return agent.run(question, AgentSpec.of(SISTEM_PROMPTU, productAiTools));
}
```

`AgentSpec.of(systemPrompt, tools...)`, `budget` alanını **`null`** bırakır — bu "sabit bir
varsayılana düş" anlamına GELMEZ, "ajanın yapılandırılmış varsayılanını kullan" anlamına gelir.
Uygulama kendi bütçesini `application.properties`'teki `zeus.ai.agent.max-steps` / `max-duration`
ile `ZeusAgentAutoConfiguration` üzerinden ayarlar; bu değerler `ZeusAgent` bean'i kurulurken
`DefaultZeusAgent`'a geçirilir ve her koşuda `spec.budget() == null` olduğunda devreye girer
(framework tarafı: `../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`, "bütçe property'leri" bölümü).

Controller yalnızca HTTP'dir; `AgentResult<String>`'i `AgentRunResponse`'a çevirir —
`stopReason` ve `steps` **bilinçli olarak** dışarı verilir: "model bitirdi" ile "bütçe doldu" aynı
yanıt gövdesine karışmamalı, çağıran durumu görebilmelidir.

## Yapılandırma

```properties
# --- Araştırma ajanı (zeus-ai-agent) ---
# Koşu SENKRONDUR; bütçe bunu mümkün kılar. max-duration, çağıranın HTTP timeout'undan
# KÜÇÜK tutulmalıdır.
zeus.ai.agent.enabled=true
zeus.ai.agent.max-steps=10
zeus.ai.agent.max-duration=2m
```

> **TUZAK — test property'leri gölgeler.** `src/test/resources/application.properties`,
> `src/main` altındaki aynı adlı dosyayı test classpath'inde **tamamen gölgeler**. `zeus-ai-mcp`
> entegrasyonunda (`gelistirmeler/19-mcp-sunucusu.md`) zaten bir kez yaşanan tuzak: yetenek
> property'si yalnızca `src/main`'e yazılırsa test context'i `ZeusAgent` bean'ini görmez ve
> (bu uygulamada değil ama komşu modüllerde) `@SpringBootTest` açılışı düşer. Bu yüzden
> `zeus.ai.agent.enabled=true` test dosyasına da **açıkça** eklendi:
> ```properties
> zeus.ai.agent.enabled=true
> ```
> Not: `max-steps`/`max-duration` testte tekrarlanmadı — context-load testi gerçek bir koşu
> tetiklemiyor, yalnızca bean'in kurulmasını doğruluyor; varsayılan bütçe (15 adım/3 dk) bu amaç
> için yeterli.

## Senkron koşu ve HTTP timeout notu

`ZeusAgent.run` **bloklar**. `zeus.ai.agent.max-duration=2m` bilinçli olarak WildFly'ın/istemcinin
tipik HTTP timeout değerlerinden (genelde ≥ 30s-öntanımlı ama kurumsal proxy'lerde dakikalarca
olabilir) **küçük** tutuldu; `ZeusAgent`'ın kendi bütçesi, istemci bağlantıyı kesmeden önce temiz
durabilsin diye. Bu uygulamada ayrıca bir HTTP timeout YAPILANDIRMASI yoktur (Undertow/WildFly
varsayılanı); kalıcı çözüm (asenkron iş modeli) bilinçli olarak kapsam dışıdır — bkz.
`../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`.

## Gerçek koşu — ölçüm (canlı model ile, 2026-10-04)

Aşağıdakilerin tamamı **gerçek bir OpenRouter anahtarıyla, WildFly üzerinde, canlı Oracle'a karşı**
ölçülmüştür. (Bu dokümanın ilk sürümü ortamda anahtar olmadığı için yalnız hata yolunu
ölçebilmişti; o kısım aşağıda "Hata yolu" başlığında korunuyor, çünkü kendi başına değerli.)

Anahtar WildFly **sürecinin ortamında** olmalıdır — uygulama değeri açılışta okur, bu yüzden
anahtarı sonradan tanımlamak için **restart** gerekir (yeniden deploy yetmez):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export AI_API_KEY='<openrouter-anahtarı>'
$WILDFLY_HOME/bin/jboss-cli.sh --connect --command=:shutdown
nohup $WILDFLY_HOME/bin/standalone.sh > /tmp/wf.log 2>&1 &

curl -s "http://127.0.0.1:8080/spring-wildfly-arch/api/agent/products/analysis" \
     -H "X-Correlation-Id: canli1"
```

### 1) Koşu modelin kendi kararıyla bitti

```json
{"stopReason":"MODEL_FINISHED","steps":3,
 "promptTokens":7366,"completionTokens":659,"durationMs":10339}
```

Üç adım, ~10 saniye, 8.025 token. Bütçe (10 adım / 2 dk) **dolmadı**; koşuyu model bitirdi.

### 2) Ajan raporunu çalışma alanına yazdı ve veri GERÇEK

Dönen `workspace` tek dosya içeriyor: **`/bulgular.md`**. İçeriği (kısaltılmış):

```markdown
# Katalog Ürün Analizi

| Ürün Adı | Fiyat (TL) | Stok Adedi |
|----------|------------|------------|
| Laptop   | 25.000     | 10         |
| Mouse    | 350        | 100        |
| Keyboard | 750        | 50         |
| Monitor  | 5.500      | 20         |
| Webcam   | 1.200      | 30         |
```

Oracle'daki gerçek satırlarla karşılaştırıldı — **beşi de birebir aynı**
(`Laptop|25000|10`, `Mouse|350|100`, `Keyboard|750|50`, `Monitor|5500|20`, `Webcam|1200|30`).
Uydurulmuş ürün ya da uydurulmuş değer yok. Uzun tablo sohbete değil **dosyaya** yazıldı; sohbete
yalnızca kısa özet döndü — harness'ın varlık sebebi tam olarak bu.

### 3) Veri yolu kanıtı — tek correlation-id altında üç satır

```
[default task-2] [canli…] ProductAiTools       : AI tool çağrıldı: listProducts -> 5 kayıt
[default task-2] [canli…] DefaultZeusAgent     : Ajan koşusu bitti — adım=3, token=8025, süre=10339 ms, sebep=MODEL_FINISHED
[default task-2] [canli…] RequestLoggingFilter : GET /api/agent/products/analysis -> 200 (10388 ms)
```

Üçü de aynı istek thread'inde (`default task-2`, bir Undertow worker'ı). Bu iki şeyi birden
kanıtlıyor: veri uygulamanın **mevcut** `ProductAiTools` → `ProductRepository` → `PRODUCT_PKG`
yolundan geldi (yeni yol açılmadı), ve tool gövdesi istek thread'inde koştuğu için MDC'deki
correlation-id koşu boyunca korundu.

### 4) Bütçe gerçekten kesiyor

`zeus.ai.agent.max-steps=1` ile yeniden deploy edilip (restart gerekmez) tekrar çağrıldığında:

```json
{"stopReason":"STEP_BUDGET","steps":1,"workspace":{}}
```
```
DefaultZeusAgent : Ajan koşusu bitti — adım=1, token=3294, süre=3730 ms, sebep=STEP_BUDGET
```

Açılış logu da yapılandırılan bütçeyi bildirdi: `varsayılan bütçe: 1 adım / 200000 token / PT2M`
(property geri alındığında yine `10 adım … PT2M`). Bu, `zeus.ai.agent.*` property'lerinin
koşuya **gerçekten** ulaştığının uçtan uca kanıtıdır — nihai review'da bu bağlantının kopuk
olduğu bulunmuş ve düzeltilmişti; burada yalnız birim testiyle değil, canlı koşuyla doğrulanıyor.

Ayrıca: bütçe dolduğunda istisna fırlatılmadı, HTTP 200 döndü ve `stopReason` durumu açıkça
söyledi — "model bitirdi" ile "bütçe doldu" aynı gövdeye karışmıyor.

### Hata yolu (anahtarsız ölçüm, korunuyor)

Anahtar tanımlı değilken aynı uç şunu döndürmüştü:

```json
{"answer":null,"workspace":{},"steps":0,"promptTokens":0,"completionTokens":0,
 "durationMs":81,"stopReason":"ERROR"}
```

Sunucu logunda sağlayıcının `401`'i aynı correlation-id altında görünüyordu. İstemciye **stack
trace sızmadı ve 502 dönmedi**: `DefaultZeusAgent` hatayı soğurup `StopReason.ERROR` ile normal
bir sonuç döndürdü. Çağıran `stopReason`'a bakarak `answer`'a güvenip güvenmeyeceğine karar
verebiliyor.


## Bilinen boşluklar

- `ZeusAgent`'ın kendisi gibi (`../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`), bu uç de
  **yetenek kaydına tabi değildir**: `zeus-ai-agent` jar'ı eklenip `zeus.ai.agent.enabled=true`
  yazılmazsa açılışta UYARI alınmaz, yalnızca `ProductAnalysisAgent`'ın `ZeusAgent` bağımlılığı
  `NoSuchBeanDefinitionException` ile ilk istekte patlar (bean açılışta değil `@RestController`
  constructor'ında enjekte edilir — bu da açılışta patlar, ilk istekte değil; ama hata mesajı
  yine de "property unutuldu" demez).
- Koşu **senkron**dur; eşzamanlı birden çok analiz isteği WildFly'ın worker thread havuzunu
  doğrudan tüketir (ayrı bir kuyruk/limit yoktur).
- `question` parametresi doğrulanmaz (`@RequestParam` serbest metin) — model promptuna doğrudan
  girer; bu bir kanıt ucu olduğu için giriş doğrulama/oran sınırlama bilinçli olarak kapsam
  dışıdır.
- Bütçe ve property bağlantısı artık **bu uygulamada da** canlı modelle ölçüldü
  (yukarıdaki "4) Bütçe gerçekten kesiyor"): `max-steps=1` ile koşu `STEP_BUDGET` ile durdu.
  Framework tarafında aynı şey `BudgetEligibilityCheckerTest` ve `DefaultZeusAgentTest`'te
  `StubChatModel` ile test edilmiş durumda (`zeus-ai-agent/src/test`); artık modelden bağımsız
  test ile gerçek zincir birbirini doğruluyor. **Kalan boşluk:** `TOKEN_BUDGET` ve
  `TIME_BUDGET` yolları bu uygulamada canlı ölçülmedi — yalnız `STEP_BUDGET` ölçüldü; diğer
  ikisi framework birim testleriyle kapsanıyor.
- Model çıktısının **doğruluğu** test edilmiyor. Ölçümde tablo Oracle ile birebir çıktı, ama bu
  tek bir koşudur; ajanın raporunun her zaman doğru olacağını garanti eden bir mekanizma
  (doğrulama tool'u, şema zorlaması) yoktur. Uç bir kanıt ucudur, rapor üretim kaynağı değil.
