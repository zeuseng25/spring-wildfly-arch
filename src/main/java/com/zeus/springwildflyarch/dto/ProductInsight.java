package com.zeus.springwildflyarch.dto;

import java.util.List;

/**
 * Yapılandırılmış çıktı (structured output) hedefi: modelin yanıtı serbest metin olarak değil,
 * doğrudan bu record'a bağlanarak döner. JSON şeması bu tipten türetilir ve prompt'a eklenir;
 * dönüştürme Spring AI tarafında yapılır (uygulamada JSON ayrıştırma kodu yoktur).
 *
 * @param summary          ürünün kısa pazarlama özeti
 * @param targetAudience   hedef kitle tanımı
 * @param keywords         arama/etiket için anahtar kelimeler
 * @param priceAssessment  fiyat/stok durumu üzerine kısa değerlendirme
 */
public record ProductInsight(
        String summary,
        String targetAudience,
        List<String> keywords,
        String priceAssessment
) {
}
