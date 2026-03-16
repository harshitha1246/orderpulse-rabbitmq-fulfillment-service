package com.orderpulse.fulfillment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.service.OrderFulfillmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFulfillmentService orderFulfillmentService;

    /**
     * Accepts an order request and publishes it to the RabbitMQ order queue.
     */
    @PostMapping
    public ResponseEntity<String> placeOrder(@RequestBody OrderMessage orderMessage) {
        log.info("Received order placement request for orderId: {}", orderMessage.getOrderId());
        orderFulfillmentService.publishOrder(orderMessage);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Order accepted for processing: " + orderMessage.getOrderId());
    }
}
