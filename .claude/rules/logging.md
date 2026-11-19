---
paths:
  - "src/main/kotlin/team/startup/expo/global/logging/**"
  - "src/main/resources/logback*.xml"
  - "src/main/resources/application*.yaml"
---

# Logging

The Expo log standard, shared by every Expo service (Spring and Nest alike) so the shared Loki in Expo-Monitoring can label them the same way:

- Every line written to stdout is one JSON object (`logback-spring.xml`, `LogstashEncoder`) with at least `@timestamp` (ISO-8601/RFC3339), `level` (uppercase `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`), `service` (the Eureka app name, `spring.application.name`), `module` and `message`. Other keys (MDC values, `logger_name`, `stack_trace`) may follow. Nothing else goes to stdout: the Spring banner is off and Logback status warnings must not appear.
- The log pipeline reads only these key names and has no per-service fallback. When a service's logs do not show up or the fields do not match, fix the service's log output, never add parsing fallbacks to the pipeline.
- `module` is derived from the logger name by `ModuleNameConverter`, never set at the call site: for `team.startup.expo.{domain|global}.<x>...` it is `<x>`, otherwise the logger name's parent package. Nest services reuse the per-class logger context (`nestjs-pino` `renameContext: 'module'`).
- Every HTTP request gets one access log line from `HttpAccessLogFilter` (module `HttpAccess`, registered ahead of Spring Security so 401/403 show too): `<METHOD> <path> status: <code> (<ms>ms)`, INFO below 400, WARN for 4xx, ERROR for 5xx and client aborts (499). The path is the matched route template, or the raw URI without query (max 200 chars) when nothing matched. Never log query strings, headers, cookies or bodies. `/actuator/**` and the API docs are skipped. `LOGGING_HTTP_ACCESS_ENABLED=false` (`logging.http-access.enabled`) turns it off.
- With the `local` profile the same JSON is also written to `${EXPO_LOG_DIR:-~/.expo/logs}/<service>/app.log` for the local Loki file tail. Production writes stdout only (pm2 captures it); add no log file settings there.
