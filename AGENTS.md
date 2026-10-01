# Expo User Server

User service of the Expo MSA. Kotlin 2.3 / Spring Boot 4.1, Gradle, Java 21, PostgreSQL + Flyway, Redis.

## Scope

- Straight port of the v1 monolith (`Expo-Server`) `auth` / `admin` / `trainee` / `participant` behavior. Match v1; adapt only what the split forces. Do not add endpoints or features v1 does not have.
- Not owned here: SMS, pre-application (신청) creation, form/survey definitions (Form-Server), expo and program data (Expo-Expo-Server).

## Boundaries

- Domains: `domain/{auth,user,training,participation}`. `user` owns `Admin`, `training` owns `Trainee`, `participation` owns `StandardParticipant`.
- Expo and Form data are referenced by ID only (`expo_id`, `survey_id`, `VARCHAR(36)`): no FK, no local entity. Reach Expo through Feign.
- Post-event survey (후기) answers arrive from Form-Server over Kafka, asynchronously. Saving must be idempotent: `UNIQUE (survey_id, respondent)`.

## Auth

- The gateway verifies the JWT (RS256, verify-only) and forwards only `X-User-Id` (JWT `sub` = `Admin.id`). Do not parse tokens or trust that header outside the gateway path; service-to-service calls bypass the gateway.
- This service signs tokens with RS256. Never commit keys.
- Until the gateway forwards a role header, resolve authority from the `Admin` row.

## Conventions

- Follow the `kotlin-spring-arch` and `api-design` skills in `.claude/skills/`.
- Entities follow Expo-Expo-Server style: `@field:` annotations, `Long? = null` ids.
- Throw `ExpectedException(status, message)` directly; no subclasses.

## Workflow

- Run `./gradlew ktlintCheck build` before every commit. Persistence tests use Testcontainers, so Docker must be running.
- Branch from `origin/develop`; never commit to `main` or `develop`.
- Open PRs with the `write-pr` skill and commit with the `git-commit` skill. The PR title format `[scope] description` is enforced by CI (`ci.yml`).
- No AI co-author trailer on commits or PRs.
