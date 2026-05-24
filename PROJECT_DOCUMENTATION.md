# Payment Orchestration System — Full Project Documentation

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Project Structure](#4-project-structure)
5. [Request/Response I/O Specification](#5-requestresponse-io-specification)
6. [API Endpoints](#6-api-endpoints)
7. [Payment Flow (Step-by-Step)](#7-payment-flow-step-by-step)
8. [Key Design Decisions](#8-key-design-decisions)
9. [Database Schema](#9-database-schema)
10. [Configuration](#10-configuration)
11. [Test Cases](#11-test-cases)
12. [Performance Metrics & Observability](#12-performance-metrics--observability)
13. [Non-Functional Requirements](#13-non-functional-requirements)
14. [Development Prompts (Vibe Coding History)](#14-development-prompts-vibe-coding-history)
15. [Interview Q\&A](#15-interview-qa)
16. [Future Enhancements](#16-future-enhancements)

---

## 1. System Overview

The **Payment Orchestration System** is a Spring Boot microservice that acts as an intelligent router and processor for payment transactions. It sits between client applications and external payment providers, handling routing, retries, failover, idempotency, and state persistence.

**Core Problem It Solves:** External payment providers (like Stripe, Razorpay) can fail temporarily due to network issues, timeouts, or server overload. This system ensures payments succeed even when a primary provider is down by routing to a secondary provider and retrying failed attempts.

**Real-World Analogy:** Think of it like a taxi aggregator app. You request a ride (payment). If Driver A (Provider A) doesn't accept or goes offline, the app automatically offers it to Driver B (Provider B) — without you having to do anything.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              CLIENT                                      │
│                    (Mobile App / Web / Backend)                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTP POST /payments
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         REST CONTROLLER                                  │
│                      PaymentController                                   │
│         POST /payments    GET /payments/{id}    GET /payments           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         SERVICE LAYER                                    │
│                      PaymentServiceImpl                                  │
│   • Validates request                                                   │
│   • Checks idempotency key (prevents duplicate payments)                │
│   • Persists payment in INITIATED state                                 │
│   • Delegates to Orchestrator                                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      ORCHESTRATOR LAYER                                 │
│                       PaymentOrchestrator                                │
│   • Routes to primary provider (CARD→A, UPI→B)                         │
│   • Attempts up to 3 retries on failure                                 │
│   • Falls back to secondary provider if retries exhaust                 │
│   • Updates DB state: INITIATED → PROCESSING → SUCCESS/FAILED          │
└─────────────────────────────────────────────────────────────────────────┘
                         │                              │
          ┌──────────────┘                              └──────────────┐
          ▼                                                         ▼
┌─────────────────────┐                               ┌─────────────────────┐
│   ProviderA          │                               │   ProviderB          │
│  (CARD payments)     │                               │  (UPI payments)      │
│                     │                               │                     │
│  HTTP call to       │                               │  HTTP call to        │
│  ProviderA API      │                               │  ProviderB API       │
└─────────────────────┘                               └─────────────────────┘
          │                                                         │
          └──────────────────────┬──────────────────────────────┘
                                 ▼
                    ┌─────────────────────────┐
                    │   H2 In-Memory DB      │
                    │   (payments table)      │
                    └─────────────────────────┘
```

---

## 3. Tech Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 21 LTS |
| Framework | Spring Boot | 4.0.6 |
| Build Tool | Maven | 3.x |
| Database | H2 (in-memory) | 2.4.x |
| ORM | Spring Data JPA / Hibernate | 7.x |
| Validation | Jakarta Bean Validation | 3.1.x |
| Testing | JUnit 6, Mockito | Latest |
| Metrics | Micrometer + Actuator | 1.16.x |
| API Docs | SpringDoc OpenAPI | — |

---

## 4. Project Structure

```
src/
├── main/
│   ├── java/com/yuno/payment_orchestrator/
│   │   ├── PaymentOrchestratorApplication.java   ← Spring Boot entry point
│   │   ├── controller/
│   │   │   └── PaymentController.java           ← REST endpoints
│   │   ├── service/
│   │   │   ├── PaymentService.java             ← Interface
│   │   │   └── impl/PaymentServiceImpl.java   ← Business logic + idempotency
│   │   ├── orchestrator/
│   │   │   └── PaymentOrchestrator.java        ← Retry + failover engine
│   │   ├── routing/
│   │   │   └── PaymentRoutingService.java     ← Provider selection
│   │   ├── provider/
│   │   │   ├── PaymentProvider.java           ← Interface
│   │   │   └── impl/
│   │   │       ├── ProviderA.java             ← CARD provider
│   │   │       └── ProviderB.java             ← UPI provider
│   │   ├── entity/
│   │   │   └── Payment.java                   ← JPA entity
│   │   ├── dto/
│   │   │   ├── PaymentRequest.java            ← API request
│   │   │   └── PaymentResponse.java           ← API response
│   │   ├── repository/
│   │   │   └── PaymentRepository.java         ← Spring Data JPA
│   │   ├── enumtype/
│   │   │   ├── PaymentStatus.java             ← INITIATED/PROCESSING/SUCCESS/FAILED
│   │   │   └── PaymentType.java              ← CARD/UPI
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java    ← Centralized error handling
│   │       ├── ErrorResponse.java             ← Standardized error DTO
│   │       ├── PaymentNotFoundException.java
│   │       └── UnsupportedPaymentTypeException.java
│   └── resources/
│       └── application.properties              ← Server port, actuator config
└── test/java/com/yuno/payment_orchestrator/
    ├── PaymentOrchestratorApplicationTests.java
    ├── controller/PaymentControllerTest.java
    ├── service/PaymentServiceTest.java
    ├── orchestrator/
    │   ├── PaymentOrchestratorTest.java
    │   └── PaymentOrchestratorRetryTest.java
    ├── routing/PaymentRoutingServiceTest.java
    └── exception/GlobalExceptionHandlerTest.java
```

---

## 5. Request/Response I/O Specification

### Create Payment — Request

```
POST /payments
Content-Type: application/json

{
  "amount": 100.00,
  "type": "CARD",
  "idempotencyKey": "order-12345"
}
```

| Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `amount` | `Double` | Yes | `@Positive` | Payment amount in currency units |
| `type` | `PaymentType` | Yes | Not null | CARD or UPI |
| `idempotencyKey` | `String` | Yes | `@NotBlank` | Client-generated unique key |

### Create Payment — Response (Success)

```
200 OK
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 100.00,
  "type": "CARD",
  "status": "SUCCESS",
  "provider": "ProviderA"
}
```

### Error Response Format

```
{
  "message": "Amount must be greater than 0",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-05-24T15:00:00"
}
```

| Error Code | HTTP Status | Trigger |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Invalid amount, null type, blank key |
| `UNSUPPORTED_PAYMENT_TYPE` | 400 | Unknown payment type |
| `PAYMENT_NOT_FOUND` | 404 | Payment ID doesn't exist |
| `INTERNAL_ERROR` | 500 | Unexpected exception |

---

## 6. API Endpoints

### POST /payments

Creates a new payment or returns an existing one for the same idempotency key.

### GET /payments/{id}

Retrieves a single payment by its UUID.

### GET /payments

Returns a paginated list of all payments.

| Parameter | Default | Description |
|---|---|---|
| `page` | 0 | Page number (0-indexed) |
| `size` | 20 | Page size |
| `sort` | `createdAt,desc` | Sort field and direction |

---

## 7. Payment Flow (Step-by-Step)

```
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 1: Request Arrives                                             │
│   Client → POST /payments {amount, type, idempotencyKey}            │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 2: Validation (Jakarta Bean Validation)                         │
│   • @NotNull @Positive on amount                                    │
│   • @NotNull on type                                                │
│   • @NotBlank on idempotencyKey                                    │
│   If invalid → 400 VALIDATION_ERROR                                 │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 3: Idempotency Check                                           │
│   SELECT * FROM payments WHERE idempotency_key = 'order-12345'      │
│   If found → return existing payment (no new DB record created)     │
│   This prevents double-charging on network retry                    │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 4: Create Payment Record (INITIATED)                           │
│   INSERT INTO payments (id, amount, type, status, idempotency_key)  │
│   Status = 'INITIATED'  ← First state in lifecycle                 │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 5: Route to Provider                                           │
│   if type == CARD → ProviderA                                      │
│   if type == UPI  → ProviderB                                      │
│   Provider name stored in payment.provider field                     │
│   Status = 'PROCESSING'  ← Second state                             │
│   UPDATE payments SET status='PROCESSING', provider='ProviderA'     │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ STEP 6: Process with Retry (MAX_RETRIES = 2 → 3 total attempts)     │
│   Attempt 1: provider.processPayment()                             │
│     If success → break                                              │
│     If fail   → retry                                               │
│   Attempt 2: provider.processPayment()                               │
│     If success → break                                              │
│     If fail   → retry                                               │
│   Attempt 3: provider.processPayment()                               │
│     If success → break                                              │
│     If fail   → move to failover                                    │
└─────────────────────────────────────────────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
               Success?                Failure?
                    │                     │
                    ▼                     ▼
┌─────────────────────┐   ┌──────────────────────────────────────┐
│ STEP 7a: SUCCESS     │   │ STEP 7b: FAILOVER                     │
│ Status = 'SUCCESS'  │   │ Get fallback provider                  │
│ UPDATE payments SET  │   │   CARD → ProviderB                    │
│   status='SUCCESS'  │   │   UPI  → ProviderA                    │
└─────────────────────┘   │                                          │
                          │ Retry 3 times with fallback             │
                          │ If any succeeds → SUCCESS              │
                          │ If all fail     → FAILED               │
                          └──────────────────────────────────────┘
                                         │
                                         ▼
                          ┌────────────────────────────┐
                          │ STEP 8: Final Persistence  │
                          │ UPDATE payments SET        │
                          │   status='SUCCESS/FAILED'  │
                          └────────────────────────────┘
                                         │
                                         ▼
                          ┌────────────────────────────┐
                          │ STEP 9: Return Response    │
                          │ {paymentId, status,       │
                          │  provider, amount, type}  │
                          └────────────────────────────┘
```

### State Transition Diagram

```
   ┌────────────┐
   │ INITIATED │
   └─────┬──────┘
         │ orchestrator.process() called
         ▼
   ┌────────────┐
   │PROCESSING │
   └─────┬──────┘
         │
    ┌────┴────┐
    │         │
 success?  fail?
    │         │
    ▼         └──► (retry up to 3x) ──► fail? ──► [FAILOVER to other provider]
┌────────┐                                              │
│SUCCESS │                                             │
└────────┘                                             ▼
                                                   ┌────────┐
                                                   │ FAILED │
                                                   └────────┘
```

---

## 8. Key Design Decisions

### 8.1 Strategy Pattern for Provider Routing

```
PaymentProvider (interface)
    ├── ProviderA (implements)
    └── ProviderB (implements)

PaymentRoutingService.route(PaymentType)
    → CARD  → ProviderA
    → UPI   → ProviderB

PaymentRoutingService.getFallback(PaymentType)
    → CARD  → ProviderB  (swapped)
    → UPI   → ProviderA  (swapped)
```

**Why?** Adding a new provider (e.g., ProviderC for BANK_TRANSFER) only requires creating a new class implementing `PaymentProvider` and adding a case to the switch — no changes needed in `PaymentOrchestrator` or `PaymentService`.

### 8.2 Idempotency-First Design

Every `POST /payments` request **must** include an `idempotencyKey`. The service checks the DB before creating a new payment. If the key exists, the existing payment is returned immediately.

**Why?** In distributed systems, network failures can cause a client to retry a payment request. Without idempotency, this would result in double charges. With idempotency, the retry is safe — the server returns the already-created payment.

### 8.3 Retry + Failover Two-Stage Strategy

| Stage | Strategy | Behavior |
|---|---|---|
| 1 | Retry | Same provider, up to 3 attempts |
| 2 | Failover | Different provider, up to 3 more attempts |

**Why not circuit breaker?** This is a simplified v1. Circuit breakers (Resilience4j) would be added in v2 to prevent hammering a failing provider.

### 8.4 Transaction Boundary

The entire `createPayment()` method is wrapped in `@Transactional`. If the orchestrator throws an exception, the DB insert of the INITIATED state rolls back.

**Limitation:** If the service crashes between `save(INITIATED)` and the final `save(SUCCESS/FAILED)`, the payment is left in PROCESSING state. A background reconciliation job would be needed for production.

---

## 9. Database Schema

### payments table (H2)

```sql
CREATE TABLE payments (
    id             VARCHAR(36)  PRIMARY KEY,  -- UUID auto-generated
    amount         DOUBLE       NOT NULL,
    type           VARCHAR(20)  NOT NULL,     -- 'CARD' or 'UPI'
    status         VARCHAR(20)  NOT NULL,     -- INITIATED/PROCESSING/SUCCESS/FAILED
    provider       VARCHAR(20),              -- ProviderA or ProviderB
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);
```

### Indexes

```sql
CREATE UNIQUE INDEX idx_idempotency_key ON payments(idempotency_key);
```

---

## 10. Configuration

```properties
server.port=9090

# Actuator — health, metrics, info
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when_authorized
```

**Max retries:** Hardcoded in `PaymentOrchestrator` as `MAX_RETRIES = 2` (3 total attempts per provider).

---

## 11. Test Cases

### 11.1 Test File Map

| Test File | Tests | Coverage |
|---|---|---|
| `PaymentControllerTest` | 8 | Controller endpoints, return types |
| `PaymentServiceTest` | 5 | Idempotency, persistence, 404 |
| `PaymentOrchestratorRetryTest` | 7 | Retry, failover, status transitions |
| `PaymentRoutingServiceTest` | 4 | Routing logic, provider selection |
| `GlobalExceptionHandlerTest` | 4 | Error mapping, HTTP status codes |
| `PaymentOrchestratorTest` | 1 | Failover behavior |
| `PaymentOrchestratorApplicationTests` | 1 | Spring context loads |

**Total: 32 tests, all passing.**

### 11.2 Test Case Table

| ID | Category | Scenario | Input | Expected Output |
|---|---|---|---|---|
| TC_S_01 | Sanity | Create CARD payment | `{amount:100, type:CARD, key:"abc"}` | 200, `{status:SUCCESS, provider:ProviderA}` |
| TC_S_02 | Sanity | Create UPI payment | `{amount:200, type:UPI, key:"xyz"}` | 200, `{status:SUCCESS/FAILED, provider:ProviderB}` |
| TC_S_03 | Sanity | Fetch payment by ID | `GET /payments/{id}` | 200, payment details |
| TC_S_04 | Sanity | List payments (paginated) | `GET /payments?page=0&size=20` | 200, page of payments |
| TC_R_01 | Regression | Idempotency hit | Same key sent twice | Same payment returned, no duplicate |
| TC_R_02 | Regression | Duplicate key skips orchestrator | Same key reused | Orchestrator never called |
| TC_R_03 | Regression | Data consistency | Create → Fetch | All fields match |
| TC_I_01 | Integration | Route CARD → ProviderA | `type=CARD` | `routingService.route()` returns ProviderA |
| TC_I_02 | Integration | Route UPI → ProviderB | `type=UPI` | `routingService.route()` returns ProviderB |
| TC_I_03 | Integration | Retry on failure | Primary returns false twice | Attempts called 3 times |
| TC_I_04 | Integration | Failover triggered | Primary fails all 3 attempts | Fallback provider called |
| TC_I_05 | Integration | Status transitions | Payment lifecycle | INITIATED→PROCESSING→SUCCESS |
| TC_N_01 | Negative | Invalid amount | `amount=-10` | 400, `VALIDATION_ERROR` |
| TC_N_02 | Negative | Missing amount | `amount=null` | 400, `VALIDATION_ERROR` |
| TC_N_03 | Negative | Missing type | `type=null` | 400, `VALIDATION_ERROR` |
| TC_N_04 | Negative | Missing idempotency key | `key=null` | 400, `VALIDATION_ERROR` |
| TC_N_05 | Negative | Blank idempotency key | `key=""` | 400, `VALIDATION_ERROR` |
| TC_N_06 | Negative | Payment not found | Non-existent ID | 404, `PAYMENT_NOT_FOUND` |
| TC_N_07 | Negative | Invalid payment type enum | `"type":"INVALID"` | 400, `INVALID` |
| TC_E_01 | Edge | Very large amount | `amount=1000000` | Processed successfully |
| TC_E_02 | Edge | Rapid duplicate requests | Same key, concurrent | One payment, idempotent response |
| TC_E_03 | Edge | Both providers fail | Simulated failure | Final status = FAILED |

---

## 12. Performance Metrics & Observability

### Exposed Metrics via `/actuator/metrics`

| Metric | Type | Tags | Description |
|---|---|---|---|
| `payment.processing.duration` | Timer | `type`, `provider`, `status` | End-to-end processing time |
| `payment.created` | Counter | `type`, `status` | New payment records |
| `payment.idempotent.hit` | Counter | `type` | Duplicate requests blocked |
| `payment.idempotent.miss` | Counter | `type` | New payments created |
| `payment.failover.triggered` | Counter | `type` | Failover activations |
| `payment.provider.attempt` | Counter | `type`, `provider` | Individual provider calls |

### Query Commands

```bash
# List all metrics
curl http://localhost:9090/actuator/metrics | jq '.names[]'

# Get processing duration with p95
curl "http://localhost:9090/actuator/metrics/payment.processing.duration?percentiles=0.95"

# Get failover count
curl "http://localhost:9090/actuator/metrics/payment.failover.triggered"
```

### Health Check

```bash
curl http://localhost:9090/actuator/health
```

---

## 13. Non-Functional Requirements

| NFR | Target |
|---|---|
| API p95 latency | < 500 ms |
| API p99 latency | < 1 s |
| Error rate | < 5% |
| Uptime SLA | 99.9% |
| Line coverage | > 70% (service + orchestrator) |
| Transaction isolation | `@Transactional` per request |
| API error responses | Never expose stack traces |
| Health endpoint | `show-details=when_authorized` |

---

## 14. Development Prompts (Vibe Coding History)

This section documents the AI-assisted development sessions.

### Session 1 — Initial Project Setup

**Prompt:** Build a payment orchestrator system in Java Spring Boot with REST API, CARD/UPI support, two providers, retry/failover, idempotency, exception handling, H2 DB, and unit tests.

**Outcome:** Full project structure created with all layers.

### Session 2 — Documentation

**Prompt:** Add JavaDoc comments to all files for developer understanding.

**Outcome:** Class-level and method-level Javadoc added across all 19 source files.

### Session 3 — Gradle Cleanup

**Prompt:** Remove Gradle reload button from IntelliJ (project uses Maven).

**Outcome:** Deleted `.idea/gradle.xml`. Updated `.gitignore`.

### Session 4 — Gap Analysis

**Prompt:** Check todo.txt and verify all requirements are implemented.

**Outcome:** Identified missing NFRs, performance metrics, development prompts doc, and test implementation.

### Session 5 — Full Implementation

**Prompt:** Implement the remaining items.

**Outcome:** Added Micrometer metrics, created 3 documentation files (NFR, Dev Prompts, Perf Metrics), implemented 32 test cases.

---

## 15. Interview Q&A

### Q1: How does the idempotency mechanism work?

**A:** Every `POST /payments` request must include an `idempotencyKey`. Before creating a new payment, the service queries the database: `SELECT * FROM payments WHERE idempotency_key = ?`. If a record exists, it returns that existing payment immediately — no new record is created, and the orchestrator is never called. This ensures that even if the client retries due to a network failure, they won't be double-charged.

**Follow-up: What if two requests with the same key arrive simultaneously?**
The `@Transactional` annotation on `createPayment()` combined with the unique constraint on `idempotency_key` in the DB ensures that only one transaction can insert. The second concurrent request will either wait for the first to commit (and then find the record) or get a constraint violation. In practice, one request succeeds and the other returns the existing payment.

---

### Q2: What is the difference between retry and failover?

**A:**
- **Retry** is attempting the same operation multiple times with the same provider. We retry up to 3 times (MAX_RETRIES=2 means loop goes 0,1,2 = 3 attempts).
- **Failover** is switching to a different provider when the primary has completely failed. After 3 failed attempts with ProviderA, we switch to ProviderB and try again up to 3 times.

```
Primary ProviderA:  [attempt 1] → [attempt 2] → [attempt 3]
                         ↓           ↓            ↓
                   fail       fail        fail
                                            ↓
                          Switch to ProviderB (failover)
                                            ↓
                         [attempt 1] → [attempt 2] → [attempt 3]
```

**Follow-up: Why not use a circuit breaker instead?**
Circuit breakers (like Resilience4j) would prevent calling a failing provider at all during a degraded period. Our current approach still calls the failing provider up to 3 times before failing over. For v1, the simpler retry+failover is sufficient. A circuit breaker would be added in v2.

---

### Q3: What happens if the application crashes mid-payment?

**A:** The transaction boundary is `createPayment()` in `PaymentServiceImpl`. Here's what can happen at each step:

| Step | State | Crash Result |
|---|---|---|
| Before `save()` | No DB record | Payment lost, client retries |
| After `save(INITIATED)` | INITIATED in DB | Payment exists, orchestrator never called — **orphan record** |
| After `save(PROCESSING)` | PROCESSING in DB | Same orphan record issue |
| After `save(SUCCESS/FAILED)` | Final state | Clean — payment complete |

**For production:** A background reconciliation job should scan for PROCESSING/INITIATED payments older than X minutes and mark them as FAILED or retry them.

---

### Q4: Why use the Strategy Pattern for providers?

**A:** The `PaymentProvider` interface defines a contract (`processPayment`, `getProviderName`). Each provider (`ProviderA`, `ProviderB`) implements this interface independently.

```
                    PaymentOrchestrator
                           │
                           ▼
                 PaymentRoutingService
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
        ProviderA                   ProviderB
   (implements PaymentProvider) (implements PaymentProvider)
```

**Benefits:**
- Adding ProviderC requires only a new class implementing `PaymentProvider` + adding a case in `PaymentRoutingService`
- `PaymentOrchestrator` is completely unaware of which provider it talks to
- Easy to unit test with mocks
-符合 **Open/Closed Principle** — open for extension, closed for modification

---

### Q5: How does the routing decide which provider to use?

**A:** The `PaymentRoutingService` uses a simple switch-based routing:

```java
public PaymentProvider route(PaymentType type) {
    return switch (type) {
        case CARD -> providerA;
        case UPI  -> providerB;
    };
}

public PaymentProvider getFallback(PaymentType type) {
    return switch (type) {
        case CARD -> providerB;  // swapped
        case UPI  -> providerA;  // swapped
    };
}
```

The fallback is always the **opposite** provider — ensuring that if one provider fails, the other gets a chance.

**Follow-up: How would you add weighted routing (e.g., 70% ProviderA, 30% ProviderB)?**
Inject a `LoadBalancer` or use a library like Ribbon. Or implement a simple random weighted selection in `PaymentRoutingService`. For production, consider a dedicated service like Consul or Eureka for dynamic provider registry.

---

### Q6: What is the transaction isolation level used?

**A:** Spring Data JPA uses the database's default isolation level. For H2/MySQL/PostgreSQL, this is typically `READ_COMMITTED`. The `@Transactional` annotation ensures:

1. All DB operations in `createPayment()` are atomic
2. If the orchestrator throws an exception, the INITIATED record is rolled back
3. Read-only queries (`getPayment`, `listPayments`) use `@Transactional(readOnly = true)` for connection pool optimization

---

### Q7: How would you scale this service horizontally?

**A:** The service is stateless by design (all state is in the DB), so horizontal scaling is straightforward:

1. **Stateless:** Each instance can handle any request — no in-memory session state.
2. **Load Balancer:** Put multiple instances behind an LB (e.g., Nginx, AWS ALB).
3. **DB Connection Pool:** Each instance has its own HikariCP pool. Size it appropriately (`core_count * 2 + spindle_count`).
4. **Idempotency:** Currently works across instances because it's DB-backed (not in-memory cache).
5. **For distributed locking:** Consider Redis-based distributed locks if you need stricter guarantees.

---

### Q8: What metrics would you watch in production?

**A:** Key metrics to monitor:

| Metric | Alert If | Why |
|---|---|---|
| `payment.processing.duration` p99 > 1s | Alert | Provider latency spike |
| `payment.created{status=FAILED}` rate | > 10% | Provider outage |
| `payment.failover.triggered` rate | > 40% | Primary provider unhealthy |
| `/actuator/health` | DOWN | Application crashed |
| HikariCP connection wait time | > 100ms | DB pool exhaustion |
| JVM heap usage | > 80% | Memory pressure / leak |

---

### Q9: Why did you choose H2 for this project?

**A:** H2 is an **in-memory** database ideal for development, testing, and demonstration. It:
- Starts instantly (no installation)
- Persists nothing to disk (clean slate on restart)
- Is SQL-compliant (Hibernate/JPA works without changes)

**For production:** Replace with PostgreSQL or MySQL. The JPA entity and repository code require **zero changes** — only the connection string in `application.properties` changes.

---

### Q10: Explain the state machine for a payment.

**A:** `PaymentStatus` has four states with strict transitions:

```
INITIATED  ──→  PROCESSING  ──→  SUCCESS
                            └────→  FAILED
```

- **INITIATED**: Created in DB, not yet sent to provider. Set by `PaymentServiceImpl`.
- **PROCESSING**: Sent to provider, awaiting response. Set by `PaymentOrchestrator`.
- **SUCCESS**: Provider confirmed payment. Set by `PaymentOrchestrator`.
- **FAILED**: All retries and failover attempts failed. Set by `PaymentOrchestrator`.

**No backward transitions** are possible by design — a payment cannot go from SUCCESS back to PROCESSING.

---

### Q11: How does validation work? Where is it enforced?

**A:** Jakarta Bean Validation (`jakarta.validation`) is used at two layers:

1. **Controller layer** — `@Valid` on `@RequestBody PaymentRequest` triggers automatic validation before the method body executes. If validation fails, `MethodArgumentNotValidException` is thrown.
2. **GlobalExceptionHandler** catches this and maps it to `ErrorResponse` with `errorCode=VALIDATION_ERROR`.

The annotations in `PaymentRequest`:
```java
@NotNull @Positive private Double amount;        // null or <=0 → 400
@NotNull          private PaymentType type;     // null → 400
@NotBlank         private String idempotencyKey; // null or "" → 400
```

---

### Q12: What is the difference between `@Transactional` on the service and on the repository?

**A:** The annotation is on the **calling method** (`createPayment` in `PaymentServiceImpl`). Spring creates a transaction proxy around `PaymentServiceImpl`. When `save()` is called, it participates in that existing transaction.

- If `orchestrator.process()` throws an exception, the transaction rolls back — the INITIATED record is deleted.
- If everything succeeds, the transaction commits after the method returns.
- `readOnly=true` on `getPayment()` and `listPayments()` hints the connection pool to use read-only connections (optimization).

---

### Q13: Can the retry mechanism cause duplicate payments?

**A:** This is a real risk. Here's the scenario:
1. Client calls `POST /payments`, gets no response (network timeout)
2. Client retries with the same idempotency key → idempotency check catches it, no duplicate created ✓
3. **But:** If the request succeeds, the provider processes it, but the network drops the response before it reaches our service, the client retries → idempotency handles it ✓

**However**, between steps in the orchestrator, if the app crashes, the payment is in PROCESSING. A reconciliation job is needed to handle these orphans.

**Production fix:** Implement **two-phase commit** with a payment state machine stored in Redis, or use an **outbox pattern** (write payment intent to a message queue, process asynchronously).

---

## 16. Future Enhancements

| Enhancement | Purpose | Complexity |
|---|---|---|
| Redis-backed idempotency | Distributed idempotency across multiple service instances | Medium |
| Resilience4j circuit breaker | Prevent hammering failing providers | Medium |
| Kafka async processing | Decouple payment processing from API response | High |
| OpenAPI / Swagger docs | Auto-generate API documentation | Low |
| PostgreSQL in production | Durable persistence with ACID guarantees | Low |
| Distributed tracing (OpenTelemetry) | Trace payments across microservices | Medium |
| Payment reconciliation job | Recover orphaned PROCESSING payments | Medium |
| Rate limiting | Prevent abuse of the payment API | Low |
| Webhook support | Receive async payment status updates from providers | High |
| Multi-currency support | Different providers for different currencies | Medium |
