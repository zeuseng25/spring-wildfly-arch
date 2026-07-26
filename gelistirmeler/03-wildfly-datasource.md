# 03 — WildFly: Oracle Datasource (JNDI)

## Amaç
Uygulama veritabanına doğrudan değil, **WildFly'da tanımlı datasource** üzerinden (JNDI lookup) bağlanır. Connection pool ve sürücü WildFly tarafında yönetilir.

## Tanım (standalone.xml)
`$WILDFLY_HOME/standalone/configuration/standalone.xml` içinde `datasources` subsystem'ine eklenir:

```xml
<datasource jndi-name="java:jboss/datasources/OracleDS" pool-name="OracleDS" enabled="true" use-java-context="true">
    <connection-url>jdbc:oracle:thin:@//localhost:1521/FREEPDB1</connection-url>
    <driver>oracle</driver>
    <pool>
        <min-pool-size>2</min-pool-size>
        <max-pool-size>10</max-pool-size>
    </pool>
    <security>
        <user-name>ABC</user-name>
        <password>ABC</password>
    </security>
    <validation>
        <valid-connection-checker class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleValidConnectionChecker"/>
        <validate-on-match>true</validate-on-match>
        <exception-sorter class-name="org.jboss.jca.adapters.jdbc.extensions.oracle.OracleExceptionSorter"/>
    </validation>
</datasource>

<drivers>
    <driver name="oracle" module="com.oracle.ojdbc">
        <driver-class>oracle.jdbc.OracleDriver</driver-class>
        <xa-datasource-class>oracle.jdbc.xa.client.OracleXADataSource</xa-datasource-class>
    </driver>
</drivers>
```

## Uygulama tarafı bağlantı (datasource yönetimi tamamen framework'te)

Bu uygulamada **datasource ile ilgili hiçbir ayar/kod yoktur** — ne `spring.datasource.jndi-name`,
ne `@Bean`, ne JNDI adı. OracleDS/ReportingDS zeus-database'in kavramıdır (JNDI adları framework
varsayılanı). WildFly'a deploy edilince JNDI'nın varlığı otomatik algılanır (`OnZeusJndiCondition`).

Uygulama yalnız `StoredProcedureExecutors` inject edip **isimli getter** ile seçer:
```java
private final StoredProcedureExecutors sp;
sp.getOracleDs().query(...);    // OracleDS
sp.getReportDs().execute(...);  // ReportingDS
```
`application-wildfly.properties` yalnız JTA ayarını taşır; datasource satırı içermez. Detay + gerekçe:
`../zeus-fw/gelistirmeler/03-zeus-database.md` (Datasource'lar — tamamen framework-yönetimli).

> `ReportingDS` da `standalone.xml`'de tanımlıdır (OracleDS'in klonu; örnek olsun diye şimdilik aynı
> Oracle şemasına bağlanır). Yeni bir **standart** datasource: önce `standalone.xml`'e `<datasource>`,
> sonra zeus-database'e JNDI adı varsayılanı + `StoredProcedureExecutors`'a yeni getter. App kodu değişmez.

## Doğrulama
WildFly logunda **her iki** datasource bind olmalı:
```
WFLYJCA0001: Bound data source [java:jboss/datasources/OracleDS]
WFLYJCA0001: Bound data source [java:jboss/datasources/ReportingDS]
```

## İlgili
- Oracle module: `02-wildfly-oracle-module.md`
- Deployment: `06-wildfly-deployment.md`
