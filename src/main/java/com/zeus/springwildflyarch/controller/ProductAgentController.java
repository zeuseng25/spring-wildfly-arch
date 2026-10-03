package com.zeus.springwildflyarch.controller;

import com.zeus.framework.ai.agent.AgentResult;
import com.zeus.springwildflyarch.ai.ProductAnalysisAgent;
import com.zeus.springwildflyarch.dto.AgentRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Çok adımlı ajan koşusunu tetikleyen uç. Sadece HTTP; iş mantığı ajandadır. */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class ProductAgentController {

    private final ProductAnalysisAgent productAnalysisAgent;

    @GetMapping("/products/analysis")
    public AgentRunResponse analysis(
            @RequestParam(defaultValue = "Katalogdaki ürünleri fiyat ve stok açısından değerlendir.")
            String question) {

        AgentResult<String> r = productAnalysisAgent.analyze(question);
        return new AgentRunResponse(r.output(), r.workspace(),
                r.stats().steps(), r.stats().tokens(), r.stats().durationMs(),
                r.stats().stopReason().name());
    }
}
