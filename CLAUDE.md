# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MyTestClass

# Run a single test method
./mvnw test -Dtest=MyTestClass#myMethod

# Skip tests during build
./mvnw clean package -DskipTests
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the app is running.

## Architecture

This project implements Stripe-style date-based API versioning. Clients send a `Stripe-Version: YYYY-MM-DD` header; the server pins their experience to that behavioral snapshot. Version logic is isolated in three layers so the core service is never polluted with version conditionals.

### Three-Layer Flow

```
Request → [Layer 1: RequestTransformer] → [Layer 2: Service] → [Layer 3: ResponseTransformer] → Response
```

- **Layer 1 (`transformer/`)** — Normalizes incoming request payloads from old shapes to the current internal model before the service sees them.
- **Layer 2 (`api/service/`)** — Pure business logic, completely version-unaware. Always operates on the latest internal model.
- **Layer 3 (`transformer/`)** — Reshapes the internal response to match the shape the client's pinned version expects.

### Version Infrastructure (`version/`)

- `ApiVersion` — Value object wrapping a `LocalDate`. Used throughout as the canonical representation of a client's pinned version.
- `ApiVersionResolver` — Reads the `Stripe-Version` header from each request and resolves it to an `ApiVersion`. Falls back to the server's configured default when the header is absent.

### Adding a New Breaking Change

1. Pick a new date (the "version date" for this change).
2. Add a branch in the relevant `RequestTransformer` to normalize the old request shape when `apiVersion.isBefore(newDate)`.
3. Change the service and internal models freely — this is the new canonical shape.
4. Add a branch in the relevant `ResponseTransformer` to downgrade the response when `apiVersion.isBefore(newDate)`.
5. Document the new date in the API changelog.

No existing client is broken; they stay pinned to their old date and hit the compatibility branches transparently.
