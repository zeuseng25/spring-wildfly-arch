# 02 — WildFly: Oracle JDBC Module

## Amaç
WildFly'ın Oracle'a bağlanabilmesi için Oracle JDBC sürücüsü (`ojdbc11`) bir WildFly **module** olarak kurulur. Sürücü WildFly tarafında olduğundan WAR'a paketlenmez.

## Kurulum

1. Sürücüyü Maven'den indir:
```bash
mvn dependency:get -Dartifact=com.oracle.database.jdbc:ojdbc11:23.4.0.24.05
```

2. Module dizinini oluştur ve jar'ı kopyala:
```
$WILDFLY_HOME/modules/system/layers/base/com/oracle/ojdbc/main/
├── ojdbc11-23.4.0.24.05.jar
└── module.xml
```

3. `module.xml`:
```xml
<module name="com.oracle.ojdbc" xmlns="urn:jboss:module:1.9">
    <resources>
        <resource-root path="ojdbc11-23.4.0.24.05.jar"/>
    </resources>
    <dependencies>
        <module name="java.logging"/>
        <module name="java.management"/>
        <module name="java.naming"/>
        <module name="java.se"/>
        <module name="java.security.sasl"/>
        <module name="java.sql"/>
        <module name="java.transaction.xa"/>
        <module name="java.xml"/>
    </dependencies>
</module>
```

## Doğrulama
WildFly başlangıç logunda:
```
WFLYJCA0004: Deploying JDBC-compliant driver class oracle.jdbc.OracleDriver (version 23.4)
WFLYJCA0018: Started Driver service with driver-name = oracle
```

`WILDFLY_HOME = /Users/omer/workspaces/intellij/wildfy-27/wildfly-27.0.1.Final`

## İlgili
- Bu module datasource'ta `<driver>oracle</driver>` olarak kullanılır: `03-wildfly-datasource.md`
