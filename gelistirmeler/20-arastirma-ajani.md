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

## Gerçek koşu — ölçüm (bu ortamda yapılabilen)

Bu ortamda **LLM API anahtarı yoktur** (`AI_API_KEY` ne kabukta ne de çalışan WildFly sürecinde
tanımlı). Bu yüzden gerçek bir model koşusu tetiklenemedi; ölçülen şey **bütçesiz/anahtarsız hata
yolu**dur — bu da gerçek ve kayda değer bir sonuçtur: hata, istemciye 500/502 veya stack trace
olarak sızmadı, `DefaultZeusAgent`'ın yakala-bloğu tarafından soğuruldu ve HTTP 200 + `ERROR`
olarak döndü.

```bash
export ZEUS_MCP_TOKEN="$(cat /tmp/zeus_mcp_token 2>/dev/null || openssl rand -hex 16)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
cd ../zeus-fw && mvn clean install && cd ../spring-wildfly-arch && ./scripts/deploy.sh
# NOT: yeni 3. parti jar yok → install-zeus-module.sh ve WildFly restart GEREKMEDİ.

curl -s "http://127.0.0.1:8080/spring-wildfly-arch/api/agent/products/analysis" \
     -H "X-Correlation-Id: ajankanit1" | tee /tmp/agent.out
```

Gerçek yanıt (ölçüldü, 2026-10-04, **I3 fix öncesi DTO şekliyle** — o zamanki `AgentRunResponse`
tek bir `tokens` alanı taşıyordu; fix sonrası aynı koşu `"promptTokens":0,"completionTokens":0`
verir, değer anlamı DEĞİŞMEDİ çünkü ilk model çağrısı 401'de düştüğü için ikisi de sıfırdı):

```json
{"answer":null,"workspace":{},"steps":0,"tokens":0,"durationMs":336,"stopReason":"ERROR"}
```

Sunucu logunda aynı correlation-id altında:

```
[ajankanit1] c.z.framework.ai.agent.DefaultZeusAgent : Ajan koşusu hata ile bitti: 401: Missing Authentication header
[ajankanit1] c.z.framework.ai.agent.DefaultZeusAgent : Ajan koşusu bitti — adım=0, token=0, süre=336 ms, sebep=ERROR, dosya=0
[ajankanit1] c.z.f.logger.RequestLoggingFilter        : GET /spring-wildfly-arch/api/agent/products/analysis -> 200 (372 ms)
```

Yorum: `401 Missing Authentication header` OpenRouter'ın (varsayılan `AI_BASE_URL`) sahte anahtarı
(`degistir`) reddetmesidir — model ilk çağrıda başarısız olduğu için **hiçbir tool hiç
çağrılmadı** (`adım=0`), dolayısıyla bu koşuda `ProductAiTools` log satırı YOKTUR (adım 0'da
beklenen budur — tool çağrısı hiç başlamadı). `workspace` boş `{}` döndü (beklenen; hiçbir
`writeFile` çağrısı olmadı), ama alan **vardı** — `AgentResult.workspace()` hata durumunda bile
dönüyor (R: hata yönetimi kontratı doğrulandı). HTTP durumu **200**, gövdede stack trace **yok** —
Task 4'ün "koşu hata alsa da workspace döner, istisna sızmaz" iddiası bu ölçümle doğrulandı.

### Canlı model gerektirdiği için yapılamayanlar

Aşağıdakiler geçerli bir `AI_API_KEY` olmadan ÖLÇÜLEMEDİ; koddan veya tasarımdan bir eksiklik
DEĞİL, bu ortamın kısıtı:

1. **`/bulgular.md` dosyasının gerçek ürün verisiyle yazılması** — model `writeFile` tool'unu
   hiç çağıramadı (ilk çağrı 401'de düştü).
2. **`stopReason = MODEL_FINISHED`** — normal bitişin ölçümü; bu koşuda `ERROR` ile sonuçlandı.
3. **Correlation-id altında `ProductAiTools` + `DefaultZeusAgent` ikilisinin birlikte görünmesi**
   — `ProductAiTools` satırı yalnızca model en az bir tool çağırdığında basılır; bu koşuda hiç
   tool çağrılmadı.
4. **Bütçe ölçümü (`zeus.ai.agent.max-steps=1` → `stopReason = STEP_BUDGET`)** — bütçenin adım
   sayarak durdurduğunu göstermek için modelin önce en az bir adım BAŞARIYLA tamamlaması gerekir;
   anahtarsız ortamda ilk adımda zaten `ERROR` ile düşüldüğü için `max-steps=1` ile
   `max-steps=10` arasında gözlemlenebilir bir fark YOKTUR (ikisi de aynı 401'de aynı adımda
   durur). Bu ölçüm anahtarla tekrarlanmadan geçerli sayılamaz.

> **GÜNCELLEME (C1 fix sonrası) — bu prosedür artık GEÇERLİ, önceden geçersizdi.** Framework
> tarafında (`../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`, C1) ölçülen kusur: `AgentSpec.of(...)`
> bütçeyi her zaman `AgentBudget.defaults()` (15 adım) ile DOLDURUYORDU, `zeus.ai.agent.max-steps`
> property'si (burada `10`) hiçbir koşuda OKUNMUYORDU. Yani C1 ÖNCESİ aşağıdaki
> `max-steps=1` denemesi bile gerçek davranışı ÖLÇMEZDİ — anahtar olsa da `stopReason` 1 adımda
> değil 15 adımda `STEP_BUDGET` olurdu (ya da model 15'ten önce bitirirse hiç olmazdı), ve bu fark
> sessizce geçerdi. C1 fix'ten sonra `AgentSpec.of(...)` bütçeyi `null` bırakır ve
> `ZeusAgentAutoConfiguration`'ın property'lerden kurduğu bütçe gerçekten uygulanır — bu yüzden
> aşağıdaki prosedür artık **anlamlı bir doğrulamadır**, sadece hâlâ geçerli bir `AI_API_KEY`
> gerektirdiği için bu ortamda **henüz çalıştırılmadı**.

**Anahtarla tekrarlanacak tek komut** (yukarıdaki curl'ün aynısı, yalnızca ortamda geçerli bir
`AI_API_KEY` tanımlıyken):

```bash
export AI_API_KEY=<gerçek-anahtar>
curl -s "http://127.0.0.1:8080/spring-wildfly-arch/api/agent/products/analysis" \
     -H "X-Correlation-Id: ajankanit1" | tee /tmp/agent.out
grep ajankanit1 $WILDFLY_HOME/standalone/log/server.log
```

Bütçe ölçümü için ayrıca `zeus.ai.agent.max-steps=1` ile yeniden deploy edip aynı isteği tekrar
atmak, `stopReason = STEP_BUDGET` beklemek ve ardından değeri `10`'a geri almak gerekir. C1
fix'ten önce bu adım geçersiz bir sonuç üretirdi (yukarıdaki güncellemeye bakın); artık
`zeus.ai.agent.max-steps` property'si gerçekten uygulandığı için bu deneme geçerli bir kanıttır.

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
- Bütçenin gerçekten durdurduğu (`STEP_BUDGET`) bu ortamda ölçülemedi (yukarıya bakın);
  framework tarafında `BudgetEligibilityCheckerTest` ile `StubChatModel` kullanılarak zaten
  ölçülmüştür (`zeus-ai-agent/src/test`), ama bu uygulamanın gerçek WildFly+Oracle+model
  zincirinde teyidi anahtar gerektirir.
- **`zeus.ai.agent.max-steps`/`max-duration` property'lerinin GERÇEKTEN uygulandığı** (C1 fix,
  `../zeus-fw/gelistirmeler/24-zeus-ai-agent.md`) framework tarafında `DefaultZeusAgentTest`'teki
  `StubChatModel` tabanlı uçtan uca testle kanıtlandı (küçük bir yapılandırılmış varsayılan
  bütçeyle koşu 15 değil o küçük sınırda durur). Bu uygulamada aynı teyit yine bir `AI_API_KEY`
  gerektirir — henüz yapılmadı, yukarıdaki "GÜNCELLEME" notuna bakın.
