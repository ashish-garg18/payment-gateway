---
trigger: always_on
---

# Development Workflow Rules

## Branching Strategy
- MUST use trunk-based or GitHub Flow.
- Feature branches MUST be short-lived.
- MUST push changes frequently.

## Pull Requests
- PRs MUST include clear descriptions.
- PRs MUST include tests for any changed logic.
- PRs MUST be ≤ 500 LOC unless justified.
- MUST NOT merge failing builds.

## CI/CD
- MUST include:
  - code formatting
  - static analysis (SAST)
  - unit tests
  - integration tests
- MUST fail builds on:
  - test failures
  - lint issues
  - coverage drops

## Coding Practices
- MUST maintain consistent code quality.
- MUST simplify code when possible.
- MUST remove dead code immediately.
