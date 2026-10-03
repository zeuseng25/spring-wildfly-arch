package com.zeus.springwildflyarch.dto;

import java.util.Map;

/**
 * Ajan koşusunun HTTP yanıtı.
 *
 * <p>{@code stopReason} ve {@code steps} bilinçli olarak dışarı verilir: "model bitirdi" ile
 * "bütçe doldu" aynı yanıta karışmamalı, çağıran durumu görebilmeli.
 *
 * <p>{@code promptTokens}/{@code completionTokens} ayrı alanlardır ({@code AgentRunStats} ile
 * aynı ayrım) — kurumsal maliyet raporlaması ikisini farklı fiyatlandırır; tek bir {@code tokens}
 * toplamına indirmek bu bilgiyi kaybeder.
 */
public record AgentRunResponse(String answer, Map<String, String> workspace,
                               int steps, long promptTokens, long completionTokens,
                               long durationMs, String stopReason) {
}
