---
trigger: always_on
---

# Spring Boot Rules

Application Development Rules: Spring Boot (Synchronous)

## General Rules
- All applications MUST use the Spring Boot framework.
- All REST APIs MUST be synchronous (blocking).  
  MUST use **spring-boot-starter-web**.  
  MUST NOT use **spring-boot-starter-webflux**.
- Gradle MUST be used for dependency management.
- Dependency versions MUST follow Spring Boot BOM recommendations.
- Application configuration MUST be externalized using `application.yml` or environment variables.
- Sensitive information MUST NOT be hardcoded under any circumstance.

## Spring Boot Specifics
- Use Spring Boot starters for standard functionality.
- Use proper annotations: `@SpringBootApplication`, `@RestController`, `@Service`.
- Utilize auto-configuration; avoid redundant configurations.
- Global exception handling MUST be implemented via `@ControllerAdvice` and `@ExceptionHandler`.
- Controllers MUST remain thin; NO BUSINESS LOGIC in controllers.

## Configuration and Properties
- MUST use `application.yml` (NOT `.properties`).
- MUST use Spring Profiles for environment separation.
- MUST use `@ConfigurationProperties` for typed configuration.
- MUST NOT commit secrets.
- MUST use environment variables in UPPER_SNAKE_CASE.
- MUST keep configuration grouped by domain concern.

## Dependency Injection and IoC
- MUST use constructor injection only.
- Bean scopes MUST be appropriate; default to singleton.
- MUST annotate components with correct stereotypes.
- Circular dependencies MUST be avoided.
- Complex DI MUST explicitly use `@Qualifier`.

## Testing
- MUST use JUnit 5 + Spring Boot Test.
- MUST follow AAA (Arrange/Act/Assert) pattern.
- MUST use TestContainers for test databases.
- MUST test services and domain logic thoroughly.
- MUST NOT write tests for Controller layer.
- MUST NOT write tests for Repository layer.
- MUST create dedicated `application-test.yml` for testing.

## Security
- DO NOT USE Spring Security.
- DO NOT USE any form of authentication or authorization.
- DO NOT import or reference any Spring Security dependencies.

## Logging and Monitoring
- MUST use SLF4J + Logback.
- MUST NOT log sensitive information.
- MUST set sensible log levels: ERROR, WARN, INFO, DEBUG.
- MUST enable Spring Boot Actuator for health and metrics.
- MUST use JSON structured logs.
- MUST use correlation IDs for tracing.

## Error Handling
- MUST implement consistent exception handling.
- MUST standardize error response structure.
- MUST include request path in logs.
- MUST generate unique error IDs for unknown exceptions.
- MUST handle domain-specific and framework-specific exceptions gracefully.
- Field-level validation errors MUST follow a structured output format.
