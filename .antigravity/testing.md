---
trigger: always_on
---

# Testing Rules

## Testing Strategy
- MUST follow the testing pyramid:
  - Unit Tests (majority)
  - Integration Tests
  - API Contract Tests (where relevant)
- MUST use JUnit 5.

## Unit Tests
- MUST mock external dependencies.
- MUST test core business logic thoroughly.
- MUST follow AAA pattern.
- MUST NOT test framework behavior.
- MUST NOT test controllers.

## Integration Tests
- MUST use `@SpringBootTest` only when necessary.
- MUST use TestContainers for DB, cache, queue tests.
- MUST isolate integration test scenarios in dedicated test packages.

## Test Configuration
- MUST use `application-test.yml`.
- MUST isolate external integrations via profiles.
- MUST use test-specific beans where appropriate.

## Code Coverage
- MUST maintain ≥ 80% coverage.
- MUST focus coverage on business logic.
- MAY exclude generated code, DTOs, and configuration classes.
