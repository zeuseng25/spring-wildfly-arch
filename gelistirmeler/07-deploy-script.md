# 07 — Deploy Script (scripts/deploy.sh)

## Amaç
Build + deploy işlemini tek komutla, manuel çalıştırılabilir bir script ile yapmak.

## Konum
`scripts/deploy.sh` (proje kökü). WAR'a paketlenmez.

## Kullanım
```bash
./scripts/deploy.sh            # mvn clean package → WAR → WildFly'a deploy + marker bekle
./scripts/deploy.sh --no-build # build atla, mevcut target/*.war'ı deploy et
```

WildFly konumu override:
```bash
WILDFLY_HOME=/baska/wildfly ./scripts/deploy.sh
```

## Ne yapar
1. **Build:** `mvn clean package` (— `--no-build` verilmedikçe).
2. `target/*.war`'ı bulur (`.original` hariç).
3. WildFly `standalone/deployments/`'a eski marker'ları temizleyip `spring-wildfly-arch.war` adıyla kopyalar.
4. WildFly çalışıyorsa `.deployed` marker'ını bekler; `.failed` çıkarsa içeriğini gösterip hata döner. Çalışmıyorsa "sunucu açılınca deploy edilecek" der.

## Varsayılanlar
- `WILDFLY_HOME` = `/Users/omer/workspaces/intellij/wildfy-27/wildfly-27.0.1.Final`
- Deploy adı: `spring-wildfly-arch.war` → context `/spring-wildfly-arch`
- Başarı çıktısı: `✅ DEPLOY BAŞARILI: http://localhost:8080/spring-wildfly-arch/`

## İlgili
- Deployment detayları: `06-wildfly-deployment.md`
