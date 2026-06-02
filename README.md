# API Versioning Demo — Stripe-Style Date-Based Versioning

This project demonstrates how to implement **Stripe-style date-based API versioning** in a Spring Boot 3.x application. Rather than versioning via URL paths (`/v1/`, `/v2/`) or `Accept` headers, each API call carries a version date (e.g. `Stripe-Version: 2024-01-15`) that pins the client to a specific behavioral snapshot of the API.

---

## The Three-Layer Architecture

Versioning logic is split into three distinct layers, keeping the core business logic clean and isolated from compatibility concerns.

```
Incoming Request
      │
      ▼
┌─────────────────────────────┐
│  Layer 1: Request           │
│  Compatibility              │  ← Normalize old request shapes to current
│  (RequestTransformer)       │    internal representation
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  Layer 2: Core API Logic    │  ← Pure business logic, version-unaware
│  (Service / Domain)         │    Always works on the latest internal model
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│  Layer 3: Response          │
│  Compatibility              │  ← Shape the response to match what the
│  (ResponseTransformer)      │    client's version date expects
└─────────────────────────────┘
      │
      ▼
Outgoing Response
```

### Layer 1 — Request Compatibility

Translates incoming request payloads from older API versions into the current internal representation. A client pinned to `2023-06-01` may send a field named `amount_cents`; the transformer rewrites this to `amountInCents` before the service ever sees it.

**Goal:** the service layer only ever deals with the latest request shape.

### Layer 2 — Core API Logic

Plain Spring services and domain objects. This layer has zero awareness of API versions. It receives a normalized request, executes business logic, and returns an internal response model.

**Goal:** business logic is never cluttered with version conditionals.

### Layer 3 — Response Compatibility

Translates the internal response model back into the shape the calling client expects for its pinned version. A client on `2023-06-01` may expect a flat `customer_name` field where newer clients receive a nested `customer.name` object.

**Goal:** old clients keep getting exactly what they were built against; new clients get the improved shape.

---

## How Version Dates Work (Stripe's Approach)

| Concept | Detail |
|---|---|
| **Version header** | `Stripe-Version: YYYY-MM-DD` sent on every request |
| **Default version** | The version the account was created on (stored server-side) |
| **Changelog** | Each breaking change is given a date; clients opt in by updating their pinned date |
| **Backwards safety** | Clients that never update their pinned date continue to work unchanged |

This means the API can evolve aggressively — adding fields, renaming things, restructuring responses — without breaking any existing integration.

---

## Project Structure

```
src/main/java/com/demo/versioning/
├── ApiVersioningDemoApplication.java
├── version/                  # Version resolution infrastructure
│   ├── ApiVersion.java       # Value object wrapping a version date
│   └── ApiVersionResolver.java
├── transformer/              # Layers 1 & 3
│   ├── RequestTransformer.java
│   └── ResponseTransformer.java
└── api/                      # Controllers + service (Layer 2)
    ├── controller/
    └── service/
```

---

## Running the App

```bash
./mvnw spring-boot:run
```

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

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
