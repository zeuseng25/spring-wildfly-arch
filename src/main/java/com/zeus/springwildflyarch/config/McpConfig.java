package com.zeus.springwildflyarch.config;

import com.zeus.framework.ai.mcp.ZeusMcpTools;
import com.zeus.springwildflyarch.ai.ProductAiTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP (Model Context Protocol) yayın yüzeyi.
 *
 * <p>Harici bir AI ajanının (Claude, kurumsal chat uygulaması) çağırabileceği tool'lar YALNIZCA
 * aşağıdaki bean'de sayılanlardır. Classpath taraması yoktur: bir {@code @Tool} metodu yazmak onu
 * dışarı açmaz; açan şey bu dosyadaki satırdır. "Bu uygulama dışarı ne açıyor?" sorusunun cevabı
 * tek yerdedir ve code review burada yapılır.
 *
 * <p><b>Yeni veri yolu açılmaz.</b> Yayınlanan {@link ProductAiTools}, {@code ZeusAiAssistant}'a
 * verilen nesnenin AYNISIDIR; veri yine {@code ProductRepository} üzerinden Oracle
 * {@code PRODUCT_PKG} stored procedure'lerinden gelir (jdbc/jpa impl seçimi aynen geçerli).
 * Model veritabanına doğrudan erişmez, SQL üretmez, DB kimliği görmez.
 *
 * <p><b>v1 kapsamı salt okunurdur.</b> {@code ProductAiTools}'un iki metodu da okuma yapar.
 * Yazma yapan bir tool eklenecekse önce yetkilendirme gerekir: bugünkü koruma yalnızca paylaşılan
 * sırdır (bir kimlik değil, taşıyıcı yetki) ve tool bazlı yetki yoktur.
 * Bkz. {@code ../zeus-fw/gelistirmeler/23-zeus-ai-mcp.md} → "Bilinen boşluk".
 */
@Configuration
public class McpConfig {

    @Bean
    ZeusMcpTools zeusMcpTools(ProductAiTools productAiTools) {
        return ZeusMcpTools.of(productAiTools);
    }
}
