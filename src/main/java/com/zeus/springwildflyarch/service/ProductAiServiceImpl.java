package com.zeus.springwildflyarch.service;

import com.zeus.framework.ai.ZeusAiAssistant;
import com.zeus.springwildflyarch.ai.ProductAiTools;
import com.zeus.springwildflyarch.dto.ProductInsight;
import com.zeus.springwildflyarch.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * LLM destekli ürün işlemleri. Uygulama Spring AI API'sine DEĞİL, framework'ün
 * {@link ZeusAiAssistant} sözleşmesine bağlanır — sağlayıcı (vLLM/LiteLLM/OpenRouter)
 * ya da Spring AI sürümü değişse bu sınıf değişmez.
 *
 * <p>Üç desen gösterilir:
 * <ol>
 *   <li>{@link #describe} — veri prompt'a gömülür, düz metin yanıt alınır.</li>
 *   <li>{@link #analyze}  — yanıt doğrudan {@link ProductInsight} record'una bağlanır.</li>
 *   <li>{@link #askCatalog} — veri gömülmez; modele tool verilir, veriyi kendisi çeker.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ProductAiServiceImpl implements ProductAiService {

    private final ZeusAiAssistant ai;
    private final ProductService productService;
    private final ProductAiTools productAiTools;

    @Override
    public String describe(Long id) {
        // Ürün bulunamazsa ProductService ResourceNotFoundException fırlatır → 404 (zeus-base).
        ProductResponse product = productService.findById(id);
        return ai.ask("""
                Aşağıdaki ürün için 2-3 cümlelik, abartısız bir e-ticaret açıklaması yaz.
                Ürün adı: %s
                Fiyat: %s TL
                Stok: %d adet
                """.formatted(product.getName(), product.getPrice(), product.getStock()));
    }

    @Override
    public ProductInsight analyze(Long id) {
        ProductResponse product = productService.findById(id);
        return ai.askAs("""
                Aşağıdaki ürünü değerlendir ve istenen alanları doldur.
                Ürün adı: %s
                Fiyat: %s TL
                Stok: %d adet
                """.formatted(product.getName(), product.getPrice(), product.getStock()),
                ProductInsight.class);
    }

    @Override
    public String askCatalog(String question) {
        // Tool listesi verilir; hangi tool'un çağrılacağına model karar verir, döngüyü
        // Spring AI 2.0'ın ToolCallingAdvisor'ı yürütür (burada döngü kodu yoktur).
        return ai.ask(question, productAiTools);
    }
}
