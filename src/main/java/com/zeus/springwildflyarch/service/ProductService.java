package com.zeus.springwildflyarch.service;

import com.zeus.springwildflyarch.dto.ProductRequest;
import com.zeus.springwildflyarch.dto.ProductResponse;

import java.util.List;

/**
 * İş mantığı sözleşmesi. Controller sadece bu arayüze bağlı kalır.
 */
public interface ProductService {

    List<ProductResponse> findAll();

    ProductResponse findById(Long id);

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);
}
