package com.example.orderprocessor.service;

import com.example.orderprocessor.config.OrderProcessingProperties;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;
    private final OrderProcessingService orderProcessingService;
    private final OrderProcessingProperties orderProcessingProperties;

    public OrderEventListener(
            ObjectMapper objectMapper,
            OrderProcessingService orderProcessingService,
            OrderProcessingProperties orderProcessingProperties
    ) {
        this.objectMapper = objectMapper;
        this.orderProcessingService = orderProcessingService;
        this.orderProcessingProperties = orderProcessingProperties;
    }

    @RabbitListener(queues = "${app.rabbitmq.order-queue}")
    public void onOrderPlaced(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
            log.info("event=order_placed_received orderId={} maxRetries={} payload={}",
                    event.orderId(), orderProcessingProperties.maxRetries(), payload);

            orderProcessingService.processOrderPlacedEvent(event);
            channel.basicAck(deliveryTag, false);
            log.info("event=message_acked orderId={} deliveryTag={}", event.orderId(), deliveryTag);
        } catch (JsonProcessingException | PermanentProcessingException ex) {
            log.error("event=message_rejected_permanent deliveryTag={} error={}", deliveryTag, ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        } catch (TransientProcessingException ex) {
            log.error("event=message_requeued_transient deliveryTag={} error={}", deliveryTag, ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception ex) {
            log.error("event=message_requeued_unknown deliveryTag={} error={}", deliveryTag, ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
