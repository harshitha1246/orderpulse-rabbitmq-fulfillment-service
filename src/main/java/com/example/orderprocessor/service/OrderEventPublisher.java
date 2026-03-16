package com.example.orderprocessor.service;

import com.example.orderprocessor.config.RabbitMQProperties;
import com.example.orderprocessor.model.OrderProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, RabbitMQProperties rabbitMQProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQProperties = rabbitMQProperties;
    }

    public void publishProcessedEvent(OrderProcessedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMQProperties.orderExchange(),
                    rabbitMQProperties.processedRoutingKey(),
                    event
            );
            log.info("event=order_processed_published orderId={} status={}", event.orderId(), event.status());
        } catch (AmqpException ex) {
            throw new TransientProcessingException("Failed to publish order.processed event", ex);
        }
    }
}
