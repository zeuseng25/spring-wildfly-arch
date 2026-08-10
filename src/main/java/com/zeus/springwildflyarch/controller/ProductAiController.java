package com.zeus.springwildflyarch.controller;

import com.zeus.springwildflyarch.dto.AiAnswer;
import com.zeus.springwildflyarch.dto.CatalogQuestion;
import com.zeus.springwildflyarch.dto.ProductInsight;
import com.zeus.springwildflyarch.service.ProductAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM destekli uçlar. Sadece HTTP; iş mantığı {@link ProductAiService}'te.
 * Hatalar (ürün yok → 404, model erişilemedi → 502) framework handler'larından ProblemDetail olarak döner.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ProductAiController {

    private final ProductAiService productAiService;

    /** Düz metin üretim: ürün verisi prompt'a gömülür. */
    @GetMapping("/products/{id}/description")
    public AiAnswer describe(@PathVariable Long id) {
        return new AiAnswer(productAiService.describe(id));
    }

    /** Yapılandırılmış çıktı: yanıt doğrudan record'a bağlanır. */
    @GetMapping("/products/{id}/insight")
    public ProductInsight insight(@PathVariable Long id) {
        return productAiService.analyze(id);
    }

    /** Tool calling: model, stored procedure'lere bağlı tool'ları çağırarak cevaplar. */
    @PostMapping("/catalog/ask")
    public AiAnswer ask(@Valid @RequestBody CatalogQuestion request) {
        return new AiAnswer(productAiService.askCatalog(request.question()));
    }
}
