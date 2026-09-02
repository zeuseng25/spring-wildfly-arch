# 02 — WildFly: Oracle JDBC Module

## Amaç

WildFly'ın Oracle'a bağlanabilmesi için Oracle JDBC sürücüsü (`ojdbc17`) bir WildFly
**module** olarak kurulur. Sürücü sunucu tarafında olduğundan WAR'a paketlenmez.

Bu module **paylaşımlı `com.zeus` module'ünden bağımsızdır** ve
`install-zeus-module.sh` tarafından üretilmez — elle kurulur/yükseltilir.

## Konum ve içerik

```
$WILDFLY_HOME/modules/com/oracle/ojdbc/main/
├── ojdbc17-23.26.3.0.0.jar
└── module.xml
```

`module.xml`:

```xml
<?xml version="1.0" ?>
<module xmlns="urn:jboss:module:1.1" name="com.oracle.ojdbc">
    <resources>
        <resource-root path="ojdbc17-23.26.3.0.0.jar"/>
    </resources>
    <dependencies>
        <module name="jakarta.transaction.api"/>
        <module name="java.se"/>
    </dependencies>
</module>
```

> `jakarta.transaction.api` XA/JTA için gereklidir (datasource JTA kullanır).
> `java.se` toplu bağımlılığı JDK modüllerini (java.sql, java.naming, java.management…)
> tek satırda karşılar.

## Kurulum / yükseltme

Sürüm **kök `zeus-fw/pom.xml`'deki `oracle-database.version`** ile hizalı olmalıdır
(bkz. aşağıdaki uyarı). WildFly kapalıyken:

```bash
WF=$WILDFLY_HOME; MD="$WF/modules/com/oracle/ojdbc/main"; V=23.26.3.0.0
BASE=https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc17/$V/ojdbc17-$V.jar
mkdir -p "$MD"
curl -sS -o "$MD/ojdbc17-$V.jar" "$BASE"
# bütünlük kontrolü — bozuk jar sessiz ClassFormatError üretir
diff <(curl -sS "$BASE.sha1" | tr -d ' \n') <(shasum -a 1 "$MD/ojdbc17-$V.jar" | awk '{print $1}')
# module.xml'deki resource-root path'ini yeni jar adına güncelle, eski jar'ı sil
```

## Doğrulama

WildFly açılış logunda:

```
WFLYJCA0018: Started Driver service with driver-name = oracle
WFLYJCA0001: Bound data source [java:jboss/datasources/OracleDS]
```

Canlı bağlantı testi:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="/subsystem=datasources/data-source=OracleDS:test-connection-in-pool"
# => "outcome" => "success", "result" => [true]
```

## ⚠️ Sürüm hizası — sessiz drift kaynağı

Uygulamaların derlendiği ojdbc sürümü **kök `zeus-fw/pom.xml`'deki
`oracle-database.version`** property'sinden gelir (`zeus-database` sürücüyü compile
scope'ta geçişli olarak getirir). Bu module elle kurulduğu için o property değiştiğinde
**otomatik güncellenmez** ve `verify-module-coverage.sh` da yakalamaz (ojdbc kasten
onun EXCLUDE_REGEX'indedir — `com.zeus` module'ünde olmaması doğru davranıştır).

Kontrol:

```bash
mvn dependency:tree -Dincludes=com.oracle.database.jdbc   # module'deki jar ile AYNI olmalı
```

Gerekçe ve prosedür: `../zeus-fw/gelistirmeler/17-module-yenileme-runbook.md`.

## Geçmiş

İlk kurulum WildFly 27 + `ojdbc11 23.4.0.24.05` ileydi ve module
`modules/system/layers/base/com/oracle/ojdbc/main/` altındaydı. WildFly 41 / Java 25
geçişinde `ojdbc17` hattına ve `modules/com/oracle/ojdbc/main/` yoluna taşındı
(bkz. `16-java25-boot4-wildfly41-yukseltme.md`).

## İlgili

- Bu module datasource'ta `<driver name="oracle" module="com.oracle.ojdbc">` olarak
  kullanılır: `03-wildfly-datasource.md`
- `WILDFLY_HOME` = `/Users/omer/workspaces/intellij/wildfly-41/wildfly-41.0.0.Final`
