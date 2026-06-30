# ecom-realtime 🛒
### Spring Boot + Kafka + Grafana + Loki | Saga Pattern | Observability

A production-grade e-commerce order processing system demonstrating:
- **Saga Choreography Pattern** via Kafka events
- **Distributed Tracing** with OpenTelemetry → Grafana Tempo
- **Structured JSON Logging** searchable in Grafana Loki (swap for Splunk at work)
- **Dead Letter Topics** for failed messages
- **Idempotency Guards** to prevent double-charging

---

## Architecture

```
POST /api/orders
      │
      ▼
 order-service ──── [order-placed] ────► inventory-service
      │                                        │
      │                              [inventory-reserved]
      │                               or [inventory-failed]
      │                                        │
      │◄─────[inventory-failed]────────────────┘
      │
      │              payment-service ◄─── [inventory-reserved]
      │                    │
      │◄──── [payment-processed] ──────────────┘
      │  (SUCCESS → CONFIRMED)
      │  (FAILED  → FAILED)
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker Desktop (running)

---

## Step 1: Start Infrastructure (Kafka + DBs + Observability)

```bash
docker-compose up -d
```

Wait ~30 seconds for everything to start, then verify:

| Service       | URL                          | Credentials     |
|---------------|------------------------------|-----------------|
| Kafka UI      | http://localhost:8090        | none            |
| Grafana       | http://localhost:3000        | admin / admin   |
| Prometheus    | http://localhost:9090        | none            |

---

## Step 2: Start the Three Microservices

Open **3 separate terminals**:

**Terminal 1 — Order Service (port 8081)**
```bash
cd order-service
mvn spring-boot:run
```

**Terminal 2 — Inventory Service (port 8082)**
```bash
cd inventory-service
mvn spring-boot:run
```

**Terminal 3 — Payment Service (port 8083)**
```bash
cd payment-service
mvn spring-boot:run
```

Wait for `Started [Service]Application` in each terminal.

---

## Step 3: Seed Inventory Data

```bash
curl -X POST http://localhost:8082/api/inventory/seed
```

This creates:
- `PROD-001` MacBook Pro (50 units)
- `PROD-002` iPhone 15 (200 units)  
- `PROD-003` AirPods Pro (5 units)

---

## Step 4: Test the Happy Path

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-001",
    "quantity": 2,
    "totalAmount": 3998.00
  }'
```

Note the `orderId` in response, then poll status:

```bash
# Replace {orderId} with actual value
curl http://localhost:8081/api/orders/{orderId}
```

Watch the status change: `PENDING → PAYMENT_PROCESSING → CONFIRMED`

---

## Step 5: Test the Failure Path (Out of Stock)

Set AirPods stock to 0:
```bash
curl -X PUT "http://localhost:8082/api/inventory/PROD-003/stock?qty=0"
```

Now try to order AirPods:
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-002",
    "productId": "PROD-003",
    "quantity": 1,
    "totalAmount": 249.00
  }'
```

Order status will be `FAILED`. Check `inventory-failed.DLT` topic in Kafka UI.

---

## Step 6: Observe in Grafana

1. Open http://localhost:3000 (admin/admin)
2. Go to **Dashboards → Ecom Realtime → Overview**
3. See orders placed, payment success rate, error rates

### Trace a Request End-to-End

1. From your order-service terminal logs, copy the `traceId` from a log line
2. In Grafana → **Explore → Tempo**
3. Paste the traceId → see all 3 services in one waterfall view

### Search Logs (like Splunk)

1. In Grafana → **Explore → Loki**
2. Query: `{job="order-service"}`
3. Filter by traceId: `{job="order-service"} |= "your-trace-id-here"`

---

## Step 7: Test Dead Letter Topic

Temporarily crash inventory-service, place an order, restart it.
- Go to Kafka UI → Topics → `order-placed.DLT`
- See the failed messages queued for replay

---

## API Reference

### Order Service (port 8081)
| Method | Path | Description |
|--------|------|-------------|
| POST | /api/orders | Place a new order |
| GET | /api/orders | List all orders |
| GET | /api/orders/{orderId} | Get order status |

### Inventory Service (port 8082)
| Method | Path | Description |
|--------|------|-------------|
| GET | /api/inventory | List all products |
| GET | /api/inventory/{productId} | Get product stock |
| PUT | /api/inventory/{productId}/stock?qty=N | Set stock level |
| POST | /api/inventory/seed | Seed demo products |

### Actuator Endpoints (all services)
- `/actuator/health` — service health
- `/actuator/prometheus` — metrics (scraped by Prometheus)

---

## Key Concepts Demonstrated

| Concept | Where |
|---------|-------|
| Saga Choreography | All 3 services via Kafka topics |
| Dead Letter Topic | KafkaConfig.java in each service |
| Idempotency Guard | PaymentConsumer.java |
| Distributed Tracing | traceId in every log line |
| Custom Business Metrics | OrderController.java, PaymentConsumer.java |
| At-least-once delivery | Consumer group offset management |

---

## Splunk Integration (for work)

Replace Loki with Splunk by changing `logback-spring.xml`:

```xml
<appender name="SPLUNK" class="com.splunk.logging.HttpEventCollectorLogbackAppender">
    <url>https://your-splunk-instance:8088</url>
    <token>your-hec-token</token>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

Search in Splunk:
```
index="production" traceId="abc123" | sort _time
index="production" service="payment-service" level="WARN" earliest=-1h
```

The structured JSON logs (with traceId) work identically in both Loki and Splunk.
