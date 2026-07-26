# 15 — Performans ve Güvenlik Analizi (referans uygulama)

Bu doküman, `spring-wildfly-arch`'ın **uygulama düzeyi** performans ve güvenlik analizidir.
Framework (platform) düzeyi analiz ve kural setleri zeus-fw tarafındadır:
`../../zeus-fw/gelistirmeler/11-guvenlik-analizi-best-practices.md` (güvenlik, G-x bulguları) ve
`../../zeus-fw/gelistirmeler/12-performans-analizi-best-practices.md` (performans, P-x bulguları).

**Neden bu doküman önemli:** Bu proje, zeus-fw'ı kullanacak 1000+ uygulamanın **referans
örneğidir** — ekipler buradaki kalıpları kopyalayacaktır. Buradaki her iyi kalıp 1000 kez
çoğalır; her kusur da öyle. Bu yüzden bulgular yalnızca "bu uygulamanın sorunları" değil,
"kopyalanmaması gereken kalıplar" olarak okunmalıdır.

**Kapsam notu:** Analiz dokümanıdır; kod değişikliği içermez. Düzeltmeler sondaki backlog'da
[APP] (bu uygulama / uygulama ekibi kuralı) ve [FW-bekleyen] (kalıcı çözümü framework'te olan)
etiketleriyle listelenir.

Format: **Önem / Bulgu / Dosya / Etki / Önerilen düzeltme / Sahiplik**.

## Güvenlik Bulguları

### AG-1 [KRİTİK] Endpoint'lerde hiçbir kimlik doğrulama/yetkilendirme yok

- **Bulgu:** `/api/products` altındaki 5 CRUD endpoint'inin tamamı anonim erişime açıktır —
  Spring Security bağımlılığı yok, hiçbir authn/authz mekanizması yok. Ağa erişen herkes ürün
  oluşturabilir/silebilir.
- **Dosya:** `src/main/java/com/zeus/springwildflyarch/controller/ProductController.java`
  (ve `pom.xml` — security starter'ı yok).
- **Etki:** Bu bir demo için kabul edilebilir görünse de, referans uygulama olarak
  **"güvenliksiz endpoint" kalıbını** 1000 ekibe örnek gösterir. Ayrıca paylaşımlı `com.zeus`
  module'ü `spring-boot-starter-security` taşımadığı için bir ekip güvenlik eklemek istese
  bugün ekleyemez (jar module'de yok).
- **Önerilen düzeltme:** Kalıcı çözüm framework'tedir: `zeus-security` modülü (zeus-fw doküman
  11 / **G-2** — varsayılan authenticated zincir). O gelene kadar bu uygulamada en azından
  WildFly/ağ katmanında erişim kısıtı belgelenmelidir; `zeus-security` gelince referans uygulama
  ilk entegrasyon örneği olmalıdır.
- **Sahiplik:** [FW-bekleyen: G-2] + [APP: entegrasyon örneği]

### AG-2 [YÜKSEK] Repo'ya commit'lenmiş kimlik bilgileri

- **Bulgu:** `application-local.properties` içinde Oracle kullanıcı adı/şifresi düz metin
  (`spring.datasource.username=ABC` / `password=ABC`); ayrıca `CLAUDE.md` WildFly yönetim
  konsolu hesabını (admin / Wildfly@123) açıkça içerir.
- **Dosya:** `src/main/resources/application-local.properties`, `CLAUDE.md`.
- **Etki:** Lokal/dev şifreleri olsalar da **repo'da sır** kalıbıdır; 1000 ekip bunu kopyalarsa
  gerçek ortam şifreleri de properties dosyalarına sızacaktır. Git geçmişine giren sır, silinse
  bile geçmişte kalır.
- **Önerilen düzeltme:** Kural (zeus-fw doküman 11'deki sorumluluk matrisi): sırlar repo'ya
  girmez — lokal geliştirmede ortam değişkeni/`.env` (gitignore'lu), sunucuda WildFly datasource
  (zaten böyle). Referans uygulamada: `application-local.properties`'te placeholder
  (`${ORACLE_PASSWORD:}`) kalıbına geçilmesi ve dokümanların gerçek şifre yerine "add-user.sh ile
  oluşturun" yönlendirmesi. Pre-commit sır taraması (ör. gitleaks) önerilir.
- **Sahiplik:** [APP]

### AG-3 [YÜKSEK] `show-sql=true` varsayılan (=prod) profilde

- **Bulgu:** `spring.jpa.show-sql=true` ve `hibernate.format_sql=true` **ortak**
  `application.properties`'tedir — yani WildFly'a deploy edilen varsayılan profilde de açıktır.
  SQL metinleri prod log'una yazılır (bilgi sızıntısı + log hacmi + biçimleme maliyeti).
- **Dosya:** `src/main/resources/application.properties` (satır 20-21).
- **Etki:** Güvenlik yüzü: log'a düşen SQL, şema/sorgu yapısını log erişimi olan herkese açar.
  Performans yüzü: her SQL'in biçimlenip loglanması yüksek trafikte ölçülebilir maliyettir
  (AP-3 ile aynı kök neden).
- **Önerilen düzeltme:** `show-sql`/`format_sql` yalnızca `application-local.properties`'e
  taşınır; ortak dosyada kapalı olur. Kural: SQL izleme prod'da log ile değil merkezî
  APM/izleme ile yapılır.
- **Sahiplik:** [APP]

### AG-4 [ORTA] `name` alanında uzunluk kısıtı yok → ORA hatası → işlenmemiş 500

- **Bulgu:** `ProductRequest.name` yalnızca `@NotBlank` taşır; DB kolonu `VARCHAR2(255)`'tir
  (`db/schema.sql`). 255 karakterden uzun `name`, validasyonu geçip Oracle'da `ORA-12899`
  ile patlar; `GlobalExceptionHandler`'da genel handler olmadığı için istemciye temiz 400
  yerine detaylı 500 döner.
- **Dosya:** `src/main/java/com/zeus/springwildflyarch/dto/ProductRequest.java`,
  `src/main/resources/db/schema.sql`.
- **Etki:** İki katmanlı kusur örneği: girişte eksik kısıt (uygulama) + hata sızıntısı
  (framework, zeus-fw doküman 11 / **G-3**). Referans kalıp olarak "DTO kısıtı = DB kısıtı"
  kuralının önemini gösterir.
- **Önerilen düzeltme:** `@Size(max = 255)` eklenir (Türkçe mesajla, mevcut stil). Genel kural:
  her `String` DTO alanı, karşılık gelen kolon uzunluğuyla sınırlanır. 500 davranışının kökü
  G-3 düzeltmesiyle kapanır.
- **Sahiplik:** [APP] (+ [FW-bekleyen: G-3] 500 gövdesi için)

### AG-5 [ORTA] Swagger / api-docs deploy ortamında açık

- **Bulgu:** springdoc paylaşımlı module'den geldiği ve hiçbir `springdoc.*` kısıtlama
  property'si bulunmadığı için `/v3/api-docs` ve `/swagger-ui/index.html` WildFly'da (prod-benzeri
  ortamda) herkese açıktır. Not: `scripts/smoke-test.sh` ve doküman `14-smoke-test.md`
  ilk adımda `/v3/api-docs`'un 200 dönmesine **bilinçli bağımlıdır** — kapatma kararı smoke
  testin güncellenmesini gerektirir.
- **Dosya:** `pom.xml` (springdoc), `scripts/smoke-test.sh`, `gelistirmeler/14-smoke-test.md`.
- **Etki:** API envanterinin (endpoint + şema) anonim keşfi. 1000 uygulamada aynı kalıp = kurum
  API yüzeyinin tamamının keşfedilebilir olması (zeus-fw doküman 11 / **G-5**).
- **Önerilen düzeltme:** Framework prod varsayılanı kapalıya çekince (G-5) bu uygulama örnek
  olur: prod profilde `springdoc.api-docs.enabled=false`, smoke test ilk adımını
  `/api/products`'a (veya api-docs'a yalnız staging'de) taşır. Alternatif: `zeus-security`
  sonrası authn arkasında açık bırakmak.
- **Sahiplik:** [APP] + [FW-bekleyen: G-5]

## Performans Bulguları

### AP-1 [YÜKSEK] `get_all` sınırsız — sayfalama yok

- **Bulgu:** `PRODUCT_PKG.get_all` tüm tabloyu `SELECT ... ORDER BY id` ile döner (limit yok,
  filtre yok); `findAll()` zinciri (Controller → `AbstractCrudService.findAll()` →
  `JdbcProductRepository.findAll()`) tüm satırları belleğe yükleyip her birini DTO'ya çevirir.
  `GET /api/products` yanıt boyutu tablo boyutuyla sınırsız büyür.
- **Dosya:** `src/main/resources/db/procedures.sql` (get_all),
  `src/main/java/com/zeus/springwildflyarch/repository/JdbcProductRepository.java`.
- **Etki:** Tablo büyüdükçe tam tarama + sıralama (DB), tam liste materializasyonu (heap/GC) ve
  büyük JSON yanıtı (ağ) birlikte büyür. Referans kalıp olarak en tehlikeli miras budur —
  framework de sayfalama sunmadığından (zeus-fw doküman 12 / **P-2**) 1000 uygulama bunu kopyalar.
- **Önerilen düzeltme:** P-2 framework desteğiyle birlikte: `get_all`'a `p_offset/p_limit`
  parametreleri (`OFFSET ... ROWS FETCH NEXT ... ROWS ONLY`), repository/service/controller
  zincirine `page/size` parametreleri, makul varsayılan ve üst limit. Bu uygulama, sayfalı SP
  kalıbının referans örneği olmalıdır (yeni SP ekleme akışı: önce `procedures.sql`, sonra iki impl —
  doküman 05).
- **Sahiplik:** [APP] (+ [FW-bekleyen: P-2] servis API'si)

### AP-2 [ORTA] `products_seq` NOCACHE — insert başına sequence turu

- **Bulgu:** `db/schema.sql` sequence'ı `NOCACHE` yaratır. Her `create_product` çağrısı
  `NEXTVAL` için diskteki sequence'ı günceller; eşzamanlı insert'lerde çekişme ve gereksiz
  gecikme yaratır.
- **Dosya:** `src/main/resources/db/schema.sql`.
- **Etki:** Tek başına küçük; ancak yüksek insert hacimli bir uygulamada ölçülebilir. Referans
  şema kalıbı olarak kopyalanması asıl risk.
- **Önerilen düzeltme:** `CACHE 20` (veya yük profiline göre daha yüksek). Boşluklu id'ler
  (cache kaybında atlanan değerler) iş açısından kabul edilebilirse — ki id anlam taşımıyorsa
  edilmelidir — maliyetsiz bir kazanımdır.
- **Sahiplik:** [APP]

### AP-3 [ORTA] SQL loglama maliyeti (AG-3 ile ortak kök)

- **Bulgu/Düzeltme:** AG-3'te tanımlandı — `show-sql=true` + `format_sql=true` her SQL'de
  biçimleme + log I/O maliyeti ekler; prod profilde kapatılır. (Ek olarak framework'ün istek
  başına INFO access-log satırı için bkz. zeus-fw doküman 12 / P-5.)
- **Sahiplik:** [APP]

### AP-4 [DÜŞÜK] PL/SQL prosedürlerinde EXCEPTION bloğu yok

- **Bulgu:** `PRODUCT_PKG` prosedürlerinin hiçbirinde `EXCEPTION` bölümü yok; her Oracle hatası
  ham `ORA-` olarak Java'ya çıkar. Transaction yönetimi doğru (SP'lerde COMMIT yok — JTA/Spring
  yönetir) ancak hata **eşleme** kalitesi düşük: Java tarafı ORA kodundan anlamlı iş hatası
  üretemez, kullanıcıya jenerik 500 düşer (AG-4 senaryosu).
- **Dosya:** `src/main/resources/db/procedures.sql`.
- **Etki:** Performanstan çok teşhis/işletim maliyeti: prod'da hata ayıklama zorlaşır, hata
  sınıflandırması (geçici mi, veri hatası mı) yapılamaz.
- **Önerilen düzeltme:** Kritik prosedürlerde beklenen hataları yakalayıp anlamlı
  `RAISE_APPLICATION_ERROR(-20xxx, ...)` kodlarına çevir; Java tarafında (framework G-3
  catch-all'ı ile birlikte) `-20xxx` aralığı iş hatasına eşlenir. Beklenmeyen hatalar ham
  yayılmaya devam eder (maskelenmez).
- **Sahiplik:** [APP]

## İyi Yapılanlar (kopyalanacak kalıplar)

Bu uygulamanın referans değeri taşıyan, **1000 ekibin aynen kopyalaması gereken** kalıpları:

| Kalıp | Nerede | Neden doğru |
|-------|--------|-------------|
| **Parametreler her zaman bind edilir** | `JdbcProductRepository` / `JpaProductRepository` (Map/positional param) + `procedures.sql` (statik SQL) | SQL injection yüzeyi yok; PL/SQL'de string birleştirme yok. |
| **Procedure adları sabit (constant)** | `JdbcProductRepository` (`PKG`, `CURSOR` sabitleri) | Identifier injection riski yok (zeus-fw doküman 11 / G-6 kuralının doğru uygulaması). |
| **`open-in-view=false`** | `application.properties` | Lazy-loading tuzağı ve gereksiz EntityManager ömrü yok. |
| **SP'lerde COMMIT yok** | `procedures.sql` | Transaction sahibi konteyner/Spring — JTA ile doğru bütünleşir. |
| **DTO sınırı** | `dto/` — `Product` dışarı sızmaz | API sözleşmesi ile iç model bağımsız evrilir. |
| **Interface üzerinden bağlanma + constructor injection** | controller/service/repository | Test edilebilirlik ve impl değiştirilebilirlik (jdbc↔jpa örneği). |
| **Deploy sonrası smoke test** | `scripts/smoke-test.sh` (doküman 14) | Her deploy davranış doğrulamasıyla kapanır; staging gate'e takılır. |

## Test Boşlukları

- Mevcut: yalnızca `contextLoads` (`SpringWildflyArchApplicationTests`, H2 ile) ve opsiyonel
  `RepositoryBenchmarkTest` (Oracle gerektirir, normalde skip).
- Eksik: Controller katmanı testi (MockMvc — validasyon 400'leri, 404 ProblemDetail gövdesi),
  servis birim testleri, güvenlik testleri (zeus-security sonrası: anonim istek 401 bekler).
  Smoke test (doküman 14) uçtan uca akışı kapatıyor ama build sırasında koşan hızlı geri
  bildirim katmanı yok. Referans uygulama olarak test kalıbı da kopyalanacaktır — MockMvc
  örneği eklemek 1000 ekibe test şablonu sağlar.
- **Sahiplik:** [APP]

## Framework'e Bağımlı Düzeltmeler (izlenebilirlik)

| Uygulama bulgusu | Bekleyen framework işi (zeus-fw doküman 11/12) |
|------------------|--------------------------------------------------|
| AG-1 (authn yok) | G-2 — `zeus-security` modülü + aggregator'a starter-security |
| AG-4'ün 500 davranışı | G-3 — GlobalExceptionHandler catch-all + `server.error.include-*` |
| AG-5 (Swagger açık) | G-5 — springdoc prod varsayılanı kapalı |
| AP-1 (sayfalama) | P-2 — AbstractCrudService sayfalı findAll |
| AP-4'ün hata eşlemesi | G-3 — DataAccessException handler'ında -20xxx eşleme noktası |

## Önceliklendirilmiş Backlog

| Faz | İş | Bulgu | Sahiplik |
|-----|-----|-------|----------|
| **Faz 1 (hemen)** | `show-sql`/`format_sql`'i ortak properties'ten local profile taşı | AG-3/AP-3 | [APP] |
| Faz 1 | `ProductRequest.name`'e `@Size(max=255)` | AG-4 | [APP] |
| Faz 1 | Lokal şifreleri placeholder/ortam değişkenine çevir; CLAUDE.md'den düz şifreyi çıkar; sır taraması ekle | AG-2 | [APP] |
| Faz 1 | `products_seq`'e `CACHE 20` | AP-2 | [APP] |
| **Faz 2 (kısa vade)** | `get_all`'a offset/limit + zincire page/size (P-2 framework desteğiyle birlikte referans örnek) | AP-1 | [APP + FW-bekleyen] |
| Faz 2 | MockMvc controller test şablonu (400/404 ProblemDetail doğrulamaları) | Test boşlukları | [APP] |
| Faz 2 | Prod profilde springdoc kapalı + smoke-test ilk adımının güncellenmesi | AG-5 | [APP + FW-bekleyen] |
| **Faz 3 (framework sonrası)** | `zeus-security` entegrasyon referans örneği (ilk uygulama bu olmalı) | AG-1 | [APP] |
| Faz 3 | Kritik SP'lere `-20xxx` hata eşlemesi (G-3 handler'ıyla birlikte) | AP-4 | [APP] |

## İlgili Dokümanlar

- `../../zeus-fw/gelistirmeler/11-guvenlik-analizi-best-practices.md` — framework güvenlik bulguları (G-x) + sorumluluk matrisi.
- `../../zeus-fw/gelistirmeler/12-performans-analizi-best-practices.md` — framework performans bulguları (P-x).
- `05-stored-procedure-veri-erisimi.md` — SP veri erişim kalıbı (yeni SP ekleme akışı).
- `12-repository-benchmark.md` — jdbc vs jpa executor ölçümü.
- `14-smoke-test.md` — `/v3/api-docs` bağımlılığı (AG-5 kararında güncellenecek).
- `13-zeus-framework-entegrasyonu.md` — framework'ten gelen bileşenlerin envanteri.
