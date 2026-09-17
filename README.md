# 🚀 Remitlytics Core Engine & Dashboard

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen?style=for-the-badge&logo=spring) ![Next.js](https://img.shields.io/badge/Next.js-16.3-black?style=for-the-badge&logo=next.js) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql) ![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=for-the-badge&logo=docker)

**An event-driven, fault-tolerant financial backend and dashboard built for resilience and data integrity.**

Remitlytics isn't just another CRUD API. It is a strictly structured financial engine designed to handle multi-tenant invoice lifecycles, guarantee transaction integrity via a double-entry ledger, enforce strict API quotas, and communicate with the outside world through a fully decoupled, async webhook pipeline.

---

## 🧠 Architecture & Engineering Decisions

As a developer, here is what you actually care about—the hard systems problems this project solves:

### 1. Immutable Multi-Tenant Double-Entry Ledger
**The Problem:** Storing balances as mutable integers (`balance = balance + X`) leads to race conditions, lost updates, and zero auditability in financial systems.  
**The Solution:** An append-only double-entry ledger. Every financial event generates immutable, balanced `CREDIT` and `DEBIT` entries bounded by a strict PostgreSQL transaction. Balances are derived, never overwritten. Strict `tenant_id` isolation guarantees zero cross-contamination of ledger state.

### 2. Zero-Bypass Rate Limiting (Bucket4j)
**The Problem:** Placing rate limiters inside Spring MVC Controllers wastes thread pools on rejected requests and fails to protect against brute-force DDoS.  
**The Solution:** A `Bucket4j` token-bucket rate limiter implemented as a Servlet `Filter` with `@Order(1)`. Requests are evaluated (300 req/min limit) before they ever hit the Spring MVC DispatcherServlet. It handles CORS preflight bypasses natively and injects standardized `X-RateLimit-Remaining` and `429 Too Many Requests` headers.

### 3. The 40ms Webhook (Decoupling with Events)
**The Problem:** Waiting for third-party webhook endpoints to respond blocks the main application thread, causing latency spikes and risking database connection pool exhaustion.  
**The Solution:** Invoice state changes publish a `WebhookDispatchEvent`. An `@Async` listener catches this and processes the outbound HTTP payload in a separate background thread pool, allowing the core API to return a `200 OK` to the client in under 50ms.

### 4. Transaction-Bound Side Effects
**The Problem:** If a database transaction rolls back (e.g., a ledger constraint fails), but a webhook event was already fired, you notify external systems of a phantom payment.  
**The Solution:** Webhook dispatch events are bound to `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. The system guarantees that external services are *only* notified if the core financial state is safely persisted to disk.

### 5. Fault Tolerance, Dead Letter Queues (DLQ), and Replays
**The Problem:** Networks are unreliable. If a client's webhook endpoint is down, the event is lost forever.  
**The Solution:** A custom retry state machine with exponential backoff. Failed events are safely parked in a PostgreSQL-backed `webhook_delivery_logs` table. The API exposes a `POST /api/v1/admin/webhooks/{id}/retry` endpoint to allow administrators to manually trigger replays for any failed webhook delivery.

### 6. Automated Due-Date Sweepers
**The Problem:** Invoices that sit unpaid past their due date need their status updated to `OVERDUE` without manual intervention.  
**The Solution:** A Spring `@Scheduled` cron worker periodically sweeps the database, automatically updating expired `SENT` invoices and emitting the corresponding state-change events.

---

## 🛠️ The Tech Stack

*   **Core Backend:** Java 21, Spring Boot 3.3.4
*   **Frontend UI:** Next.js 16.3 (TypeScript, Turbopack), React
*   **Data Layer:** PostgreSQL 16, Hibernate / Spring Data JPA, HikariCP
*   **Database Migrations:** Flyway
*   **Infrastructure:** Docker Compose, Multi-stage Alpine containerization (non-root `remit` user)
*   **Security & Traffic:** Custom API Key Authentication, Bucket4j Token-Bucket Rate Limiting

---

## 🚀 Quick Start (Docker FTW)

No complex local setups or JVM configurations. You are 60 seconds away from running the engine.

```bash
# 1. Clone the repo
git clone [https://github.com/yourusername/remitlytics.git](https://github.com/yourusername/remitlytics.git)
cd remitlytics

# 2. Spin up the Postgres DB, Spring Boot API, and Next.js Frontend
docker compose up --build -d

# 3. Follow the backend initialization logs
docker compose logs -f core-engine

Frontend Dashboard: `http://localhost:3000`
The API is now alive at `http://localhost:8080`.

---

## 📡 Core API Flow

The system strictly enforces the invoice state machine: DRAFT ➔ SENT ➔ PAID / OVERDUE.

### 1. Create a Draft Invoice
POST /api/v1/invoices
Content-Type: application/json
X-API-KEY: your_api_key

### 2. Mark as Sent
PATCH /api/v1/invoices/{id}/status
X-API-KEY: your_api_key
{
  "status": "SENT"
}

### 3. Process Payment (Triggers Ledger Append & Async Webhook)
PATCH /api/v1/invoices/{id}/status
X-API-KEY: your_api_key
{
  "status": "PAID"
}

### 4. Manually Replay a Failed Webhook Delivery
POST /api/v1/admin/webhooks/{webhook_log_id}/retry
X-API-KEY: your_api_key
