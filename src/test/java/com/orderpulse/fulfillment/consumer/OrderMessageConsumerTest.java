package com.orderpulse.fulfillment.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.service.OrderFulfillmentService;

@ExtendWith(MockitoExtension.class)
class OrderMessageConsumerTest {

    @Mock
    private OrderFulfillmentService orderFulfillmentService;

    @InjectMocks
    private OrderMessageConsumer orderMessageConsumer;

    private OrderMessage orderMessage;

    @BeforeEach
    void setUp() {
        orderMessage = OrderMessage.builder()
                .orderId("ORDER-001")
                .customerId("CUSTOMER-001")
                .productId("PRODUCT-001")
                .quantity(1)
                .totalAmount(new BigDecimal("49.99"))
                .build();
    }

    @Test
    void consumeOrder_shouldDelegateToFulfillmentService() {
        orderMessageConsumer.consumeOrder(orderMessage);

        verify(orderFulfillmentService, times(1)).processOrder(orderMessage);
    }

    @Test
    void consumeOrder_whenServiceThrows_shouldRethrowException() {
        doThrow(new RuntimeException("Processing failed"))
                .when(orderFulfillmentService).processOrder(orderMessage);

        assertThrows(RuntimeException.class, () -> orderMessageConsumer.consumeOrder(orderMessage));
        verify(orderFulfillmentService, times(1)).processOrder(orderMessage);
    }

    @Test
    void consumeDeadLetter_shouldLogWithoutThrowing() {
        orderMessageConsumer.consumeDeadLetter(orderMessage);
        // No exception means DLQ handler completed successfully
    }
}
