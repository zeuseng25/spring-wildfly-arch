package com.zeus.springwildflyarch.dto;

/**
 * Serbest metin LLM yanıtının dış sözleşmesi. Düz String döndürmek yerine sarmalanır:
 * ileride kaynak/token gibi alanlar eklenirse istemci sözleşmesi kırılmasın.
 */
public record AiAnswer(String answer) {
}
