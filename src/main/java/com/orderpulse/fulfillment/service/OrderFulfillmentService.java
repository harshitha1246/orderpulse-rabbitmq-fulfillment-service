package com.orderpulse.fulfillment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.entity.Order;
import com.orderpulse.fulfillment.entity.OrderStatus;
import com.orderpulse.fulfillment.entity.ProcessedEvent;
import com.orderpulse.fulfillment.repository.OrderRepository;
import com.orderpulse.fulfillment.repository.ProcessedEventRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.order}")
    private String orderExchange;

    @Value("${rabbitmq.routing-key.order}")
    private String orderRoutingKey;

    /**
     * Publishes an order message to the RabbitMQ order queue.
     */
    public void publishOrder(OrderMessage orderMessage) {
        log.info("Publishing order message for orderId: {}", orderMessage.getOrderId());
        rabbitTemplate.convertAndSend(orderExchange, orderRoutingKey, orderMessage);
        log.info("Order message published successfully for orderId: {}", orderMessage.getOrderId());
    }

    /**
     * Processes an incoming order message with idempotency guarantee.
     * If the event has already been processed (identified by orderId), it is skipped.
     */
    @Transactional
    public void processOrder(OrderMessage orderMessage) {
        String orderId = orderMessage.getOrderId();

        if (processedEventRepository.existsByEventId(orderId)) {
            log.warn("Duplicate event detected. Skipping already-processed orderId: {}", orderId);
            return;
        }

        log.info("Processing order for orderId: {}", orderId);

        Order order = Order.builder()
                .orderId(orderId)
                .customerId(orderMessage.getCustomerId())
                .productId(orderMessage.getProductId())
                .quantity(orderMessage.getQuantity())
                .totalAmount(orderMessage.getTotalAmount())
                .status(OrderStatus.PROCESSING)
                .build();

        orderRepository.save(order);

        // Simulate fulfillment logic
        fulfillOrder(order);

        order.setStatus(OrderStatus.FULFILLED);
        orderRepository.save(order);

        // Record event as processed for idempotency
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(orderId)
                .build();
        processedEventRepository.save(processedEvent);

        log.info("Order fulfilled successfully for orderId: {}", orderId);
    }

    /**
     * Simulated fulfillment logic (e.g., inventory check, shipping, etc.).
     */
    private void fulfillOrder(Order order) {
        log.info("Fulfilling order: productId={}, quantity={}, customerId={}",
                order.getProductId(), order.getQuantity(), order.getCustomerId());
    }
}
