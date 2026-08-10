# 17 — Spring AI Sürüm Seçimi: Neden 2.x, Neden 1.x Değil

Bu doküman, Spring AI entegrasyonunda **2.x hattının** seçilmesinin gerekçesini ve 1.x ile
farklarını kayıt altına alır. Amaç, "neden 1.x'i seçmediniz?" sorusuna verilecek cevabın
tek bir yerde, doğrulanabilir referanslarla durması.

> Not: Bu bir **sürüm/uyumluluk kararı** dokümanıdır; Spring AI'ın uygulamaya fiilen
> entegrasyonu (bağımlılıklar, `com.zeus` module etkisi, konfigürasyon) ayrı bir
> geliştirme olarak ele alınır. Framework tarafını ilgilendiren kısımlar (BOM'a sürüm
> eklenmesi, module'e jar girmesi) `../../zeus-fw` tarafında yaşar — bkz. `08-com-zeus-module.md`.

## Kısa cevap

**Spring Boot 4 kullanan bir uygulamada Spring AI 1.x zaten bir seçenek değil.**
Spring AI 1.x, Spring Boot 3.5 hattına bağlıdır ve Boot 4 context'inde yüklenmez.
Bu projenin platformu **Spring Boot 4.0.7 / Java 25 / WildFly 41** (zeus 2.0.0 —
bkz. `16-java25-boot4-wildfly41-yukseltme.md`) olduğu için karar teknik olarak
burada kapanır. Aşağıdaki maddeler bu kararı destekleyen ikincil argümanlardır.

## Farklar (1.0 / 1.1 → 2.0)

Spring AI 2.0.0 GA: **12 Haziran 2026**.

| Konu | Spring AI 1.0 / 1.1 | Spring AI 2.0 |
|------|---------------------|---------------|
| Platform | Spring Boot 3.5 / Framework 6, Jackson 2 | Spring Boot 4.0–4.1 / Framework 7, Jakarta EE 11, Jackson 3 |
| Null safety | Spring'in kendi `org.springframework.lang` anotasyonları | JSpecify (Spring portföyünün yeni standardı) |
| Tool calling | Tool-call döngüsü her chat model implementasyonunun **içine gömülü ve kapalı** — araya girmek, sarmalamak, stratejiyi değiştirmek mümkün değil | Döngü advisor zincirine çıkarıldı: `ToolCallingAdvisor` otomatik kayıtlı, iterasyonlar elle kontrol edilebiliyor. Ayrıca `ToolSearchToolCallingAdvisor` (yüzlerce tool'u progressive disclosure ile ölçekleme) ve `StructuredOutputValidationAdvisor` (JSON çıktısını doğrulayıp kendi kendini düzeltme) |
| MCP | Toplulukta gelişiyor, transport'lar MCP Java SDK'da | Core'a alındı: `@McpTool` / `@McpResource` / `@McpPrompt`, WebMVC/WebFlux transport'ları Spring AI içinde, streamable HTTP varsayılan (SSE deprecated), OpenTelemetry metrikleri + OAuth 2.0 desteği. MCP Java SDK 2.0.0 (2025-11-25 spesifikasyonu) |
| Model sağlayıcılar | Aynı sağlayıcı için birden çok varyant (OpenAI 3, Anthropic 2, Google GenAI 2 implementasyon) | Sağlayıcı başına tek, SDK tabanlı implementasyon; gereksiz HTTP/Azure varyantları kaldırıldı |
| Options / konfigürasyon | Property key'lerinde yapay `.options` segmenti, constructor ile kurulan options, varsayılanlar dağınık | `.options` kalktı, builder tabanlı ve **immutable** options, varsayılanlar options seviyesinde tek yerde, reflection'sız merge |
| Artifact adları | `spring-ai-advisors-vector-store` | `spring-ai-vector-store-advisor` (ve benzeri yeniden adlandırmalar) |
| Destek | Boot 3.5 ile hizalı — **Haziran 2026'da EOL**, yeni güvenlik/hata yaması yok | Aktif hat |

Advisor framework, VectorStore soyutlaması ve RAG desteği 1.0'da da vardı; 2.0 bunları
olgunlaştırıp API'yi stabilize etti. Yani 2.0'ın katkısı "yeni özellik listesi"nden çok
**API stabilizasyonu + tool/MCP mimarisinin açılması**.

## "Neden 1.x'i seçmediniz?" — argüman sırası

1. **Uyumluluk (asıl gerekçe, tek başına yeterli).**
   Platform Spring Boot 4.0.7 / Java 25. Spring AI 1.x Boot 3.x'e bağımlıdır, Boot 4
   üzerinde çalışmaz. 1.x'i seçmek, "bir AI kütüphanesi uğruna tüm platformu Boot 3.5'te
   dondurmak" anlamına gelirdi — zeus 2.0.0 yükseltmesinin tersine bir karar.

2. **Destek penceresi.**
   Spring Boot 3.5 ve onunla hizalı Spring AI 1.0/1.1 Haziran 2026'da EOL oldu
   (3.5.16 ücretsiz son sürüm). Yeni bir işi, ilk günden yama almayan bir hat üzerine
   kurmak savunulabilir değil: CVE çıktığında elde kalan seçenekler ticari genişletilmiş
   destek satın almak ya da acil migrasyon yapmak.

3. **Göç maliyeti zaten ödendi.**
   1.x → 2.0 geçişinin asıl yükü Spring AI'ın kendisi değil, Boot 4 kaynaklı kırıcı
   değişikliklerdir (Jackson 3, Jakarta EE 11, Security 7, Undertow ve JUnit 4'ün
   kaldırılması). Bu göç platform yükseltmesinde zaten yapıldı. 1.x'te kalmak maliyeti
   ortadan kaldırmaz; daha büyük bir yığınla, daha ileri bir tarihe erteler.

4. **Mimari kazanç (geriye taşınmadı, yalnızca 2.0'da var).**
   Tool döngüsünün advisor zincirine çıkması, kendi retry / guardrail / audit / loglama
   mantığımızı araya koyabilmemiz demek. 1.x'te bu döngü model implementasyonunun içinde
   kapalıydı; müdahale ancak kütüphaneyi fork ederek mümkündü. MCP'nin core'a girmesi de
   üçüncü parti entegrasyonlarda topluluk modüllerine bağımlılığı kaldırıyor.

5. **Sadeleşme.**
   Sağlayıcı başına tek implementasyon ve builder tabanlı immutable options; 1.x'te
   varyant seçiminden ve constructor/property karmaşasından doğan hata sınıfını baştan eliyor.

## Dürüst karşı senaryo (sorulursa söylenecek)

1.x yalnızca şu durumda savunulabilirdi: Boot 3.5'e çakılı, kısa ömürlü veya geliştirmesi
donmuş bir uygulama **ve** ticari genişletilmiş destek alınıyor olması. Bizim durumumuz
bunun tersi — Boot 4 / Java 25 üzerinde, uzun ömürlü olması beklenen, aktif geliştirilen
bir uygulama.

## Bu projeye özel etkiler (entegrasyon yapılırken dikkat)

- **İnce WAR + `com.zeus` module:** Spring AI jar'ları 3. parti olduğu için WAR'a değil,
  paylaşımlı `com.zeus` module'üne girer. Yani sürüm eklemek/yükseltmek
  `../../zeus-fw/zeus-wildfly-module` aggregator'ını ve `install-zeus-module.sh`
  çalıştırmayı, ardından **WildFly restart**'ı gerektirir. Bkz. `08-com-zeus-module.md`.
- **Jackson 3:** 2.0 Jackson 3 kullanır; platform zaten Boot 4 ile Jackson 3'te olduğu için
  ek bir çatışma beklenmiyor, ancak module içinde tek Jackson sürümü kalmasına dikkat edilir.
- **Sürüm yönetimi:** Spring AI sürümü pom'a yazılmaz, zeus BOM'dan yönetilir
  (bkz. `13-zeus-framework-entegrasyonu.md`).

## Kaynaklar

- [Spring AI 2.0.0 GA Available Now](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/)
- [Spring AI 2.0.0-RC1 Available Now](https://spring.io/blog/2026/06/06/spring-ai-2-0-0-RC1-available-now/)
- [Spring — Support Policy](https://spring.io/support-policy/)
- [Spring Boot End of Life: Every 3.x Branch Is Now Unsupported](https://www.danvega.dev/blog/spring-boot-end-of-life)
- [Spring AI 2.0 Is Coming Soon (HeroDevs)](https://www.herodevs.com/blog-posts/spring-ai-2-0-is-coming-soon-your-boot-4-0-migration-does-not-have-to-start-tomorrow)
- [Spring Boot 3 EOL to Spring Boot 4: A Production Upgrade Playbook (Jackson 3)](https://loiane.com/2026/04/spring-boot-3-eol-to-4-upgrade-playbook-jackson-3/)
