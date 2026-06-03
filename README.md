# API Versioning Demo — Stripe-Style Date-Based Versioning

This project demonstrates how to implement **Stripe-style date-based API versioning** in a Spring Boot 3.x application. Rather than versioning via URL paths (`/v1/`, `/v2/`) or `Accept` headers, each API call carries a version date (e.g. `Stripe-Api-Version: 2024-03-10`) that pins the client to a specific behavioral snapshot of the API.

---

## The Three-Layer Architecture

Versioning logic is split into three distinct layers, keeping the core business logic clean and isolated from compatibility concerns.

```
Incoming Request (version-shaped payload)
      │
      ▼
┌─────────────────────────────┐
│  Layer 1: Request Gate      │
│  Chain (gate/)              │  ← Normalize old request shapes forward
│  RequestGateChain           │    to the current internal representation
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  Layer 2: Core API Logic    │  ← Pure business logic, version-unaware
│  (core/ — Service / Domain) │    Always works on the latest (2024) model
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  Layer 3: Response          │
│  Transformer Chain          │  ← Downgrade the response backward to
│  (transformer/)             │    match what the caller's version expects
└─────────────────────────────┘
      │
      ▼
Outgoing Response (version-shaped payload)
```

### Layer 1 — Request Gate Chain

Each `RequestGate` carries a `fromVersion → toVersion` pair. When a request arrives, `RequestGateChain` runs all gates whose `fromVersion >= callerVersion`, walking the payload **forward** through every version upgrade it hasn't seen yet.

- A 2020 caller sends flat snake_case fields → both gates run → payload is normalized to the 2024 shape.
- A 2022 caller sends a nested `billing` map → only the 2022→2024 gate runs.
- A 2024 caller → no gates run; the payload is already current.

**Goal:** the service layer only ever deals with the latest request shape.

### Layer 2 — Core API Logic

Plain Spring services and domain objects. This layer has zero awareness of API versions. It receives a fully normalized request, executes business logic, and returns an internal response model.

**Goal:** business logic is never cluttered with version conditionals.

### Layer 3 — Response Transformer Chain

Each `ResponseTransformer` carries a `fromVersion → toVersion` pair. After the service returns, `ResponseTransformerChain` runs all transformers whose `toVersion >= targetVersion`, walking the response **backward** through every version downgrade needed to reach the caller's pinned version.

- A 2020 caller → both transformers run (2024→2022, then 2022→2020).
- A 2022 caller → only the 2024→2022 transformer runs.
- A 2024 caller → no transformers run.

**Goal:** old clients keep getting exactly what they were built against; new clients get the improved shape.

---

## How Version Dates Work (Stripe's Approach)

| Concept | Detail |
|---|---|
| **Version header** | `Stripe-Api-Version: YYYY-MM-DD` sent on every request |
| **Default version** | `2020-01-01` (configurable via `api.versioning.default-version`) |
| **Changelog** | Each breaking change is assigned a date; clients opt in by updating their pinned date |
| **Backwards safety** | Clients that never update their pinned date continue to work unchanged |

This means the API can evolve aggressively — renaming fields, restructuring payloads — without breaking any existing integration.

---

## Project Structure

```
src/main/java/com/demo/versioning/
├── ApiVersioningDemoApplication.java
├── version/                  # Version resolution infrastructure
│   ├── ApiVersion.java       # Enum: V_2020_01_01, V_2022_06_15, V_2024_03_10
│   ├── VersionManifest.java  # Feature-gate map per version
│   └── VersionContext.java   # ThreadLocal<ApiVersion>
├── request/                  # HTTP filter
│   └── ApiVersionFilter.java # Resolves header → ApiVersion → VersionContext
├── gate/                     # Layer 1: Request normalization (forward)
│   ├── RequestGate.java              # Interface: open(), fromVersion(), toVersion()
│   ├── RequestGateChain.java         # Orchestrates gates in fromVersion order
│   ├── V2020To2022RequestGate.java   # Upgrades 2020 flat shape → 2022
│   └── V2022To2024RequestGate.java   # Upgrades 2022 billing map → 2024 billingDetails
├── core/                     # Layer 2: Business logic (version-unaware)
│   ├── PaymentRequest.java
│   ├── PaymentResponse.java
│   ├── BillingDetails.java
│   └── PaymentService.java
├── transformer/              # Layer 3: Response downgrade (backward)
│   ├── ResponseTransformer.java          # Interface: transform(), fromVersion(), toVersion()
│   ├── ResponseTransformerChain.java     # Orchestrates transformers in reverse order
│   ├── V2024To2022ResponseTransformer.java
│   └── V2022To2020ResponseTransformer.java
├── exception/
│   └── GlobalExceptionHandler.java
└── controller/
    └── PaymentController.java
```

---

## Running the App

```bash
mvn spring-boot:run
```

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## curl Examples

Each version sends the payload shaped as that version's client would send it.

---

**Version `2020-01-01` — flat snake_case request, legacy cents field in response**
```bash
curl -s -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -H "Stripe-Api-Version: 2020-01-01" \
  -d '{
    "customer_id": "cust_abc123",
    "amount": 49.99,
    "billing_name": "Jane Doe",
    "billing_email": "jane@example.com",
    "billing_address": "123 Main St",
    "billing_city": "New York",
    "billing_country": "US"
  }' | jq .
```
```json
{
  "payment_id": "…",
  "amount_cents": 4999,
  "status": "succeeded",
  "createdAt": "…",
  "billing_name": "Jane Doe",
  "billing_email": "jane@example.com",
  "billing_address": "123 Main St"
}
```

---

**Version `2022-06-15` — nested `billing` map, currency present**
```bash
curl -s -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -H "Stripe-Api-Version: 2022-06-15" \
  -d '{
    "customerId": "cust_abc123",
    "amount": 49.99,
    "currency": "USD",
    "billing": {
      "name": "Jane Doe",
      "email": "jane@example.com",
      "address": "123 Main St"
    }
  }' | jq .
```
```json
{
  "paymentId": "…",
  "amount": 49.99,
  "currency": "USD",
  "status": "succeeded",
  "createdAt": "…",
  "billing": {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "address": "123 Main St"
  }
}
```

---

**Version `2024-03-10` — nested `billingDetails`, currency present**
```bash
curl -s -X POST http://localhost:8080/v1/payments \
  -H "Content-Type: application/json" \
  -H "Stripe-Api-Version: 2024-03-10" \
  -d '{
    "customerId": "cust_abc123",
    "amount": 49.99,
    "currency": "USD",
    "billingDetails": {
      "name": "Jane Doe",
      "email": "jane@example.com",
      "addressLine1": "123 Main St",
      "city": "New York",
      "country": "US"
    }
  }' | jq .
```
```json
{
  "paymentId": "…",
  "amount": 49.99,
  "currency": "USD",
  "status": "succeeded",
  "createdAt": "…",
  "billingDetails": {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "addressLine1": "123 Main St",
    "city": "New York",
    "country": "US"
  }
}
```

---

**List all supported versions**
```bash
curl -s http://localhost:8080/v1/versions | jq .
```

**Resolve the version for a given header**
```bash
curl -s http://localhost:8080/v1/versions/current \
  -H "Stripe-Api-Version: 2022-06-15" | jq .
```

---

## Tech Stack

| Dependency | Purpose |
|---|---|
| Spring Boot 3.3 | Application framework |
| Java 21 | Runtime (virtual threads ready) |
| spring-boot-starter-web | REST endpoints |
| spring-boot-starter-validation | Bean Validation (JSR-380) |
| Lombok | Boilerplate reduction |
| springdoc-openapi-starter-webmvc-ui | Swagger UI / OpenAPI 3 |
