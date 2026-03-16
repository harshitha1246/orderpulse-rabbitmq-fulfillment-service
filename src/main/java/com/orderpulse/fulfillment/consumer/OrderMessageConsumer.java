package com.orderpulse.fulfillment.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.orderpulse.fulfillment.dto.OrderMessage;
import com.orderpulse.fulfillment.service.OrderFulfillmentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderFulfillmentService orderFulfillmentService;

    /**
     * Listens to the order queue and delegates processing to the fulfillment service.
     * On exception, the message is rejected and routed to the DLQ via RabbitMQ configuration.
     */
    @RabbitListener(queues = "${rabbitmq.queue.order}", containerFactory = "rabbitListenerContainerFactory")
    public void consumeOrder(OrderMessage orderMessage) {
        log.info("Received order message for orderId: {}", orderMessage.getOrderId());
        try {
            orderFulfillmentService.processOrder(orderMessage);
        } catch (Exception e) {
            log.error("Failed to process order for orderId: {}. Error: {}",
                    orderMessage.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Listens to the Dead Letter Queue for messages that failed processing.
     */
    @RabbitListener(queues = "${rabbitmq.queue.dlq}", containerFactory = "rabbitListenerContainerFactory")
    public void consumeDeadLetter(OrderMessage orderMessage) {
        log.error("Received dead letter message for orderId: {}. Manual intervention required.",
                orderMessage.getOrderId());
    }
}
