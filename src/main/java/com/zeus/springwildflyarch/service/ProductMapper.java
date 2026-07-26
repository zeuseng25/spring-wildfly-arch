package com.zeus.springwildflyarch.service;

import com.zeus.framework.service.DtoMapper;
import com.zeus.springwildflyarch.dto.ProductRequest;
import com.zeus.springwildflyarch.dto.ProductResponse;
import com.zeus.springwildflyarch.model.Product;
import org.springframework.stereotype.Component;

/**
 * Product için DTO ↔ domain dönüşümü (framework {@link DtoMapper} sözleşmesi).
 */
@Component
public class ProductMapper implements DtoMapper<Product, ProductRequest, ProductResponse> {

    @Override
    public Product toEntity(ProductRequest request) {
        return Product.builder()
                .name(request.getName())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();
    }

    @Override
    public ProductResponse toResponse(Product entity) {
        return ProductResponse.from(entity);
    }
}
