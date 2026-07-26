# 08 — com.zeus Module ile İnce WAR

## Amaç
WAR boyutunu düşürmek. Tüm 3. parti kütüphaneler (Spring, Spring Boot, Hibernate,
Jackson, ...) bir WildFly **module**'üne (`com.zeus`) konur; WAR'a paketlenmez.

| | Önce | Sonra |
|--|------|-------|
| WAR | **44 MB** (57 jar) | **~26 KB** (lib yok) |
| Kütüphaneler | WEB-INF/lib | `com.zeus` module (55 jar, ~43 MB, sunucuda bir kez) |

## Neden "sadece Spring" değil, tümü?
WildFly module classloader'ı WAR içindeki sınıfları **göremez**. Spring'i module'e
koyup Jackson'ı WAR'da bırakırsanız Spring Jackson'ı bulamaz (JSON kırılır). Spring'in
runtime'da dokunduğu her şey de module'de olmalı → pratikte tüm 3. parti kütüphaneler.

## Parçalar

### 1) Module kurulumu — `../zeus-fw/scripts/install-zeus-module.sh`
> Module **paylaşımlı ve platform'a aittir**; üretim scripti bu projede DEĞİL, framework'tedir.
> Script tek bir app'in değil, tüm uygulamaların 3. parti runtime bağımlılıklarının birleşimini
> tanımlayan `../zeus-fw/zeus-wildfly-module` aggregator modülüne karşı çözer.
> Detay: `../zeus-fw/gelistirmeler/08-wildfly-module-dagitim.md`.

- `mvn dependency:copy-dependencies` (runtime scope) ile jar'ları toplar (WAR'dan bağımsız).
- `jakarta.*-api`, `lombok`, `jarmode` ve `zeus-*` hariç hepsini `modules/com/zeus/main/`'e kopyalar.
- Her jar'a **Jandex** annotation index'i gömer (`jandex -m`).
- `module.xml` üretir.

```bash
( cd ../zeus-fw && ./scripts/install-zeus-module.sh )
# Hedef sunucu WILDFLY_HOME ile seçilir (staging/prod).
# module.xml değişirse WildFly RESTART gerekir (module tanımı cache'lenir).
```

### 2) module.xml bağımlılıkları (kritik)
```xml
<dependencies>
    <module name="java.se"/>
    <module name="org.jboss.vfs"/>      <!-- Spring classpath taraması JBoss VFS kullanır -->
    <module name="jdk.unsupported"/>    <!-- Objenesis/CGLIB sun.misc.Unsafe ister; java.se içermez -->
    <module name="jakarta.servlet.api"  export="true"/>
    <module name="jakarta.annotation.api" export="true"/>
    <module name="jakarta.persistence.api" export="true"/>
    <module name="jakarta.transaction.api" export="true"/>
    <module name="jakarta.validation.api" export="true"/>
    <module name="jakarta.inject.api"   export="true"/>
    <module name="jakarta.xml.bind.api" export="true"/>
    <module name="jakarta.activation.api" export="true"/>
</dependencies>
```
`jakarta.*-api`'ler module'e KONMAZ; WildFly server module'lerinden `export="true"` ile
verilir (çift sınıf / LinkageError önler, deployment annotation'ları görür).

### 3) WAR'dan lib'leri çıkarma — `pom.xml`
- `maven-war-plugin`: `<packagingExcludes>WEB-INF/lib/*.jar</packagingExcludes>`
- `spring-boot-maven-plugin`: `<skip>true</skip>` — repackage bağımlılıkları WEB-INF/lib'e
  geri eklediğinden kapatılır (WildFly'da executable WAR gerekmez).

### 4) Deployment bağlama — `WEB-INF/jboss-deployment-structure.xml` (FRAMEWORK ÜRETİR)

> **Bu dosya artık app repo'sunda tutulmaz.** Şablonu zeus-fw'dadır (`zeus-war-defaults`);
> zeus-parent'ın `zeus-generated-descriptor` profili WAR build'inde şablonu açar, `slot`
> değerini (`zeus.module.slot`, varsayılan `main`) doldurur ve WEB-INF'e koyar. App'te elle
> bir kopya kalsa bile üretilen dosya kazanır. İçerik değişikliği → şablon zeus-fw'da güncellenir.
> **`src/main/webapp` dizini silinmemeli** (profil bu dizinin varlığıyla aktifleşir; boş
> kaldığı için `.gitkeep` ile tutulur — silinirse deploy guard'ı net hatayla durdurur).
> Detay: `../zeus-fw/gelistirmeler/10-versiyonlu-slot-uretilen-descriptor.md`.

```xml
<module name="com.zeus" slot="${zeus.module.slot}" services="import" meta-inf="import" annotations="true"/>
```
- `services/meta-inf=import`: ServletContainerInitializer, spring.factories, JPA provider görünür.
- `annotations="true"`: module'deki Jandex index'leri composite index'e katar →
  `@HandlesTypes(WebApplicationInitializer)` taraması deployment sınıfını module'deki
  üst hiyerarşiye bağlar, DispatcherServlet kurulur.

Ek olarak kullanmadığımız ve `annotations="true"` ile tetiklenen subsystem'ler dışlanır:
```xml
<exclude-subsystems>
    <subsystem name="logging"/>
    <subsystem name="weld"/>         <!-- CDI: bean manager arar -->
    <subsystem name="batch-jberet"/> <!-- batch: bean manager arar -->
    <subsystem name="jsf"/>          <!-- spring-web JSF sınıfını link etmeye çalışır -->
    <subsystem name="jaxrs"/>
</exclude-subsystems>
```

## Karşılaşılan hatalar ve çözümleri (özet)
| Hata | Çözüm |
|------|-------|
| `No WebApplicationInitializer detected` | Jandex index + `annotations="true"` |
| `batch.artifact.factory missing beanmanager` | `weld` + `batch-jberet` dışla |
| `NoClassDefFound ...PhaseListener` (JSF) | `jsf` + `jaxrs` dışla |
| `Could not detect JBoss VFS` | module'e `org.jboss.vfs` |
| `Objenesis ... CGLIB proxy` | module'e `jdk.unsupported` |

> Module.xml her değiştiğinde WildFly restart şart (cache).

## Çalıştırma sırası
1. `( cd ../zeus-fw && ./scripts/install-zeus-module.sh )`  (paylaşımlı module — platform scripti)
2. WildFly başlat/yeniden başlat (`$WILDFLY_HOME/bin/standalone.sh`)
3. `./scripts/deploy.sh`  (bu uygulamanın ince WAR'ı)

## İlgili
- Deployment temeli: `06-wildfly-deployment.md`
- Deploy script: `07-deploy-script.md`
