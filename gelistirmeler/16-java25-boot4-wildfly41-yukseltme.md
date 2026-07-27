# 16 — Java 25 + Spring Boot 4.0.7 + WildFly 41 Yükseltmesi (uygulama)

Uygulama, Zeus **2.0.0** platformuna (Java 25 + Spring Boot 4.0.7 + WildFly 41) yükseltildi.
Platform tarafındaki değişiklikler ve gerekçeler:
`../../zeus-fw/gelistirmeler/13-java25-boot4-wildfly41-yukseltme.md`.
Bu doküman, zeus 2.x'e geçecek **her uygulamanın yapacağı değişikliklerin** referans örneğidir.

## pom.xml değişiklikleri

| Eski | Yeni | Neden |
|------|------|-------|
| parent `zeus-parent:1.0.0-SNAPSHOT` | **`2.0.0-SNAPSHOT`** | Kırıcı platform sürümü |
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** | Boot 4 starter modülerleşmesi |
| `ojdbc11` (provided) | **`ojdbc17`** (provided) | JDK 25 uyumlu Oracle sürücü satırı (sürüm BOM'dan) |
| `spring-boot-starter-tomcat` (provided) | AYNI | Boot 4'te de gerekli — webmvc tomcat çeker; provided WAR/module dışı tutar, `spring-boot:run` görür |
| — | (geçici `spring-boot-properties-migrator`) | Yükseltme sırasında eklendi, rapor TEMİZ çıkınca kaldırıldı |

Kaynak kodda **sıfır değişiklik** gerekti: Controller/Service/Repository/Entity/DTO/
OpenApiConfig/SpringBootServletInitializer Boot 4'te aynen derlendi ve çalıştı.
Testler de değişmedi (contextLoads H2 ile yeşil).

## Property değişiklikleri (sil-önce stratejisi — hepsi başarılı)

1. **`application-wildfly.properties`:** `hibernate.transaction.coordinator_class=jta` ve
   `hibernate.transaction.jta.platform=...JBossAppServerJtaPlatform` satırları SİLİNDİ.
   Hibernate 7 + Boot 4, WildFly'da JTA platformunu otomatik bağlıyor — deploy log'unda:
   `HHH000490: Using JTA platform [org.springframework.boot.hibernate.SpringJtaPlatform]`.
   Sorun çıkarsa geri dönüş merdiveni: önce `spring.jpa.properties.hibernate.transaction.jta.platform=JBossAS`
   (kısa ad), o da olmazsa Hibernate 7 FQCN'i doğrulanıp geri konur.
2. **`application.properties`:** `spring.jpa.database-platform=OracleDialect` SİLİNDİ —
   Hibernate 7 dialect'i bağlantı metadata'sından algılıyor (hem Oracle hem H2 testte doğrulandı).
3. **`src/test/resources/application.properties`:** `H2Dialect` satırı SİLİNDİ (aynı gerekçe).
4. `spring-boot-properties-migrator` hiçbir eski/yeniden-adlandırılmış property raporlamadı —
   kalan tüm property'ler Boot 4'te geçerli.

## WildFly 41 ortam kurulumu (WF27'den kopyalanmadı, sıfırdan)

WF27→41 on dört sürümlük sıçrama olduğundan `standalone.xml` taşınmadı; temiz kurulum yapıldı:

```bash
# 1) Kurulum: /Users/omer/workspaces/intellij/wildfly-41/wildfly-41.0.0.Final (EE 11 dağıtımı)
# 2) Admin:  $WILDFLY_HOME/bin/add-user.sh -u admin -p 'Wildfly@123'
# 3) Sürücü modülü (ad AYNI kaldı → uygulama wiring'i değişmedi):
jboss-cli.sh --connect "module add --name=com.oracle.ojdbc \
  --resources=ojdbc17-23.9.0.25.07.jar --dependencies=jakarta.transaction.api,java.se"
# 4) Driver + datasource'lar:
/subsystem=datasources/jdbc-driver=oracle:add(driver-name=oracle,driver-module-name=com.oracle.ojdbc,...)
data-source add --name=OracleDS    --jndi-name=java:jboss/datasources/OracleDS    ... ABC/ABC @FREEPDB1
data-source add --name=ReportingDS --jndi-name=java:jboss/datasources/ReportingDS ... (min 2 / max 10)
# 5) Doğrulama: :test-connection-in-pool → her iki DS için true
```

- WF41, JDK 25 ile çalışıyor (başlangıç ~1.2 sn). **WF27 kurulumu rollback için aynen duruyor.**
- `scripts/deploy.sh` varsayılan `WILDFLY_HOME`'u WF41'e güncellendi.

## Doğrulama sonuçları

| Adım | Sonuç |
|------|-------|
| `mvn test` (contextLoads, H2 + Hibernate 7 otomatik dialect) | ✅ |
| İnce WAR: yalnız 4 × zeus-2.0.0 jar, ~51 KB; descriptor üretildi | ✅ |
| `verify-module-coverage.sh` | ✅ tam kapsam |
| `deploy.sh` → WF41 + `smoke-test.sh` (**jdbc** impl) | ✅ 9/9 |
| Aynı akış **jpa** impl ile (`app.repository.impl=jpa`) | ✅ 9/9 |
| Lokal profil: `mvn spring-boot:run` → Java 25 Tomcat, port 2525, ojdbc17 ile CRUD | ✅ |
| server.log: ERROR/LinkageError/Jackson çakışması | ✅ 0 |

Opsiyonel `RepositoryBenchmarkTest` (`-Dbenchmark=true`) bu yükseltmede koşulmadı; jdbc/jpa
karşılaştırmasını Boot 4 üzerinde yenilemek istenirse doküman 12'deki akış aynen geçerli.

## İlgili Dokümanlar

- `../../zeus-fw/gelistirmeler/13-java25-boot4-wildfly41-yukseltme.md` — platform tarafı (starter tablosu, module değişiklikleri).
- `03-wildfly-datasource.md` — datasource kavramları (WF41'de jboss-cli ile yeniden kuruldu).
- `10-profiller-lokal-calistirma.md` — profil ayrımı (değişmedi; JTA satırları artık yok).
- `14-smoke-test.md` — doğrulama geçidi (değişmeden WF41'de çalıştı).
