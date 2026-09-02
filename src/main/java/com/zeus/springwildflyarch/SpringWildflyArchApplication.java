package com.zeus.springwildflyarch;

import com.zeus.framework.boot.ZeusServletInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Parent olarak {@link ZeusServletInitializer} kullanılır (Spring'in
 * {@code SpringBootServletInitializer}'ı değil): framework, WAR başlatılırken geçerli olması
 * gereken varsayılanları (ör. correlation ID log pattern'ı) buradan enjekte eder.
 * Gerekçe: {@code ../zeus-fw/gelistirmeler/18-correlation-id.md}.
 */
@SpringBootApplication
public class SpringWildflyArchApplication extends ZeusServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(SpringWildflyArchApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringWildflyArchApplication.class, args);
    }

}
