# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

No `mvnw` wrapper is present — use the system `mvn` directly.

```bash
# Build
mvn clean package

# Run
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=PaymentVersioningIntegrationTest

# Run a single test method
mvn test -Dtest=PaymentVersioningIntegrationTest#whenVersion2020_bothGatesRun_bothTransformersRun

# Skip tests during build
mvn clean package -DskipTests
```

Swagger UI: `http://localhost:8080/swagger-ui.html` (redirects to `/swagger-ui/index.html`).
OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

## Architecture

Clients send a `Stripe-Api-Version: YYYY-MM-DD` header. The server pins their experience to that behavioral snapshot. Versioning logic lives entirely in the gate chain (request normalization) and transformer chain (response downgrade); the core service is completely version-unaware.

```
Request (version-shaped payload)
  │
  ▼ ApiVersionFilter          — resolves + stores ApiVersion in VersionContext (ThreadLocal)
  ▼ PaymentController
      ├─ RequestGateChain.open(rawBody, callerVersion)
      │    Runs each RequestGate whose fromVersion >= callerVersion,
      │    upgrading the payload forward to the current (2024) shape.
      ├─ PaymentService.process(request)   — always sees the 2024-shape request
      └─ ResponseTransformerChain.transform(response, targetVersion)
           Runs each ResponseTransformer whose toVersion >= targetVersion,
           downgrading the response backward to the caller's expected shape.
  │
Response (version-shaped payload)
```

### Package Map

| Package | Responsibility |
|---|---|
| `version/` | `ApiVersion` enum, `VersionManifest` (feature gates), `VersionContext` (ThreadLocal) |
| `request/` | `ApiVersionFilter` |
| `gate/` | `RequestGate` interface, `RequestGateChain`, forward-migration gate implementations |
| `transformer/` | `ResponseTransformer` interface, `ResponseTransformerChain`, backward-transformation implementations |
| `core/` | `PaymentRequest`, `PaymentResponse`, `BillingDetails` records/class, `PaymentService` |
| `exception/` | `GlobalExceptionHandler` |
| `controller/` | `PaymentController` |

### version/ — Version Infrastructure

- **`ApiVersion`** — enum with three constants (`V_2020_01_01`, `V_2022_06_15`, `V_2024_03_10`), each holding a `LocalDate` and description. `fromString(String)` parses `YYYY-MM-DD`; throws `IllegalArgumentException` on unknown dates and `DateTimeParseException` on malformed ones — both caught by the filter. `ordered()` returns versions sorted ascending by date; used by both chains for stable sort.
- **`VersionManifest`** — `@Component` `EnumMap<ApiVersion, Set<String>>` of active feature gates per version. No longer used by the gate/transformer chains (they compare versions directly), but kept for any gate logic that needs it.
- **`VersionContext`** — `ThreadLocal<ApiVersion>` with static `get/set/clear`. Set by `ApiVersionFilter` before the chain runs; cleared in a `finally` block. Always `null` outside a request (e.g. in `GlobalExceptionHandler` for filter-level errors).

### request/ — Request Compatibility Layer

- **`ApiVersionFilter`** (`OncePerRequestFilter`, `@Component`) reads `Stripe-Api-Version`, falls back to `${api.versioning.default-version}` (`2020-01-01`), resolves the enum, and stores it in `VersionContext`. Invalid/unknown version dates return a structured `400` directly (bypassing `GlobalExceptionHandler` since filters run before the dispatcher).

### gate/ — Request Gate Chain

`RequestGateChain` sorts gates ascending by `fromVersion()` on startup (via `@PostConstruct`), then for each request runs the gates where `gate.fromVersion() >= callerVersion`. This progressively upgrades the raw body from the caller's version to the current internal shape.

| Gate | fromVersion → toVersion | What it does |
|---|---|---|
| `V2020To2022RequestGate` | `2020-01-01 → 2022-06-15` | Renames `customer_id` → `customerId`; promotes flat `billing_*` fields into a nested `billing` map |
| `V2022To2024RequestGate` | `2022-06-15 → 2024-03-10` | Renames nested `billing` map → `billingDetails` with camelCase keys (`address` → `addressLine1`) |

To add a new gate: create a `@Component` implementing `RequestGate`, set its `fromVersion()`/`toVersion()` pair, and implement `open()`.

### core/ — Business Logic (Version-Unaware)

`PaymentService.process(PaymentRequest)` always works on the 2024-shape (current) model. It generates a UUID `paymentId`, sets `status = "succeeded"`, sets `createdAt = Instant.now()`, and returns a `PaymentResponse`. It imports nothing from `version.*` or `gate.*`.

### transformer/ — Response Transformer Chain

`ResponseTransformerChain` converts `PaymentResponse → Map<String, Object>` via `ObjectMapper.convertValue()`, sorts transformers descending by `fromVersion()` (latest first, so they walk 2024→2022→2020), then runs each transformer where `transformer.toVersion() >= targetVersion`. This progressively downgrades the internal response to the caller's expected shape.

| Transformer | fromVersion → toVersion | What it does |
|---|---|---|
| `V2024To2022ResponseTransformer` | `2024-03-10 → 2022-06-15` | Converts `billingDetails` → nested `billing` map with `name`, `email`, `address` |
| `V2022To2020ResponseTransformer` | `2022-06-15 → 2020-01-01` | Renames `paymentId` → `payment_id`; converts `amount` → `amount_cents` (×100 as long); removes `currency`; promotes `billing.*` to flat `billing_*` keys |

To add a new transformer: create a `@Component` implementing `ResponseTransformer` (`transformer` package), set its `fromVersion()`/`toVersion()` pair, and implement `transform()`.

### Version Shapes At-a-Glance

| Version | Request shape (incoming) | Response shape (outgoing) |
|---|---|---|
| `2020-01-01` | flat snake_case: `customer_id`, `billing_name`, … | flat: `payment_id`, `amount_cents`, `billing_name`, … no `currency` |
| `2022-06-15` | camelCase id, nested `billing: {name, email, address}` | `paymentId`, `amount`, `currency`, nested `billing: {name, email, address}` |
| `2024-03-10` | camelCase id, nested `billingDetails: {name, email, addressLine1, city, country}` | `paymentId`, `amount`, `currency`, nested `billingDetails: {…}` |

### Error Response Shape

All errors (from `GlobalExceptionHandler` and directly from the filter) return:
```json
{ "error": { "code": "...", "message": "...", "version": "..." } }
```
`version` is `VersionContext.get().name()` when available, `"unknown"` for filter-level errors (before the version is resolved).

### Adding a New Breaking Change

1. Add a new constant to `ApiVersion` with the change date.
2. Add its gate set to `VersionManifest` (if any gate logic still uses it).
3. Add a `@Component RequestGate` in `gate/` that upgrades from the prior version to the new one.
4. Add a `@Component ResponseTransformer` in `transformer/` that downgrades from the new version to the prior one.
5. Change `PaymentService` / domain models freely — this is always the latest shape.

No existing client breaks; they stay pinned to their old date and the gate/transformer chains handle the translation transparently.
