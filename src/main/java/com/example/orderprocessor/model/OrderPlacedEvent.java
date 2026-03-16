package com.example.orderprocessor.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record OrderPlacedEvent(
        @NotBlank String orderId,
        @NotBlank String productId,
        @Min(1) int quantity,
        @NotBlank String customerId,
        Instant timestamp
) {
}
