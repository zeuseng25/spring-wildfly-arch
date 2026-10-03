package com.zeus.springwildflyarch.ai;

import com.zeus.framework.ai.agent.AgentResult;
import com.zeus.framework.ai.agent.AgentSpec;
import com.zeus.framework.ai.agent.ZeusAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Katalog analiz ajanı — zeus-ai-agent'ın kanıt kullanımı.
 *
 * <p>Mimari süreklilik: ajan YENİ BİR VERİ YOLU AÇMAZ. Mevcut {@link ProductAiTools}'u kullanır,
 * yani veri yine {@code ProductRepository} üzerinden Oracle {@code PRODUCT_PKG} stored
 * procedure'lerinden gelir. Ajanın eklediği tek şey ÇOK ADIMLILIK: veriyi çeker, ara bulgularını
 * çalışma alanına yazar, sonunda raporu derler.
 */
@Component
@RequiredArgsConstructor
public class ProductAnalysisAgent {

    private static final String SISTEM_PROMPTU = """
            Sen bir katalog analiz ajanısın. Görevini adım adım yürüt:
            1. Önce listProducts ile katalogu çek.
            2. Bulgularını /bulgular.md dosyasına yaz (write_file) — uzun metni sohbete dökme.
            3. Son olarak kısa bir özet cevap ver.
            Yalnızca sana verilen araçlardan gelen veriyi kullan; ürün özelliği UYDURMA.
            """;

    private final ZeusAgent agent;
    private final ProductAiTools productAiTools;

    public AgentResult<String> analyze(String question) {
        return agent.run(question, AgentSpec.of(SISTEM_PROMPTU, productAiTools));
    }
}
