package com.example.orderprocessor.integration;

import com.example.orderprocessor.model.OrderEntity;
import com.example.orderprocessor.model.OrderPlacedEvent;
import com.example.orderprocessor.model.OrderProcessedEvent;
import com.example.orderprocessor.model.OrderStatus;
import com.example.orderprocessor.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderFlowIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    static MySQLContainer<?> mySQLContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("orderdb")
            .withUsername("user")
            .withPassword("password");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword);

        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange orderEventsExchange;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PROCESSED_TEST_QUEUE = "order.processed.test.queue";

    @BeforeEach
    void setUpProcessedQueueBinding() {
        Queue queue = new Queue(PROCESSED_TEST_QUEUE, false, false, true);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(orderEventsExchange).with("order.processed"));
    }

    @Test
    void shouldConsumePlacedEventUpdateDbAndPublishProcessedEvent() throws Exception {
        OrderPlacedEvent placedEvent = new OrderPlacedEvent(
                "order-it-1",
                "product-it-1",
                3,
                "customer-it-1",
                Instant.now()
        );

        rabbitTemplate.convertAndSend("order.events", "order.placed", placedEvent);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    Optional<OrderEntity> orderOpt = orderRepository.findById("order-it-1");
                    assertThat(orderOpt).isPresent();
                    assertThat(orderOpt.get().getStatus()).isEqualTo(OrderStatus.PROCESSED);
                });

        Message processedMessage = Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .until(() -> rabbitTemplate.receive(PROCESSED_TEST_QUEUE), msg -> msg != null);

        OrderProcessedEvent processedEvent = objectMapper.readValue(
                new String(processedMessage.getBody(), StandardCharsets.UTF_8),
                OrderProcessedEvent.class
        );

        assertThat(processedEvent.orderId()).isEqualTo("order-it-1");
        assertThat(processedEvent.status()).isEqualTo("PROCESSED");
    }
}
