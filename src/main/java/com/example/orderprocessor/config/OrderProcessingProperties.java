package com.example.orderprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.order")
public record OrderProcessingProperties(int maxRetries) {
}
