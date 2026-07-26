package com.zeus.springwildflyarch.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * İstemciden gelen veri (create/update). Domain'den ayrı tutulur ki
 * API sözleşmesi ile iç model birbirinden bağımsız evrilebilsin.
 */
@Data
public class ProductRequest {

    @NotBlank(message = "name boş olamaz")
    private String name;

    @NotNull(message = "price zorunlu")
    @Min(value = 0, message = "price negatif olamaz")
    private BigDecimal price;

    @Min(value = 0, message = "stock negatif olamaz")
    private int stock;
}
