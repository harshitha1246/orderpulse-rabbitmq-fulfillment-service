package com.orderpulse.fulfillment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.entity.Order;
import com.orderpulse.fulfillment.entity.OrderStatus;
import com.orderpulse.fulfillment.entity.ProcessedEvent;
import com.orderpulse.fulfillment.repository.OrderRepository;
import com.orderpulse.fulfillment.repository.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderFulfillmentService orderFulfillmentService;

    private OrderMessage orderMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderFulfillmentService, "orderExchange", "order.exchange");
        ReflectionTestUtils.setField(orderFulfillmentService, "orderRoutingKey", "order.routingkey");

        orderMessage = OrderMessage.builder()
                .orderId("ORDER-001")
                .customerId("CUSTOMER-001")
                .productId("PRODUCT-001")
                .quantity(2)
                .totalAmount(new BigDecimal("99.99"))
                .build();
    }

    @Test
    void publishOrder_shouldSendMessageToExchange() {
        orderFulfillmentService.publishOrder(orderMessage);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("order.exchange"),
                eq("order.routingkey"),
                eq(orderMessage)
        );
    }

    @Test
    void processOrder_newOrder_shouldSaveOrderAndMarkProcessed() {
        when(processedEventRepository.existsByEventId("ORDER-001")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderFulfillmentService.processOrder(orderMessage);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getAllValues().get(1);
        assertThat(savedOrder.getOrderId()).isEqualTo("ORDER-001");
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FULFILLED);

        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("ORDER-001");
    }

    @Test
    void processOrder_duplicateOrder_shouldSkipProcessing() {
        when(processedEventRepository.existsByEventId("ORDER-001")).thenReturn(true);

        orderFulfillmentService.processOrder(orderMessage);

        verify(orderRepository, never()).save(any(Order.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void processOrder_firstSave_setsStatusToProcessing() {
        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);

        // Capture the status at the time of each save call
        java.util.List<OrderStatus> capturedStatuses = new java.util.ArrayList<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            capturedStatuses.add(o.getStatus());
            return o;
        });

        orderFulfillmentService.processOrder(orderMessage);

        assertThat(capturedStatuses).hasSize(2);
        assertThat(capturedStatuses.get(0)).isEqualTo(OrderStatus.PROCESSING);
        assertThat(capturedStatuses.get(1)).isEqualTo(OrderStatus.FULFILLED);
    }
}
