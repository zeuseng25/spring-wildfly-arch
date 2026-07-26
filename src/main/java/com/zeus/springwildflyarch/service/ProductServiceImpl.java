package com.zeus.springwildflyarch.service;

import com.zeus.framework.service.AbstractCrudService;
import com.zeus.framework.service.DtoMapper;
import com.zeus.springwildflyarch.dto.ProductRequest;
import com.zeus.springwildflyarch.dto.ProductResponse;
import com.zeus.springwildflyarch.model.Product;
import com.zeus.springwildflyarch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Product iş kuralları. Ortak CRUD akışı (transaction, DTO dönüşümü, not-found → 404)
 * framework'ün {@link AbstractCrudService}'inden gelir; burada yalnızca veri erişim
 * kancaları repository'ye bağlanır.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl
        extends AbstractCrudService<Product, Long, ProductRequest, ProductResponse>
        implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    protected DtoMapper<Product, ProductRequest, ProductResponse> mapper() {
        return productMapper;
    }

    @Override
    protected List<Product> doFindAll() {
        return productRepository.findAll();
    }

    @Override
    protected Optional<Product> doFindById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    protected Product doCreate(Product entity) {
        return productRepository.create(entity);
    }

    @Override
    protected Optional<Product> doUpdate(Long id, Product entity) {
        if (productRepository.update(id, entity)) {
            entity.setId(id);
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Override
    protected boolean doDelete(Long id) {
        return productRepository.delete(id);
    }

    @Override
    protected String notFoundMessage(Long id) {
        return "Product bulunamadı: id=" + id;
    }
}
