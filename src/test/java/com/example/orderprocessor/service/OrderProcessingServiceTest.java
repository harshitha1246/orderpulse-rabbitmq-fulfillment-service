package com.example.orderprocessor.service;

import com.example.orderprocessor.model.OrderEntity;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private OrderProcessingService orderProcessingService;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        orderProcessingService = new OrderProcessingService(orderRepository, orderEventPublisher, validator);
    }

    @Test
    void shouldProcessAndPublishForNewOrder() {
        OrderPlacedEvent event = new OrderPlacedEvent("order-1", "prod-1", 2, "cust-1", Instant.now());
        List<OrderStatus> savedStatuses = new ArrayList<>();

        when(orderRepository.findById("order-1")).thenReturn(Optional.empty());
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            savedStatuses.add(entity.getStatus());
            return entity;
        });

        boolean processed = orderProcessingService.processOrderPlacedEvent(event);

        assertThat(processed).isTrue();
        verify(orderEventPublisher, times(1)).publishProcessedEvent(any());
        verify(orderRepository, times(2)).save(any(OrderEntity.class));
        assertThat(savedStatuses).containsExactly(OrderStatus.PROCESSING, OrderStatus.PROCESSED);
    }

    @Test
    void shouldSkipAlreadyProcessedOrderForIdempotency() {
        OrderEntity existing = new OrderEntity();
        existing.setId("order-2");
        existing.setStatus(OrderStatus.PROCESSED);

        when(orderRepository.findById("order-2")).thenReturn(Optional.of(existing));

        boolean processed = orderProcessingService.processOrderPlacedEvent(
                new OrderPlacedEvent("order-2", "prod-1", 1, "cust-1", Instant.now())
        );

        assertThat(processed).isFalse();
        verify(orderRepository, times(0)).save(any(OrderEntity.class));
        verify(orderEventPublisher, times(0)).publishProcessedEvent(any());
    }

    @Test
    void shouldThrowPermanentExceptionForInvalidEvent() {
        OrderPlacedEvent invalid = new OrderPlacedEvent("", "prod-1", 0, "cust-1", Instant.now());

        assertThatThrownBy(() -> orderProcessingService.processOrderPlacedEvent(invalid))
                .isInstanceOf(PermanentProcessingException.class);
    }
}
