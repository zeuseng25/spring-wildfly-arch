# 01 — Database: Oracle (Docker)

## Amaç
Uygulamanın veritabanı olarak Docker üzerinde çalışan Oracle kullanılır.

## Container
- **İsim:** `fw-batch-oracle`
- **Image:** `gvenzl/oracle-free:23-slim-faststart`
- **Port:** `1521` (host'a map'li)
- **CDB (SID):** `FREE` → default PDB **`FREEPDB1`**
- **SYS/SYSTEM şifresi:** `oracle`
- **Uygulama kullanıcı/şema:** `ABC` / şifre `ABC`

Başlatma:
```bash
docker start fw-batch-oracle
# health bekle:
docker inspect fw-batch-oracle --format '{{.State.Health.Status}}'
```

Bağlantı testi:
```bash
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1
```
JDBC URL: `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`

## Şema (schema.sql)
`src/main/resources/db/schema.sql` — `products` tablosu + `products_seq` sequence. Manuel uygulanır (Hibernate `ddl-auto=none`).

```sql
CREATE TABLE products (
    id     NUMBER(19)     NOT NULL,
    name   VARCHAR2(255)  NOT NULL,
    price  NUMBER(19, 2)  NOT NULL,
    stock  NUMBER(10)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
);
CREATE SEQUENCE products_seq START WITH 1 INCREMENT BY 1 NOCACHE;
```

Uygula:
```bash
docker exec -i fw-batch-oracle sqlplus -s ABC/ABC@//localhost:1521/FREEPDB1 < src/main/resources/db/schema.sql
```

## İlgili
- Stored procedure'ler: `05-stored-procedure-veri-erisimi.md`
- WildFly bağlantısı (JNDI datasource): `03-wildfly-datasource.md`
