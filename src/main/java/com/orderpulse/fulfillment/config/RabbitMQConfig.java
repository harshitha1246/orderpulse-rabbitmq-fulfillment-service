package com.orderpulse.fulfillment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.order}")
    private String orderExchange;

    @Value("${rabbitmq.queue.order}")
    private String orderQueue;

    @Value("${rabbitmq.queue.dlq}")
    private String deadLetterQueue;

    @Value("${rabbitmq.routing-key.order}")
    private String orderRoutingKey;

    @Value("${rabbitmq.routing-key.dlq}")
    private String dlqRoutingKey;

    // Dead Letter Queue
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    // Main Order Queue with DLQ configuration
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(orderQueue)
                .withArgument("x-dead-letter-exchange", orderExchange)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    // Direct Exchange
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(orderExchange);
    }

    // Binding: order queue -> exchange with order routing key
    @Bean
    public Binding orderQueueBinding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(orderRoutingKey);
    }

    // Binding: DLQ -> exchange with DLQ routing key
    @Bean
    public Binding deadLetterQueueBinding(Queue deadLetterQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(orderExchange).with(dlqRoutingKey);
    }

    // JSON message converter
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Configure RabbitTemplate to use JSON converter
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    // Configure listener container factory to use JSON converter
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
