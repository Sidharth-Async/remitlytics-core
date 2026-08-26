# 🚀 Remitlytics Core Engine

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen?style=for-the-badge&logo=spring) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql) ![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=for-the-badge&logo=docker)

**An event-driven, fault-tolerant financial backend built for resilience.**

Remitlytics isn't just another CRUD API. It is a strictly structured financial engine designed to handle invoice lifecycles, guarantee transaction integrity via a double-entry ledger, and communicate with the outside world through a fully decoupled, async webhook pipeline.

---

## 🧠 Architecture & Engineering Decisions

As a developer, here is what you actually care about—the hard problems this API solves:

### 1. The 40ms Webhook (Decoupling with Events)
**The Problem:** Waiting for third-party webhook endpoints to respond blocks the main application thread, causing latency spikes (600ms+) and risking connection pooling exhaustion.  
**The Solution:** Invoice state changes publish a `WebhookDispatchEvent`. An `@Async` listener catches this and processes the HTTP payload in a separate background thread, returning a `200 OK` to the client in under 50ms.

### 2. Transaction-Bound Side Effects
**The Problem:** If a database transaction rolls back, but an event was already fired, you send a webhook for a phantom payment.  
**The Solution:** Webhook events are bound to `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. The system guarantees that external systems are *only* notified if the financial data is safely persisted to disk.

### 3. Fault Tolerance & Dead Letter Queues (DLQ)
**The Problem:** Networks are unreliable. If a client's webhook endpoint is down, the event is lost forever.  
**The Solution:** A custom retry loop with exponential backoff. If max retries are exceeded, the event is safely parked in a PostgreSQL-backed **Dead Letter Queue (DLQ)** with the exact failure reason (e.g., `Connection refused`), awaiting a background cron job sweeper for recovery.

### 4. Immutable Double-Entry Ledger
Financial records are sacred. When an invoice transitions to `PAID`, the system automatically triggers a strict double-entry ledger service, ensuring credits and debits always balance.

---

## 🛠️ The Tech Stack

*   **Core:** Java 21, Spring Boot 3.3.4
*   **Data Layer:** PostgreSQL 16, Hibernate / Spring Data JPA, HikariCP
*   **Database Migrations:** Flyway
*   **Infrastructure:** Docker, Docker Compose
*   **Security:** Custom API Key Authentication, Bucket4j Rate Limiting

---

## 🚀 Quick Start (Docker FTW)

No complex local setups. If you have Docker installed, you are 60 seconds away from running the engine.

```bash
# 1. Clone the repo
git clone https://github.com/yourusername/finance-backend-service.git
cd finance-backend-service

# 2. Spin up the Postgres DB and Spring Boot API
docker compose up --build -d

# 3. Follow the logs to see Flyway migrations and Async threads in action
docker compose logs -f core-engine
```

The API is now alive at `http://localhost:8080`.

---

## 📡 Core API Flow

The system strictly enforces the state machine: `DRAFT` ➔ `SENT` ➔ `PAID`.

```http
### 1. Create a Draft Invoice
POST /api/v1/invoices
Content-Type: application/json

### 2. Mark as Sent
PATCH /api/v1/invoices/{id}/status
{
  "status": "SENT"
}

### 3. Process Payment (Triggers Ledger & Async Webhook)
PATCH /api/v1/invoices/{id}/status
{
  "status": "PAID"
}
```
