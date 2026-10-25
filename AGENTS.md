# Expo User Server

User service of the Expo MSA. Kotlin 2.3 / Spring Boot 4.1, Gradle, Java 21, PostgreSQL + Flyway, Redis.

## Scope

- Straight port of the v1 monolith (`Expo-Server`) `auth` / `admin` / `trainee` / `participant` behavior. Match v1; adapt only what the split forces. Do not add endpoints or features v1 does not have.
- Not owned here: SMS, pre-application (신청) creation, form/survey definitions (Form-Server), expo and program data (Expo-Expo-Server).

## Boundaries

- Domains: `domain/{auth,user,training,participation}`. `user` owns `Admin`, `training` owns `Trainee`, `participation` owns `StandardParticipant`.
- Expo and Form data are referenced by ID only (`expo_id`, `survey_id`, `VARCHAR(36)`): no FK, no local entity. Reach Expo through `ExpoPeriodReader` (Feign + circuit breaker; a missing expo is `null`, a failed call is 503, never "not found"). It sends `EXPO_INTERNAL_TOKEN`.
- Post-event survey (후기) answers arrive from Form-Server over Kafka, asynchronously. Saving must be idempotent: `UNIQUE (survey_id, respondent)`.

## Auth

- Other services can reach this service directly, so never trust gateway-supplied headers (`X-User-Id`, `X-User-Role`). Admin routes authenticate by verifying the `Authorization` access token ourselves (`AccessTokenAuthenticationFilter`; JWT `sub` = `Admin.id`).
- This service signs tokens with RS256. Never commit keys.
- Authority always comes from the `Admin` row, never from a token or header claim.
- Service-to-service endpoints live under `/internal` and are authenticated only by the `X-Internal-Token` shared secret (`INTERNAL_TOKEN`); user tokens are ignored there. Never add an `/internal` prefix to the gateway routing table. Phone numbers go in request bodies, never in URLs.

## Conventions

- Follow the `kotlin-spring-arch` and `api-design` skills in `.claude/skills/`.
- Entities follow Expo-Expo-Server style: `@field:` annotations, `Long? = null` ids.
- Throw `ExpectedException(status, message)` directly; no subclasses.
- Tests without a database (utilities, service logic) are Kotest `BehaviorSpec` (`given`/`when`/`then`) with MockK for collaborators. Tests that need the real stack extend `IntegrationTestSupport` (Testcontainers; run them in CI).

## Logging

The Expo log standard, shared by every Expo service (Spring and Nest alike) so the shared Loki in Expo-Monitoring can label them the same way:

- Every line written to stdout is one JSON object (`logback-spring.xml`, `LogstashEncoder`) with at least `@timestamp` (ISO-8601/RFC3339), `level` (uppercase `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`), `service` (the Eureka app name, `spring.application.name`), `module` and `message`. Other keys (MDC values, `logger_name`, `stack_trace`) may follow. Nothing else goes to stdout: the Spring banner is off and Logback status warnings must not appear.
- The log pipeline reads only these key names and has no per-service fallback. When a service's logs do not show up or the fields do not match, fix the service's log output, never add parsing fallbacks to the pipeline.
- `module` is derived from the logger name by `ModuleNameConverter`, never set at the call site: for `team.startup.expo.{domain|global}.<x>...` it is `<x>`, otherwise the logger name's parent package. Nest services reuse the per-class logger context (`nestjs-pino` `renameContext: 'module'`).
- With the `local` profile the same JSON is also written to `${EXPO_LOG_DIR:-~/.expo/logs}/<service>/app.log` for the local Loki file tail. Production writes stdout only (pm2 captures it); add no log file settings there.

## Workflow

- Run `./gradlew ktlintCheck build` before every commit. Persistence tests use Testcontainers, so Docker must be running.
- Branch from `origin/develop`; never commit to `main` or `develop`.
- Open PRs with the `write-pr` skill and commit with the `git-commit` skill. The PR title format `[scope] description` is enforced by CI (`ci.yml`).
- No AI co-author trailer on commits or PRs.
