# 10 — Spring Profilleri & Lokal Çalıştırma

## Amaç
İki çalışma senaryosu: WAR olarak WildFly'a deploy (JNDI) ve lokal embedded Tomcat
(doğrudan JDBC). DataSource yapılandırması profillere ayrıldı.

## Profiller
| Profil | DataSource | Çalışma | Dosya |
|--------|-----------|---------|-------|
| `wildfly` (varsayılan) | JNDI `java:jboss/datasources/OracleDS` + JTA | WAR -> WildFly | `application-wildfly.properties` |
| `local` | Doğrudan JDBC `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` (ABC/ABC) | Embedded Tomcat, port **2525** | `application-local.properties` |

- `application.properties`: ortak ayarlar + `spring.profiles.active=wildfly` (varsayılan).
- Oracle dialect, `ddl-auto=none`, `app.repository.impl` ortak.
- DataSource neden profilde: `jndi-name` ile `url` aynı anda olamaz; profil ile ayrıştırıldı.

## Lokal çalıştırma (embedded Tomcat)
ojdbc `provided` scope ile lokal classpath'te (WAR'a/module'e girmez).

**Maven:**
```bash
./maven.sh spring-boot:run -Dspring-boot.run.profiles=local
```
`local` profili sabit port **2525** ve context path **/spring-wildfly-arch** kullanır
(WildFly 8080 ile çakışmaz, URL'ler WildFly ile aynı kalır; ekstra argüman gerekmez).
→ `http://localhost:2525/spring-wildfly-arch/api/products`, `.../swagger-ui/index.html`.

**IntelliJ (Run/Debug Configuration → Spring Boot):**
- Main class: `com.zeus.springwildflyarch.SpringWildflyArchApplication`
- Active profiles: `local`  (veya VM options: `-Dspring.profiles.active=local`)
- Ön koşul: Oracle container çalışıyor (`docker start fw-batch-oracle`).

> Not: `spring-boot:run` çalışsın diye `spring-boot-maven-plugin`'de skip yalnızca
> `repackage` execution'ında (plugin geneli değil). Bkz. `08-com-zeus-module.md`.

## IntelliJ "ClassNotFoundException" / yanlış classpath
Eğer çalıştırınca `ClassNotFoundException: ...SpringWildflyArchApplication` veya
classpath'te `~/.gradle/...` jar'ları görünüyorsa: IntelliJ bayat/Gradle modeli kullanıyordur.
Çözüm:
1. Maven panelinde **Reload** (veya `pom.xml` → *Add as Maven Project*).
2. Eski Spring Boot run configuration'ı sil (modülü `spring-wildfly-arch.main` olan).
3. **Build → Rebuild Project** (Maven `target/classes`'a derler).
4. Run config'i yeniden oluştur; Active profiles = `local`.

## İlgili
- WildFly deploy: `06-wildfly-deployment.md`
- Module: `08-com-zeus-module.md`
