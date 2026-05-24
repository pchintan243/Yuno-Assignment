# 💳 Payment Orchestration System

A simplified backend system that simulates a real-world payment orchestration platform (inspired by modern fintech systems).
This service intelligently routes payments, ensures idempotency, and provides retry & failover mechanisms for reliability.

---

## 🚀 Features

* Create Payment API
* Fetch Payment API
* Smart Routing (CARD → Provider A, UPI → Provider B)
* Retry Mechanism (configurable attempts)
* Failover Support (automatic fallback to secondary provider)
* Idempotency Handling (prevents duplicate transactions)
* Payment Status Tracking
* Global Exception Handling
* Unit Test Coverage

---

## 🏗️ Architecture Overview

```
Client
 ↓
Controller Layer
 ↓
Service Layer (Business Logic + Idempotency)
 ↓
Orchestrator (Core Execution Engine)
 ↓
Routing Engine (Strategy Pattern)
 ↓
Provider Connectors (A / B)
 ↓
Persistence Layer (H2 DB)
```

---

## ⚙️ Tech Stack

* Java 21
* Spring Boot
* Maven
* Spring Data JPA (Hibernate)
* H2 In-Memory Database
* JUnit & Mockito

---

## 📦 Project Structure

```
controller      → REST APIs
service         → Business logic & idempotency
orchestrator    → Core payment execution (retry + failover)
routing         → Provider selection logic
provider        → Payment provider implementations
repository      → Database access layer
entity          → Database models
dto             → Request/Response objects
exception       → Global exception handling
```

---

## 🔌 API Endpoints

### ➤ Create Payment

**POST /payments**

#### Request:

```json
{
  "amount": 100.0,
  "type": "CARD",
  "idempotencyKey": "abc123"
}
```

#### Response:

```json
{
  "paymentId": "uuid",
  "amount": 100.0,
  "type": "CARD",
  "status": "SUCCESS",
  "provider": "ProviderA"
}
```

---

### ➤ Fetch Payment

**GET /payments/{id}**

---

## 🔁 Payment Flow

1. Validate incoming request
2. Check idempotency key (prevent duplicates)
3. Route to appropriate provider
4. Execute payment
5. Retry on failure (max attempts)
6. Failover to secondary provider if needed
7. Persist and return final status

---

## 🔐 Idempotency

* Each request must include a unique `idempotencyKey`
* Duplicate requests return the same response
* Ensures **exactly-once processing behavior**

---

## 🔄 Retry & Failover Strategy

* Retries are attempted on the primary provider
* If retries fail → system switches to fallback provider
* Improves success rate and system resilience

---

## ❌ Error Handling

Standardized error response format:

```json
{
  "message": "Amount must be greater than 0",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-04-10T12:00:00"
}
```

---

## 🧪 Test Coverage

### ✔ Sanity Tests

* Payment creation
* Payment retrieval

### ✔ Regression Tests

* Idempotency validation

### ✔ Integration-Level Logic

* Retry mechanism
* Failover handling

### ✔ Negative Scenarios

* Invalid amount
* Missing idempotency key
* Unsupported payment type

---

## ▶️ How to Run

```bash
mvn spring-boot:run
```

Application runs at:

```
http://localhost:8080
```

---

## 🧠 Design Decisions

* **Strategy Pattern** for provider routing (extensible design)
* **Layered Architecture** for separation of concerns
* **Idempotency-first approach** for data consistency
* **Retry + Failover** for fault tolerance
* Designed to easily support additional providers

---

## 🚀 Future Enhancements

* Redis for distributed idempotency store
* Circuit breaker (Resilience4j)
* Async processing with messaging (Kafka)
* Observability (metrics, logging, tracing)

---

## 👨‍💻 Author
Sai Krishna Kolipaka.
