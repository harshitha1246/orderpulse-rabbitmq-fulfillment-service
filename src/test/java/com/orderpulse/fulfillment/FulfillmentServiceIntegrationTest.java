package com.orderpulse.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.entity.Order;
import com.orderpulse.fulfillment.entity.OrderStatus;
import com.orderpulse.fulfillment.repository.OrderRepository;
import com.orderpulse.fulfillment.repository.ProcessedEventRepository;
import com.orderpulse.fulfillment.service.OrderFulfillmentService;

@SpringBootTest
@ActiveProfiles("test")
class FulfillmentServiceIntegrationTest {

    @Autowired
    private OrderFulfillmentService orderFulfillmentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanUp() {
        orderRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertThat(orderFulfillmentService).isNotNull();
    }

    @Test
    void processOrder_shouldPersistOrderWithFulfilledStatus() {
        OrderMessage orderMessage = OrderMessage.builder()
                .orderId("INT-ORDER-001")
                .customerId("CUSTOMER-INT-001")
                .productId("PRODUCT-INT-001")
                .quantity(3)
                .totalAmount(new BigDecimal("149.97"))
                .build();

        orderFulfillmentService.processOrder(orderMessage);

        Order savedOrder = orderRepository.findByOrderId("INT-ORDER-001").orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.FULFILLED);
        assertThat(savedOrder.getCustomerId()).isEqualTo("CUSTOMER-INT-001");
        assertThat(savedOrder.getQuantity()).isEqualTo(3);

        assertThat(processedEventRepository.existsByEventId("INT-ORDER-001")).isTrue();
    }

    @Test
    void processOrder_duplicate_shouldBeIdempotent() {
        OrderMessage orderMessage = OrderMessage.builder()
                .orderId("INT-ORDER-DUP-001")
                .customerId("CUSTOMER-INT-001")
                .productId("PRODUCT-INT-001")
                .quantity(1)
                .totalAmount(new BigDecimal("49.99"))
                .build();

        orderFulfillmentService.processOrder(orderMessage);
        orderFulfillmentService.processOrder(orderMessage);

        long orderCount = orderRepository.findAll().stream()
                .filter(o -> "INT-ORDER-DUP-001".equals(o.getOrderId()))
                .count();
        assertThat(orderCount).isEqualTo(1);
    }
}
