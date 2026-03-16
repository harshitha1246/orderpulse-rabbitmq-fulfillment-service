package com.example.orderprocessor.service;

import com.example.orderprocessor.model.OrderEntity;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderProcessedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final Validator validator;

    public OrderProcessingService(
            OrderRepository orderRepository,
            OrderEventPublisher orderEventPublisher,
            Validator validator
    ) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.validator = validator;
    }

    @Transactional
    public boolean processOrderPlacedEvent(OrderPlacedEvent event) {
        validateEvent(event);

        try {
            OrderEntity order = orderRepository.findById(event.orderId()).orElseGet(OrderEntity::new);

            if (OrderStatus.PROCESSED.equals(order.getStatus())) {
                log.info("event=idempotent_skip orderId={} reason=already_processed", event.orderId());
                return false;
            }

            order.setId(event.orderId());
            order.setProductId(event.productId());
            order.setCustomerId(event.customerId());
            order.setQuantity(event.quantity());
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
            log.info("event=order_status_updated orderId={} status={}", event.orderId(), OrderStatus.PROCESSING);

            OrderProcessedEvent processedEvent = new OrderProcessedEvent(
                    event.orderId(),
                    OrderStatus.PROCESSED.name(),
                    Instant.now()
            );
            orderEventPublisher.publishProcessedEvent(processedEvent);

            order.setStatus(OrderStatus.PROCESSED);
            orderRepository.save(order);
            log.info("event=order_status_updated orderId={} status={}", event.orderId(), OrderStatus.PROCESSED);
            return true;
        } catch (DataAccessException ex) {
            throw new TransientProcessingException("Database access failed", ex);
        }
    }

    private void validateEvent(OrderPlacedEvent event) {
        if (event == null) {
            throw new PermanentProcessingException("OrderPlacedEvent cannot be null");
        }

        Set<ConstraintViolation<OrderPlacedEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Invalid order placed event");
            throw new PermanentProcessingException(message);
        }
    }
}
