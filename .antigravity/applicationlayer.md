---
trigger: always_on
---

# Application Layer Rules

## Application Layer Responsibilities
- MUST coordinate workflows between domain and infrastructure.
- MUST NOT contain SQL, HTTP client code, or persistence logic.
- MUST handle transactions where necessary.
- MUST maintain strict boundaries to ensure Clean Architecture.

## DTO Rules
- MUST use dedicated DTOs for:
  - API Requests
  - API Responses
  - Application Commands/Queries
- MUST NOT reuse JPA entities as DTOs.

## Mapping Rules
- MUST use MapStruct or explicit mappers.
- MUST NOT use reflection-based or auto-magic mappers.
- MUST document mapping rules for complex objects.

## Command/Query Rules
- Commands MUST represent state-changing operations.
- Queries MUST represent read-only operations.
