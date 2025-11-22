---
trigger: always_on
---

# Docker Rules

## Image and Build Rules
- MUST use multi-stage Docker builds.
- MUST use Eclipse Temurin JRE (17+) for runtime.
- MUST run application as non-root user.
- MUST minimize image size by removing unnecessary files.

## Container Behavior
- MUST use environment variables for configuration.
- MUST expose only necessary ports.
- MUST log to STDOUT/STDERR.
- MUST include a Docker healthcheck.
- MUST follow 12-Factor principles.

## Security Rules
- MUST NOT embed secrets in Docker images.
- MUST use OS packages from trusted Alpine or Debian sources.
