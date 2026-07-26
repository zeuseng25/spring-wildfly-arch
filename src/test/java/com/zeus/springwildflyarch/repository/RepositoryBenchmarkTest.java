package com.zeus.springwildflyarch.repository;

import com.zeus.framework.database.sp.JdbcStoredProcedureExecutor;
import com.zeus.framework.database.sp.StoredProcedureExecutors;
import com.zeus.springwildflyarch.model.Product;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Basit zaman ölçer benchmark — JDBC (SimpleJdbcCall) vs JPA (StoredProcedureQuery)
 * implementasyonlarını AYNI Oracle stored procedure'leri üzerinden karşılaştırır.
 *
 * Çalıştırma (Oracle container açık olmalı):
 *   ./mvnw test -Dtest=RepositoryBenchmarkTest -Dbenchmark=true
 *
 * Normal `mvn test` (H2) bunu ATLAR (-Dbenchmark=true yoksa @Disabled gibi davranır).
 * app.repository.impl=jpa verilir ki JPA bean'i aktif olsun; JDBC impl'i ise
 * DataSource'tan elle kurulur — böylece ikisi tek context'te yan yana ölçülür.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "app.repository.impl=jpa",
        "spring.jpa.show-sql=false",
        // src/test/resources/application.properties H2 dialect'i zorluyor; Oracle'a çek.
        "spring.jpa.database-platform=org.hibernate.dialect.OracleDialect",
        // KRİTİK: test props create-drop ayarlı; Oracle'da gerçek tabloyu DROP etmesin!
        "spring.jpa.hibernate.ddl-auto=none"
})
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryBenchmarkTest {

    /** findAll farkının görünmesi için eklenen geçici kayıt sayısı. */
    private static final int SEED_ROWS = 1000;
    private static final int WARMUP = 50;
    private static final int MEASURE = 200;

    @Autowired
    private DataSource dataSource;

    /** app.repository.impl=jpa olduğu için aktif bean JpaProductRepository. */
    @Autowired
    private ProductRepository jpaRepo;

    @Autowired
    private PlatformTransactionManager txManager;

    /** JDBC impl'i conditional'a takılmadan elle kuruluyor. */
    private ProductRepository jdbcRepo;
    private TransactionTemplate txTemplate;

    private static Long sampleId;

    @BeforeEach
    void setUpJdbc() {
        jdbcRepo = new JdbcProductRepository(new StoredProcedureExecutors(() -> new JdbcStoredProcedureExecutor(dataSource), () -> new JdbcStoredProcedureExecutor(dataSource)));
        txTemplate = new TransactionTemplate(txManager);
    }

    @BeforeAll
    void seed() {
        // @BeforeEach'ten önce çalışır; kendi JDBC impl'ini kurar.
        ProductRepository seeder = new JdbcProductRepository(new StoredProcedureExecutors(() -> new JdbcStoredProcedureExecutor(dataSource), () -> new JdbcStoredProcedureExecutor(dataSource)));
        for (int i = 0; i < SEED_ROWS; i++) {
            Product p = Product.builder()
                    .name("BENCH_" + i)
                    .price(new BigDecimal("9.99"))
                    .stock(i)
                    .build();
            Product saved = seeder.create(p);
            if (i == SEED_ROWS / 2) {
                sampleId = saved.getId();
            }
        }
    }

    @AfterAll
    void cleanup() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            // Hikari autocommit açık; DELETE kendiliğinden commit olur.
            st.executeUpdate("DELETE FROM products WHERE name LIKE 'BENCH_%'");
        }
    }

    @Test
    void benchmark() {
        int rows = runInTx(() -> jdbcRepo.findAll().size());
        System.out.println("\n==== Repository Benchmark (rows in table ~ " + rows + ") ====");

        bench("findAll", () -> jdbcRepo.findAll(), () -> jpaRepo.findAll());

        final Long id = sampleId;
        bench("findById", () -> jdbcRepo.findById(id), () -> jpaRepo.findById(id));

        System.out.println("=========================================================\n");
    }

    private void bench(String label, Runnable jdbc, Runnable jpa) {
        // Her çağrı kendi transaction'ında: üretimdeki "istek başına 1 tx" yaşam
        // döngüsünü taklit eder ve JPA REF_CURSOR'larının her çağrıda kapanmasını sağlar.
        for (int i = 0; i < WARMUP; i++) { runInTx(jdbc); runInTx(jpa); }

        double jdbcUs = timeAvgMicros(jdbc);
        double jpaUs  = timeAvgMicros(jpa);

        System.out.printf("%-10s | JDBC %9.1f us/op | JPA %9.1f us/op | JPA/JDBC = %.2fx%n",
                label, jdbcUs, jpaUs, jpaUs / jdbcUs);
    }

    private double timeAvgMicros(Runnable op) {
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE; i++) {
            runInTx(op);
        }
        long elapsed = System.nanoTime() - start;
        return elapsed / (double) MEASURE / 1000.0;
    }

    private void runInTx(Runnable r) {
        txTemplate.executeWithoutResult(s -> r.run());
    }

    private <T> T runInTx(java.util.function.Supplier<T> r) {
        return txTemplate.execute(s -> r.get());
    }
}