---
paths:
  - "**/*.kt"
---

# Kotlin conventions

- Follow the `kotlin-spring-arch` and `api-design` skills in `.claude/skills/`.
- Entities follow Expo-Expo-Server style: `@field:` annotations, `Long? = null` ids.
- Throw `ExpectedException(status, message)` directly; no subclasses.
- Tests without a database (utilities, service logic) are Kotest `BehaviorSpec` (`given`/`when`/`then`) with MockK for collaborators. Tests that need the real stack extend `IntegrationTestSupport` (Testcontainers; run them in CI).
