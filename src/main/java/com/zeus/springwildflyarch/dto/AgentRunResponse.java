package com.zeus.springwildflyarch.dto;

import java.util.Map;

/**
 * Ajan koşusunun HTTP yanıtı.
 *
 * <p>{@code stopReason} ve {@code steps} bilinçli olarak dışarı verilir: "model bitirdi" ile
 * "bütçe doldu" aynı yanıta karışmamalı, çağıran durumu görebilmeli.
 */
public record AgentRunResponse(String answer, Map<String, String> workspace,
                               int steps, long tokens, long durationMs, String stopReason) {
}
