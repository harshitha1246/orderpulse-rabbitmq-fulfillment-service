# OrderPulse RabbitMQ Fulfillment Service

Backend microservice that consumes `order.placed` events, processes orders atomically in MySQL, publishes `order.processed` events, and routes failed messages through RabbitMQ DLQ patterns.

## Tech Stack

- Java 17
- Spring Boot 3 (Web, AMQP, JPA)
- RabbitMQ
- MySQL 8
- Docker + Docker Compose
- JUnit 5 + Mockito + Testcontainers

## Features Implemented

- Stateless event-driven order processor.
- RabbitMQ topology declaration on startup:
  - Exchange: `order.events` (topic)
  - Queue: `order.placed.queue`
  - Binding: `order.placed`
  - DLX: `dlx.order.events`
  - DLQ: `order.dlq`
- Manual message ACK/NACK handling.
- Transient failures are NACKed with requeue.
- Queue configured as quorum with delivery limit for retry capping and DLQ transfer.
- Permanent failures are NACKed without requeue and sent to DLQ.
- Idempotency for duplicate already-processed orders.
- Transactional DB updates for order status transitions.
- Structured logging with event and order context.
- `GET /health` endpoint returns `{"status":"UP"}`.
- Multi-stage Docker build.
- Integration test validates end-to-end flow.

## Project Structure

```text
.
├── src/main/java/com/example/orderprocessor
│   ├── config
│   ├── controller
│   ├── model
│   ├── repository
│   └── service
├── src/main/resources/application.yml
├── src/test/java/com/example/orderprocessor
│   ├── controller
│   ├── integration
│   └── service
├── db_init/init.sql
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── pom.xml
```

## Event Schemas

Incoming (`order.placed`):

```json
{
  "orderId": "string",
  "productId": "string",
  "quantity": 1,
  "customerId": "string",
  "timestamp": "2023-10-27T10:00:00Z"
}
```

Outgoing (`order.processed`):

```json
{
  "orderId": "string",
  "status": "PROCESSED",
  "processedAt": "2023-10-27T10:05:00Z"
}
```

## Orders Table

Created via `db_init/init.sql` and aligned with JPA entity:

- `id` (PK)
- `product_id`
- `customer_id`
- `quantity`
- `status` (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`)
- `created_at`
- `updated_at`

## Setup

### Prerequisites

- Docker Desktop
- Java 17 (optional for local non-container runs)
- Maven 3.9+ (optional for local non-container runs)

### Run with Docker Compose

1. (Optional) copy `.env.example` to `.env` and customize values.
2. Start services:

```bash
docker compose up --build
```

3. Verify health:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"UP"}
```

### RabbitMQ UI

- URL: `http://localhost:15672`
- Username: `guest`
- Password: `guest`

## Running Tests

### Local Maven tests

```bash
mvn test
```

### Inside service container (if running compose)

```bash
docker compose exec order-processor-service mvn test
```

When tests are run from a standalone Maven container without Docker socket access, the Testcontainers-based integration test is skipped automatically while unit tests continue to run.

## Event Flow

1. Producer publishes `order.placed` to `order.events` with routing key `order.placed`.
2. Service consumes from `order.placed.queue`.
3. Payload is parsed and validated.
4. Service upserts order and sets status `PROCESSING`.
5. Service publishes `order.processed`.
6. Service sets status `PROCESSED` in same transactional flow.
7. Listener ACKs message.
8. If permanent parse/validation error: NACK without requeue (goes to DLQ).
9. If transient DB/Rabbit error: NACK with requeue, quorum queue delivery limit controls max retries, then message is dead-lettered.

## Idempotency Strategy

- If an incoming order already has status `PROCESSED`, processing is skipped.
- This prevents duplicate state transitions and duplicate side effects for repeated deliveries.

## Error Handling Strategy

- `PermanentProcessingException`: malformed/invalid event, NACK `requeue=false`.
- `TransientProcessingException`: temporary infra issues, NACK `requeue=true`.
- Unknown exceptions are treated as transient.

## Environment Variables

Documented in `.env.example`:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`
- `APP_RABBITMQ_ORDER_EXCHANGE`
- `APP_RABBITMQ_ORDER_QUEUE`
- `APP_RABBITMQ_ORDER_ROUTING_KEY`
- `APP_RABBITMQ_PROCESSED_ROUTING_KEY`
- `APP_RABBITMQ_DLX_EXCHANGE`
- `APP_RABBITMQ_DLQ_QUEUE`
- `APP_ORDER_MAX_RETRIES`
- `SERVER_PORT`
