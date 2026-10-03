package com.zeus.springwildflyarch.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP yayın yüzeyinin kilidi.
 *
 * <p>Bu test, harici AI ajanlarına ne açtığımızı sabitler. {@code ProductAiTools}'un bir metodu
 * yeniden adlandırılır, kaldırılır ya da yenisi eklenirse burası kırmızıya döner — çünkü tool
 * adları artık iç bir detay değil, dışarıya yayınlanmış bir **API sözleşmesidir**.
 *
 * <p>Aynı sebeple yeni bir tool'un sessizce yayına girmesini de engeller: beklenen liste elle
 * güncellenmedikçe test geçmez.
 */
@SpringBootTest
class McpConfigTest {

    @Autowired
    private ToolCallbackProvider zeusMcpToolCallbackProvider;

    @Test
    void mcpYuzeyiTamOlarakIkiSaltOkunurToolDur() {
        List<String> adlar = Arrays.stream(zeusMcpToolCallbackProvider.getToolCallbacks())
                .map(cb -> cb.getToolDefinition().name())
                .sorted()
                .toList();

        // YAYIN YÜZEYİ. Bu listeyi değiştirmek, dışarıya açılan API'yi değiştirmektir:
        // v1 bilinçli olarak SALT OKUNURDUR (bkz. config/McpConfig javadoc'u ve
        // ../zeus-fw/gelistirmeler/23-zeus-ai-mcp.md → "Bilinen boşluk").
        assertThat(adlar).containsExactly("findProductById", "listProducts");
    }

    @Test
    void toolAciklamalariBosDegil() {
        // Açıklama bir yorum değil, arayüz sözleşmesidir: model tool'u bu metinle seçer.
        Arrays.stream(zeusMcpToolCallbackProvider.getToolCallbacks()).forEach(cb ->
                assertThat(cb.getToolDefinition().description())
                        .as("tool '%s' açıklaması", cb.getToolDefinition().name())
                        .isNotBlank());
    }
}
