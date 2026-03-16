package com.example.orderprocessor.model;

import java.time.Instant;

public record OrderProcessedEvent(
        String orderId,
        String status,
        Instant processedAt
) {
}
