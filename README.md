# orderpulse-rabbitmq-fulfillment-service

Event-driven order fulfillment microservice built with **Spring Boot**, **RabbitMQ**, and **MySQL**.

## Features

- **Event-driven architecture** – Orders are published to RabbitMQ and consumed asynchronously
- **Idempotent processing** – Duplicate messages are safely detected and skipped using a `processed_events` table
- **Dead Letter Queue (DLQ)** – Failed messages are automatically routed to a DLQ for inspection and retry
- **Health checks** – Spring Boot Actuator exposes `/actuator/health` with detailed status for all components
- **Docker Compose** – One-command local environment with MySQL, RabbitMQ, and the application
- **Automated tests** – Unit tests, controller tests, and integration tests (H2 in-memory database)

## Architecture

```
HTTP POST /api/orders
        │
        ▼
OrderController  ──►  RabbitMQ (order.exchange / order.queue)
                                │
                                ▼
                    OrderMessageConsumer
                                │
                    ┌───────────┴──────────┐
                    │                      │
                    ▼                      ▼
           (success)              (exception thrown)
         OrderFulfillmentService      order.dlq
                    │
           Idempotency check
           (processed_events)
                    │
               MySQL (orders)
```

## Getting Started

### Prerequisites

- Java 17+
- Docker & Docker Compose

### Run with Docker Compose

```bash
docker-compose up --build
```

This starts:
- **MySQL** on port `3306`
- **RabbitMQ** on port `5672` (management UI at `http://localhost:15672`, guest/guest)
- **Fulfillment Service** on port `8080`

### API

#### Place an Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER-001",
    "customerId": "CUSTOMER-001",
    "productId": "PRODUCT-001",
    "quantity": 2,
    "totalAmount": 99.99
  }'
```

#### Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Configuration

All settings are configurable via environment variables:

| Variable            | Default     | Description           |
|---------------------|-------------|-----------------------|
| `DB_HOST`           | `localhost` | MySQL host            |
| `DB_PORT`           | `3306`      | MySQL port            |
| `DB_NAME`           | `orderpulse`| MySQL database name   |
| `DB_USER`           | `root`      | MySQL username        |
| `DB_PASSWORD`       | `root`      | MySQL password        |
| `RABBITMQ_HOST`     | `localhost` | RabbitMQ host         |
| `RABBITMQ_PORT`     | `5672`      | RabbitMQ AMQP port    |
| `RABBITMQ_USER`     | `guest`     | RabbitMQ username     |
| `RABBITMQ_PASSWORD` | `guest`     | RabbitMQ password     |

## Running Tests

```bash
mvn clean test
```

Tests use an H2 in-memory database and mock RabbitMQ connections — no external services required.
