package com.zeus.springwildflyarch.service;

import com.zeus.springwildflyarch.dto.ProductInsight;

/**
 * Ürünlerle ilgili LLM destekli işlemlerin sözleşmesi.
 * Controller yalnızca bu arayüze bağlıdır (katmanlı mimari kuralı).
 */
public interface ProductAiService {

    /** Ürün için serbest metin pazarlama açıklaması üretir. */
    String describe(Long id);

    /** Ürün için yapılandırılmış analiz üretir (yanıt doğrudan record'a bağlanır). */
    ProductInsight analyze(Long id);

    /**
     * Katalog hakkında serbest bir soruyu cevaplar. Ürün verisini prompt'a gömmek yerine
     * modele tool verilir; model gerekli veriyi kendisi çağırarak alır.
     */
    String askCatalog(String question);
}
