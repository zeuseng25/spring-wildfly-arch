# 06 — WildFly Deployment (WAR)

## Amaç
Uygulama embedded sunucu yerine **WAR** olarak paketlenip WildFly 27'ye deploy edilir.

## WAR paketleme
- `pom.xml`: `<packaging>war</packaging>`.
- `spring-boot-starter-tomcat` **provided** scope (WildFly kendi servlet container'ını sağlar).
- `SpringWildflyArchApplication extends SpringBootServletInitializer` → WildFly WAR'ı başlatabilir.

## Logging fix (ÖNEMLİ)
Spring Boot Logback ile WildFly'ın `slf4j-jboss-logmanager`'ı çakışır:
```
LoggerFactory is not a Logback LoggerContext but Logback is on the classpath
```
Çözüm — `src/main/webapp/WEB-INF/jboss-deployment-structure.xml`:
```xml
<jboss-deployment-structure xmlns="urn:jboss:deployment-structure:1.3">
    <deployment>
        <exclude-subsystems>
            <subsystem name="logging"/>
        </exclude-subsystems>
        <exclusions>
            <module name="org.slf4j"/>
        </exclusions>
    </deployment>
</jboss-deployment-structure>
```
Bu sayede Spring Boot kendi Logback'ini kullanır. **Silme/değiştirme.**

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
