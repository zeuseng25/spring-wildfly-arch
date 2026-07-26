package com.zeus.springwildflyarch.dto;

import com.zeus.springwildflyarch.model.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * İstemciye dönen veri. Domain'i dışarı sızdırmamak için ayrı tutulur.
 */
@Data
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private BigDecimal price;
    private int stock;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    }
}
