Global Agent Rules

These rules apply to all agents operating in this repo.

1. General Engineering Principles

Follow SOLID, Clean Architecture, and DDD-derived separation.
Prefer composition over inheritance.
Code must be readable, maintainable, testable, observable.
Avoid unnecessary abstractions (no "clever" code).
All changes must improve or preserve quality, security, performance.

2. Code Generation Rules

Generate only compilable, idiomatic Java (>= Java 17 unless overridden).
Do NOT generate framework-specific code unless in a designated module.
Always generate imports; avoid unused dependencies.
When modifying code, provide minimal diffs instead of rewriting files.

3. Documentation & Artifacts

Update Javadoc, README sections, and OpenAPI when behavior changes.
Always create or update tests when modifying functional code.

4. Safety Rules

Never delete user code unless the task explicitly requires it.
Never auto-run destructive commands (rm -rf, force pushes, etc.).
Ask for confirmation if an operation is unsafe.