package com.zeus.springwildflyarch.ai;

import com.zeus.springwildflyarch.dto.ProductResponse;
import com.zeus.springwildflyarch.model.Product;
import com.zeus.springwildflyarch.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Modele açılan araçlar (tool). Model bir soruyu cevaplamak için veriye ihtiyaç duyduğunda
 * bu metotları çağırır; Spring AI 2.0'ın {@code ToolCallingAdvisor}'ı çağrı döngüsünü yürütür.
 *
 * <p>ÖNEMLİ — mimari süreklilik: burada yeni bir veri yolu AÇILMAZ. Araçlar mevcut
 * {@link ProductRepository} arayüzünü çağırır, yani veri yine Oracle {@code PRODUCT_PKG}
 * stored procedure'lerinden gelir (jdbc/jpa impl seçimi aynen geçerlidir). Model veritabanına
 * doğrudan erişmez; yalnızca uygulamanın izin verdiği metotları çağırabilir.
 *
 * <p>Dışarıya {@link Product} değil {@link ProductResponse} verilir — DTO kuralı model için de geçerli.
 */
@Component
@RequiredArgsConstructor
public class ProductAiTools {

    private static final Logger log = LoggerFactory.getLogger(ProductAiTools.class);

    private final ProductRepository productRepository;

    @Tool(description = "Katalogdaki tüm ürünleri id, ad, fiyat ve stok bilgisiyle listeler.")
    public List<ProductResponse> listProducts() {
        List<ProductResponse> products = productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
        log.info("AI tool çağrıldı: listProducts -> {} kayıt", products.size());
        return products;
    }

    @Tool(description = "Verilen id'ye sahip ürünü döner; ürün yoksa null döner.")
    public ProductResponse findProductById(
            @ToolParam(description = "Ürünün sayısal id'si") Long id) {
        log.info("AI tool çağrıldı: findProductById({})", id);
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElse(null);
    }
}
