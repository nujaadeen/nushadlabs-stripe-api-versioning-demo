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
mvn test -Dtest=PaymentVersioningIntegrationTest#whenVersion2020_responseIsFlattened

# Skip tests during build
mvn clean package -DskipTests
```

Swagger UI: `http://localhost:8080/swagger-ui.html` (redirects to `/swagger-ui/index.html`).
OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

## Architecture

Clients send a `Stripe-Api-Version: YYYY-MM-DD` header. The server pins their experience to that behavioral snapshot. Version logic is isolated in three layers so the core service is never polluted with version conditionals.

```
Request
  │
  ▼ ApiVersionFilter          — resolves + stores ApiVersion in VersionContext (ThreadLocal)
  ▼ CachedBodyRequestWrapper  — wraps the request so the body can be read twice
  ▼ RequestCompatibilityInterceptor  — rejects deprecated fields not valid for the resolved version
  ▼ PaymentController         — delegates to PaymentService (version-unaware)
  ▼ ResponseCompatibilityService    — applies ordered ResponseTransformer chain
  │
Response
```

### Package Map

| Package | Responsibility |
|---|---|
| `version/` | `ApiVersion` enum, `VersionManifest` (feature gates), `VersionContext` (ThreadLocal) |
| `request/` | `ApiVersionFilter`, `CachedBodyRequestWrapper`, `RequestCompatibilityInterceptor`, `WebConfig` |
| `core/` | `PaymentRequest`, `PaymentResponse`, `BillingDetails` records/class, `PaymentService` |
| `response/` | `ResponseTransformer` interface + three implementations, `ResponseCompatibilityService` |
| `exception/` | `GlobalExceptionHandler` |
| `controller/` | `PaymentController` |

### version/ — Version Infrastructure

- **`ApiVersion`** — enum with three constants (`V_2020_01_01`, `V_2022_06_15`, `V_2024_03_10`), each holding a `LocalDate` and description. `fromString(String)` parses `YYYY-MM-DD`; throws `IllegalArgumentException` on unknown dates and `DateTimeParseException` on malformed ones — both caught by the filter.
- **`VersionManifest`** — `@Component` `EnumMap<ApiVersion, Set<String>>` of active feature gates per version. Gates drive all transformer decisions; never add version conditionals (`if version == X`) in business code — add a gate instead.
- **`VersionContext`** — `ThreadLocal<ApiVersion>` with static `get/set/clear`. Set by `ApiVersionFilter` before the chain runs; cleared in a `finally` block. Always `null` outside a request (e.g. in `GlobalExceptionHandler` for filter-level errors).

### request/ — Request Compatibility Layer

- **`ApiVersionFilter`** (`OncePerRequestFilter`, `@Component`) reads `Stripe-Api-Version`, falls back to `${api.versioning.default-version}` (`2020-01-01`), resolves the enum, and wraps the request in `CachedBodyRequestWrapper`. Invalid/unknown version dates return a structured `400` directly (bypassing `GlobalExceptionHandler` since filters run before the dispatcher).
- **`CachedBodyRequestWrapper`** reads the `InputStream` once into a `byte[]` on construction, stores the decoded string as request attribute `cachedRequestBody`, and serves a fresh `ByteArrayInputStream` on every `getInputStream()` call. Required so both the interceptor and the Jackson deserializer can read the body.
- **`RequestCompatibilityInterceptor`** (`HandlerInterceptor`) checks for `legacy_amount` in query/form params and in the cached JSON body. Returns `400` if the field is present but the resolved version's `legacy_amount_field` gate is inactive.
- **`WebConfig`** registers `RequestCompatibilityInterceptor`. The filter auto-registers because it's a `@Component`.

### core/ — Business Logic (Version-Unaware)

`PaymentService.process(PaymentRequest)` always works on the latest internal model. It generates a UUID `paymentId`, sets `status = "succeeded"`, sets `createdAt = Instant.now()`, and returns a `PaymentResponse`. It imports nothing from `version.*`.

### response/ — Response Compatibility Layer

`ResponseCompatibilityService` converts `PaymentResponse → Map<String, Object>` via `ObjectMapper.convertValue()`, then pipes the map through all `ResponseTransformer` beans in `@Order` order:

| Order | Class | Gate checked | Action |
|---|---|---|---|
| 1 | `CurrencyCodeTransformer` | `multi_currency` absent → remove `currency` key |
| 2 | `FlattenBillingTransformer` | `flat_billing` present → promote `billingDetails.*` to top-level `billing_*` keys |
| 3 | `LegacyAmountTransformer` | `legacy_amount_field` present → add `legacy_amount` = `amount × 100` (int) |

To add a new transformer: create a `@Component` implementing `ResponseTransformer`, give it the next `@Order`, and add its gate to the relevant versions in `VersionManifest`.

### Feature Gate Matrix

| Version | `flat_billing` | `legacy_amount_field` | `nested_billing_preview` | `nested_billing` | `multi_currency` |
|---|---|---|---|---|---|
| `2020-01-01` | ✓ | ✓ | | | |
| `2022-06-15` | ✓ | | ✓ | | |
| `2024-03-10` | | | | ✓ | ✓ |

### Error Response Shape

All errors (from `GlobalExceptionHandler` and directly from filters/interceptors) return:
```json
{ "error": { "code": "...", "message": "...", "version": "..." } }
```
`version` is `VersionContext.get().name()` when available, `"unknown"` for filter-level errors (before the version is resolved).

### Adding a New Breaking Change

1. Add a new constant to `ApiVersion` with the change date.
2. Add its gate set to `VersionManifest`.
3. Add a `@Component @Order(N) ResponseTransformer` (and/or a check in `RequestCompatibilityInterceptor`) that activates on the new gate.
4. Change `PaymentService` / domain models freely — this is always the latest shape.

No existing client breaks; they stay pinned to their old date and hit the compatibility branches transparently.
