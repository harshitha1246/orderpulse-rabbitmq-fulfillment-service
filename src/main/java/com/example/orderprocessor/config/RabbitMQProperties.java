package com.example.orderprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public record RabbitMQProperties(
        String orderExchange,
        String orderQueue,
        String orderRoutingKey,
        String processedRoutingKey,
        String dlxExchange,
        String dlqQueue
) {
}
