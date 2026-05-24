# Non-Functional Requirements

This document defines the non-functional requirements (NFRs) for the Payment Orchestration System. These requirements govern the quality attributes of the system and guide infrastructure, deployment, and operational decisions.

---

## 1. Performance

### 1.1 Latency

| Metric | Target | Measurement Point |
|--------|--------|-------------------|
| API response time (p95) | < 500 ms | `POST /payments` end-to-end |
| API response time (p99) | < 1 s | `POST /payments` end-to-end |
| GET /payments/{id} p95 | < 100 ms | Controller entry to response |
| Payment processing throughput | > 50 TPS | Sustained load over 60 s |

> **Note:** Current instrumentation captures `payment.processing.duration` via Micrometer Timer. Query via `GET /actuator/metrics/payment.processing.duration?percentiles=0.5,0.95,0.99`.

### 1.2 Resource Utilization

| Resource | Target |
|----------|--------|
| CPU utilization | < 70% under peak load |
| Heap memory | < 512 MB (JVM `-Xmx`) |
| DB connection pool | Max 10 connections (HikariCP default) |

### 1.3 Scalability

- The application is **horizontally scalable**: multiple instances can run behind a load balancer.
- Session state is stored in the database (idempotency key), so no in-memory session affinity is required.
- **Stateless design** — no JVM heap-based caching of payment state; each request is self-contained.

---

## 2. Availability

| Requirement | Target |
|-------------|--------|
| Uptime SLA | 99.9% (approx. 8.7 hours downtime/year) |
| Planned maintenance window | Announced 48 hours in advance |
| Failover success rate | > 95% (primary failure does not guarantee overall failure due to fallback provider) |

- The retry + failover mechanism (up to 3 attempts per provider) improves effective availability beyond what a single provider offers.
- No multi-region deployment is implemented in the current version.

---

## 3. Reliability

| Requirement | Description |
|-------------|-------------|
| Idempotency guarantee | Exactly-once semantics via idempotency key — duplicate requests return the same response without creating a new payment. |
| Crash recovery | If the service crashes mid-payment after DB persistence of INITIATED state, the payment will be in INITIATED or PROCESSING state on restart. An operator workflow is needed to recover PROCESSING payments (out of scope for v1). |
| Transaction boundary | Each `createPayment` call wraps DB writes and orchestration in a single `@Transactional` boundary. |
| Provider failure isolation | A failing provider does not crash the JVM; failures are caught and result in FAILED status. |

---

## 4. Security

| Requirement | Implementation |
|-------------|----------------|
| Input validation | All `PaymentRequest` fields validated via Jakarta Bean Validation (`@NotNull`, `@Positive`, `@NotBlank`). |
| Error responses | Stack traces are never returned to clients (`GlobalExceptionHandler` returns a sanitized `ErrorResponse`). |
| SQL injection | Mitigated via Spring Data JPA (parameterized queries). |
| Sensitive data | Payment data is stored in an in-memory H2 database (dev-only). Production must use a persistent, access-controlled database. |
| Health endpoint exposure | `show-details=when_authorized` — health details are not exposed publicly. |

---

## 5. Observability

| Requirement | Implementation |
|-------------|----------------|
| Structured logging | SLF4J with `logback` (JSON format recommended in production) |
| Metrics | Micrometer timers and counters on payment lifecycle events; queryable via `/actuator/metrics` |
| Health checks | `/actuator/health` — includes DB health indicator |
| Request tracing | Not implemented in v1 (consider adding Micrometer Tracing / OpenTelemetry for distributed tracing) |

---

## 6. Data Consistency

| Requirement | Description |
|-------------|-------------|
| Atomicity | DB writes and orchestration are within a single transaction. If the orchestrator throws an exception mid-flow, the entire transaction rolls back. |
| Ordering | Payment status transitions follow a strict order: `INITIATED → PROCESSING → SUCCESS/FAILED`. No out-of-order transitions are possible by design. |
| Durability | H2 in-memory DB provides no durability across restarts. For production, replace with PostgreSQL or MySQL with `write-ahead log` enabled. |

---

## 7. Maintainability

| Requirement | Target |
|-------------|--------|
| Code coverage | > 70% line coverage for service and orchestrator layers |
| Cyclomatic complexity | < 10 per method |
| Build | Must pass `mvn clean test` with zero failures on every commit |
| Dependency age | Dependencies older than 2 years should be reviewed at each release |

---

## 8. Compatibility

| Requirement | Description |
|-------------|-------------|
| Java version | Java 21 LTS |
| Spring Boot version | 4.x |
| Database | H2 (dev) — PostgreSQL/MySQL compatible in production |
| API format | JSON over HTTP |
| Browser / Client | Any HTTP/JSON-capable client (Postman, curl, mobile SDKs) |

---

## 9. Operational Limits

| Limit | Value | Reason |
|-------|-------|--------|
| Max payment amount | Not enforced | External limits or currency conversion should be enforced upstream |
| Retry attempts per provider | 3 (configurable via `MAX_RETRIES`) | Prevents infinite loops; balances latency vs. reliability |
| Concurrent payments | Bounded by DB connection pool size + thread pool | `spring-boot-starter-webmvc` default thread pool is adequate for v1 |

---

## 10. Compliance Considerations (Future)

These are noted for completeness but are not implemented in v1:

- **PCI-DSS**: Payment card data must never be stored in plain text; a certified vault or tokenization service is required.
- **Data retention**: Payment records should be retained per regulatory requirements (typically 5–7 years for financial data).
- **Audit logging**: All state transitions should be written to an immutable audit log.
