# 11 — Lokal Çalıştırma (Embedded Tomcat)

WildFly'a deploy etmeden, uygulamayı bilgisayarında **embedded Tomcat** ile çalıştırma
rehberi. `local` profili doğrudan JDBC ile Docker'daki Oracle'a bağlanır (JNDI yok).

> Profil/yapılandırma detayı: `10-profiller-lokal-calistirma.md`.

---

## 1. Ön koşullar

**Oracle container açık olmalı:**
```bash
docker start fw-batch-oracle
# hazır mı kontrol:
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 <<< "SELECT 1 FROM dual;"
```
İlk kez ise tablo + procedure'ler uygulanmış olmalı:
```bash
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/schema.sql
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/procedures.sql
```

Java 17 ve `./maven.sh` yeterli. ojdbc sürücüsü `provided` scope ile lokal classpath'te (ekstra kurulum yok).

---

## 2. Çalıştırma — Maven (terminal)

```bash
./maven.sh spring-boot:run -Dspring-boot.run.profiles=local
```
`local` profili sabit **port 2525** kullanır (WildFly 8080 ile çakışmasın diye). Ekstra argüman gerekmez.

Başarılı başlangıçta logda şunlar görünür:
```
The following 1 profile is active: "local"
HikariPool-1 - Added connection oracle.jdbc.driver.T4CConnection@...
Tomcat started on port(s): 2525 (http) with context path '/spring-wildfly-arch'
Started SpringWildflyArchApplication in ...
```
Durdurmak için terminalde `Ctrl+C`.

---

## 3. Çalıştırma — IntelliJ

1. **Run/Debug Configurations → + → Spring Boot**
2. **Main class:** `com.zeus.springwildflyarch.SpringWildflyArchApplication`
3. **Active profiles:** `local`  (alternatif: VM options `-Dspring.profiles.active=local`)
4. **Run** ▶ — port otomatik 2525 (local profilinden).

> Oracle container'ın açık olduğundan emin ol.

---

## 4. Doğrulama

Lokal de WildFly ile **aynı** context path'i kullanır: `/spring-wildfly-arch` (port 2525).

| Ne | URL |
|----|-----|
| API | http://localhost:2525/spring-wildfly-arch/api/products |
| Swagger UI | http://localhost:2525/spring-wildfly-arch/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:2525/spring-wildfly-arch/v3/api-docs |

Hızlı test:
```bash
curl http://localhost:2525/spring-wildfly-arch/api/products
curl -X POST http://localhost:2525/spring-wildfly-arch/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","price":9.99,"stock":3}'
```

---

## 5. Sık karşılaşılan hatalar

| Belirti | Sebep / Çözüm |
|---------|---------------|
| `ClassNotFoundException: ...SpringWildflyArchApplication` veya classpath'te `~/.gradle/...` | IntelliJ bayat/Gradle modeli. Maven **Reload** → eski run config'i sil → **Rebuild** → yeni Spring Boot config (bkz. `10-...md`). |
| `Could not detect JBoss VFS` / JNDI hatası | `local` profili verilmemiş; `wildfly` profili (JNDI) lokalde çalışmaz. `-Dspring.profiles.active=local` ekle. |
| `IO Error: ... Connection refused` (1521) | Oracle container kapalı. `docker start fw-batch-oracle`. |
| `ORA-00942: table or view does not exist` | schema.sql / procedures.sql uygulanmamış (Ön koşullar). |
| `Port 2525 was already in use` | Önceki lokal instance kapanmamış. `lsof -ti :2525 \| xargs kill` ile durdur, tekrar başlat. |

---

## Lokal vs WildFly (özet)
| | Lokal (`local`) | WildFly (`wildfly`) |
|--|-----------------|---------------------|
| Sunucu | Embedded Tomcat (`spring-boot:run`) | WildFly 27 (WAR deploy) |
| DataSource | Doğrudan JDBC (ABC/ABC) | JNDI `OracleDS` + JTA |
| Port | `2525` | `8080` |
| Context path | `/spring-wildfly-arch` | `/spring-wildfly-arch` |
| ojdbc | provided (classpath'te) | WildFly module/datasource |

## İlgili
- Profiller: `10-profiller-lokal-calistirma.md`
- WildFly deploy: `06-wildfly-deployment.md`
