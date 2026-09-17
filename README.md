# 🚀 Remitlytics — Core Engine & Dashboard

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen?style=for-the-badge&logo=spring)
![Next.js](https://img.shields.io/badge/Next.js-16.3-black?style=for-the-badge&logo=next.js)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=for-the-badge&logo=docker)

**An event-driven, fault-tolerant financial backend and dashboard built for resilience and data integrity.**

Remitlytics isn't just another CRUD API. It's a strictly structured financial engine designed to handle multi-tenant invoice lifecycles, guarantee transaction integrity via a double-entry ledger, enforce strict API quotas, and communicate with the outside world through a fully decoupled, async webhook pipeline.

---

## 🧠 Architecture & Engineering Decisions

Here's what actually matters — the hard systems problems this project solves.

### 1. Immutable Multi-Tenant Double-Entry Ledger
**The problem:** Storing balances as mutable integers (`balance = balance + x`) leads to race conditions, lost updates, and zero auditability in financial systems.

**The solution:** An append-only double-entry ledger. Every financial event generates immutable, balanced `CREDIT` and `DEBIT` entries bounded by a strict PostgreSQL transaction. Balances are derived, never overwritten. Strict `tenant_id` isolation guarantees zero cross-contamination of ledger state.

### 2. Zero-Bypass Rate Limiting (Bucket4j)
**The problem:** Placing rate limiters inside Spring MVC controllers wastes thread pools on requests that get rejected anyway, and fails to protect against brute-force DDoS.

**The solution:** A `Bucket4j` token-bucket rate limiter implemented as a Servlet `Filter` with `@Order(1)`. Requests are evaluated (300 req/min limit) before they ever reach the Spring MVC `DispatcherServlet`. It handles CORS preflight bypasses natively and injects standardized `X-RateLimit-Remaining` and `429 Too Many Requests` responses.

### 3. The 40ms Webhook (Decoupling with Events)
**The problem:** Waiting for third-party webhook endpoints to respond blocks the main application thread, causing latency spikes and risking database connection pool exhaustion.

**The solution:** Invoice state changes publish a `WebhookDispatchEvent`. An `@Async` listener catches this and processes the outbound HTTP payload on a separate background thread pool, letting the core API return `200 OK` to the client in under 50ms.

### 4. Transaction-Bound Side Effects
**The problem:** If a database transaction rolls back (e.g. a ledger constraint fails) but a webhook event already fired, you've notified external systems of a phantom payment.

**The solution:** Webhook dispatch events are bound to `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. External services are only notified once the core financial state is safely persisted to disk.

### 5. Fault Tolerance, Dead Letter Queues (DLQ), and Replays
**The problem:** Networks are unreliable. If a client's webhook endpoint is down, the event is lost forever.

**The solution:** A custom retry state machine with exponential backoff. Failed events are safely parked in a PostgreSQL-backed `webhook_delivery_logs` table. The API exposes a `POST /api/v1/admin/webhooks/{id}/retry` endpoint so admins can manually trigger replays for any failed delivery.

### 6. Automated Due-Date Sweepers
**The problem:** Invoices that sit unpaid past their due date need their status updated to `OVERDUE` without manual intervention.

**The solution:** A Spring `@Scheduled` cron worker periodically sweeps the database, automatically updating expired `SENT` invoices and emitting the corresponding state-change events.

---

## 🛠️ Tech Stack

| Layer | Tech |
|---|---|
| **Core backend** | Java 21, Spring Boot 3.3.4 |
| **Frontend UI** | Next.js 16.3 (TypeScript, Turbopack), React |
| **Data layer** | PostgreSQL 16, Hibernate / Spring Data JPA, HikariCP |
| **Migrations** | Flyway |
| **Infrastructure** | Docker Compose, multi-stage Alpine containers (non-root `remit` user) |
| **Security & traffic** | Custom API key authentication, Bucket4j token-bucket rate limiting |

---

## 🚀 Quick Start (Docker)

No complex local setup or JVM configuration needed. You're about 60 seconds away from running the engine.

```bash
# 1. Clone the repo
git clone https://github.com/Sidharth-Async/remitlytics-core.git
cd remitlytics

# 2. Spin up Postgres, the Spring Boot API, and the Next.js frontend
docker compose up --build -d

# 3. Follow the backend initialization logs
docker compose logs -f core-engine
```

- **Frontend dashboard:** `http://localhost:3000`
- **API:** `http://localhost:8080`
- **Frontend dashboard repo:** [`Sidharth-Async/remitlytics-frontend`](https://github.com/Sidharth-Async/remitlytics-frontend)

---

## 📡 Core API Flow

The system strictly enforces the invoice state machine:

```
DRAFT → SENT → PAID / OVERDUE
```

### 1. Create a draft invoice
```http
POST /api/v1/invoices
Content-Type: application/json
X-API-KEY: your_api_key
```

### 2. Mark as sent
```http
PATCH /api/v1/invoices/{id}/status
X-API-KEY: your_api_key

{
  "status": "SENT"
}
```

### 3. Process payment (triggers ledger append & async webhook)
```http
PATCH /api/v1/invoices/{id}/status
X-API-KEY: your_api_key

{
  "status": "PAID"
}
```

### 4. Manually replay a failed webhook delivery
```http
POST /api/v1/admin/webhooks/{webhook_log_id}/retry
X-API-KEY: your_api_key
```
