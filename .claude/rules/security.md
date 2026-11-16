---
paths:
  - "src/main/kotlin/team/startup/expo/global/security/**"
  - "src/main/kotlin/team/startup/expo/**/presentation/**"
---

# Security

- Other services can reach this service directly, so never trust gateway-supplied headers (`X-User-Id`, `X-User-Role`). Admin routes authenticate by verifying the `Authorization` access token ourselves (`AccessTokenAuthenticationFilter`; JWT `sub` = `Admin.id`).
- This service signs tokens with RS256. Never commit keys.
- Authority always comes from the `Admin` row, never from a token or header claim.
- Service-to-service endpoints live under `/internal` and are authenticated only by the `X-Internal-Token` shared secret (`INTERNAL_TOKEN`); user tokens are ignored there. Never add an `/internal` prefix to the gateway routing table. Phone numbers go in request bodies, never in URLs.
