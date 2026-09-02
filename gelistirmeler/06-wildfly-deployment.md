# 06 — WildFly Deployment (WAR)

## Amaç
Uygulama embedded sunucu yerine **WAR** olarak paketlenip WildFly 41'e deploy edilir.

## WAR paketleme
- `pom.xml`: `<packaging>war</packaging>`.
- `spring-boot-starter-tomcat` **provided** scope (WildFly kendi servlet container'ını sağlar).
- `SpringWildflyArchApplication extends SpringBootServletInitializer` → WildFly WAR'ı başlatabilir.

## Logging fix (ÖNEMLİ)
Spring Boot Logback ile WildFly'ın `slf4j-jboss-logmanager`'ı çakışır:
```
LoggerFactory is not a Logback LoggerContext but Logback is on the classpath
```
Çözüm, `WEB-INF/jboss-deployment-structure.xml`'de `logging` subsystem'ini dışlamaktır;
böylece Spring Boot kendi Logback'ini kullanır.

> **GÜNCEL DURUM — bu dosya artık app repo'sunda TUTULMAZ.** Framework build sırasında
> üretir: `zeus-war-defaults` şablonu + `zeus-parent`'ın `zeus-generated-descriptor`
> profili, `${zeus.module.slot}` doldurularak WAR'ın `WEB-INF`'ine konur. Uygulamada
> `src/main/webapp/WEB-INF/jboss-deployment-structure.xml` **oluşturma** — üretilen dosya
> onu ezer ve iki kaynak birbirinden ayrışır.
>
> Üretilen içerik `logging` dışlamasına ek olarak `weld`, `batch-jberet`, `jsf`, `jaxrs`
> subsystem'lerini de dışlar ve `com.zeus` module bağımlılığını ekler. Eski `org.slf4j`
> module dışlaması **kaldırıldı** — WildFly 41'de o module yok; slf4j/logback zaten
> `com.zeus` module'ünden geliyor.
>
> Detay: `../zeus-fw/gelistirmeler/10-versiyonlu-slot-uretilen-descriptor.md` ve
> `08-com-zeus-module.md`.

> **Güncelleme:** Bu dosya artık app repo'sunda durmuyor — framework build'de üretiyor
> (şablon: zeus-fw `zeus-war-defaults`; yukarıdaki dışlamalar şablonda aynen var).
> Bkz. `08-com-zeus-module.md` §4.

## Deploy
- WAR `standalone/deployments/spring-wildfly-arch.war` olarak kopyalanır (context root `/spring-wildfly-arch`).
- Deployment scanner `.deployed` marker'ı üretir; hata olursa `.failed`.
- Pratik kullanım: `scripts/deploy.sh` (bkz. `07-deploy-script.md`).

## Doğrulama
WildFly logunda:
```
Started SpringWildflyArchApplication ...
WFLYUT0021: Registered web context: '/spring-wildfly-arch'
WFLYSRV0010: Deployed "spring-wildfly-arch.war"
```
Uygulama: `http://localhost:8080/spring-wildfly-arch/api/products`

## Türkçe locale / metrics bug (deployment FAILED görünmesi)
Türkçe locale'li JVM'de (`user.country=TR`) WildFly `metrics` subsystem'i
`"milliseconds".toUpperCase()` -> `MİLLİSECONDS` (noktalı İ) enum hatasıyla çöker.
Metrics her deployment'a servis bağladığından, web context çalışsa bile **deployment
status=FAILED** görünür (console'da kırmızı).

Çözüm — `$WILDFLY_HOME/bin/standalone.conf` içinde JVM locale'ini sabitle:
```sh
JAVA_OPTS="$JAVA_OPTS -Duser.language=en -Duser.country=US"
```
Sonra WildFly restart → metrics düzgün başlar, `status=OK`.

## İlgili
- Deploy script: `07-deploy-script.md`
- Datasource: `03-wildfly-datasource.md`
