package com.orderpulse.fulfillment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.service.OrderFulfillmentService;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderFulfillmentService orderFulfillmentService;

    @Test
    void placeOrder_shouldReturn202AndPublishMessage() throws Exception {
        OrderMessage orderMessage = OrderMessage.builder()
                .orderId("ORDER-CTRL-001")
                .customerId("CUSTOMER-001")
                .productId("PRODUCT-001")
                .quantity(1)
                .totalAmount(new BigDecimal("29.99"))
                .build();

        doNothing().when(orderFulfillmentService).publishOrder(any(OrderMessage.class));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderMessage)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Order accepted for processing: ORDER-CTRL-001"));

        verify(orderFulfillmentService, times(1)).publishOrder(any(OrderMessage.class));
    }
}
