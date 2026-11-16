---
paths:
  - "src/main/kotlin/team/startup/expo/domain/**"
  - "src/main/kotlin/team/startup/expo/global/client/**"
  - "src/main/resources/db/migration/**"
---

# Domain rules

- Straight port of the v1 monolith (`Expo-Server`) `auth` / `admin` / `trainee` / `participant` behavior. Match v1; adapt only what the split forces. Do not add endpoints or features v1 does not have.
- Not owned here: SMS, pre-application (신청) creation, form/survey definitions (Form-Server), expo and program data (Expo-Expo-Server).
- Domains: `domain/{auth,user,training,participation}`. `user` owns `Admin`, `training` owns `Trainee`, `participation` owns `StandardParticipant`.
- Expo and Form data are referenced by ID only (`expo_id`, `survey_id`, `VARCHAR(36)`): no FK, no local entity. Reach Expo through `ExpoPeriodReader` (Feign + circuit breaker; a missing expo is `null`, a failed call is 503, never "not found"). It sends `EXPO_INTERNAL_TOKEN`. With `KAFKA_EXPO_PROJECTION_ENABLED=true` it reads the local projection fed by Expo's `expo.expo.changed` events first (`tb_expo_projection`, the only Expo data stored here) and calls Expo only for expos not projected yet.
- Post-event survey (후기) answers arrive from Form-Server over Kafka, asynchronously. Saving must be idempotent: `UNIQUE (survey_id, respondent)`.
