---
trigger: always_on
---

# OpenAPI Rules

## General API Guidelines
- MUST follow API-first development.
- MUST maintain an OpenAPI 3.1+ specification.
- MUST update the spec before implementing new endpoints.

## Controller Requirements
- MUST follow OpenAPI contract exactly.
- MUST use validated DTOs for requests.
- MUST document all HTTP status codes.
- MUST document all error responses.
- MUST NOT expose internal or domain models directly.

## Versioning
- MUST use URI-based versioning (`/api/v1/...`).
- MUST NOT introduce breaking changes without version bump.

## Schema Rules
- Response and request objects MUST be fully described.
- MUST include examples for all request and response bodies.
- MUST define shared components for repeated schemas.

## Contract Testing
- MUST validate the API implementation against the spec.
