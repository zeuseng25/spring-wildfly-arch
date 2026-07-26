package com.zeus.springwildflyarch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata. Swagger UI ve /v3/api-docs bu bilgileri gösterir.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Spring WildFly Arch API")
                .description("Ürün yönetimi REST API — stored procedure tabanlı veri erişimi")
                .version("v1")
                .contact(new Contact().name("zeus")));
    }
}
