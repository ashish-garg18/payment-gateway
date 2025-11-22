---
trigger: always_on
---

# Java Rules

## General Language Rules
- All applications MUST use Java 17 or higher.
- Code MUST follow object-oriented design principles (SOLID).
- Code MUST be readable, maintainable, and predictable.
- MUST prefer composition over inheritance.
- MUST avoid deep class hierarchies.

## Coding Conventions
- MUST follow a consistent style (Google Java Style unless overridden).
- MUST use Records for pure immutable DTOs.
- MUST NOT overuse `var`; use only when readability improves.
- MUST write meaningful Javadoc on public classes & methods.

## Error Handling
- MUST NOT swallow exceptions.
- MUST wrap exceptions in meaningful domain/service exceptions.
- MUST avoid `Optional` as a method parameter.
- MUST use `Optional` only for optional return values.

## Performance Standards
- MUST reduce unnecessary object creation.
- Streams MAY be used but MUST remain readable and shallow.
- MUST avoid premature optimization unless justified.

## Immutability Rules
- Prefer immutable domain models.
- Collections MUST be unmodifiable when safe.
- DTOs MUST be immutable unless stated otherwise.
