package com.example.orderprocessor.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderEventsExchange(RabbitMQProperties properties) {
        return new TopicExchange(properties.orderExchange(), true, false);
    }

    @Bean
    public TopicExchange dlxOrderEventsExchange(RabbitMQProperties properties) {
        return new TopicExchange(properties.dlxExchange(), true, false);
    }

    @Bean
    public Queue orderPlacedQueue(RabbitMQProperties properties, OrderProcessingProperties orderProcessingProperties) {
        return new Queue(properties.orderQueue(), true, false, false, Map.of(
                "x-dead-letter-exchange", properties.dlxExchange(),
                "x-dead-letter-routing-key", properties.orderRoutingKey(),
                "x-queue-type", "quorum",
                "x-delivery-limit", orderProcessingProperties.maxRetries() + 1
        ));
    }

    @Bean
    public Queue orderDlq(RabbitMQProperties properties) {
        return new Queue(properties.dlqQueue(), true);
    }

    @Bean
    public Binding bindOrderPlacedQueue(Queue orderPlacedQueue, TopicExchange orderEventsExchange, RabbitMQProperties properties) {
        return BindingBuilder.bind(orderPlacedQueue).to(orderEventsExchange).with(properties.orderRoutingKey());
    }

    @Bean
    public Binding bindOrderDlq(Queue orderDlq, TopicExchange dlxOrderEventsExchange, RabbitMQProperties properties) {
        return BindingBuilder.bind(orderDlq).to(dlxOrderEventsExchange).with(properties.orderRoutingKey());
    }

    @Bean
    public Declarables rabbitDeclarables(
            TopicExchange orderEventsExchange,
            TopicExchange dlxOrderEventsExchange,
            Queue orderPlacedQueue,
            Queue orderDlq,
            Binding bindOrderPlacedQueue,
            Binding bindOrderDlq
    ) {
        return new Declarables(orderEventsExchange, dlxOrderEventsExchange, orderPlacedQueue, orderDlq, bindOrderPlacedQueue, bindOrderDlq);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
