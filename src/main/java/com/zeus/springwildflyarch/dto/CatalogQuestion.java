package com.zeus.springwildflyarch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Katalog sorusu girişi. Doğrulama hataları zeus-base GlobalExceptionHandler
 * tarafından ProblemDetail (400) olarak döner.
 */
public record CatalogQuestion(
        @NotBlank(message = "Soru boş olamaz")
        @Size(max = 500, message = "Soru en fazla 500 karakter olabilir")
        String question
) {
}
