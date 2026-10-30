Expo-User-Server

[ 스타트업 박람회 프로젝트 | Server-V2 ] User 서비스 — auth, admin, trainee, participant 도메인을 담당한다.

## 운영: 첫 관리자 만들기

새 서버(빈 DB)에서는 가입을 승인해 줄 관리자가 없어 아무도 로그인할 수 없다. 첫 관리자는 환경변수로 만든다.

1. **추측하기 어려운 닉네임**을 정하고 `BOOTSTRAP_ADMIN_NICKNAME`에 넣는다. 비밀번호는 환경변수에 넣지 않는다.
2. 서버를 띄운 뒤 **운영자가 직접** 그 닉네임으로 `POST /auth` 가입한다. 가입에는 전화번호로 받은 인증번호(`code`)가 필요해서 알림(Notification) 서비스가 동작해야 한다(그렇지 않으면 503). 아직 승인 대기(`PENDING`) 상태이며 로그인되지 않는다.
3. 서버를 **재기동**한다. 기동할 때 승인된 관리자가 한 명도 없고 그 닉네임의 승인 대기 계정이 있으면 관리자(`ACCEPTED`, `ROLE_ADMIN`)로 승인한다.
4. 로그인을 확인한 뒤 **환경변수를 비운다**. 관리자가 생긴 뒤에는 값이 남아 있어도 아무 일도 하지 않지만 남겨 두지 않는다.

주의
- 승인은 가입할 때가 아니라 **기동할 때만** 한다. 설정을 켜 둔 동안 다른 사람이 같은 닉네임으로 먼저 가입하면 그 사람이 승인되므로, 2번과 3번 사이를 짧게 하고 닉네임을 알려 주지 않는다.
- 승인된 관리자가 이미 있거나 닉네임의 계정이 승인 대기가 아니면 아무것도 하지 않는다. 로그에는 닉네임만 남긴다.
- 환경변수를 쓰지 않을 때의 수동 방법은 DB에서 `status`와 `authority`를 **함께** 바꾸는 것이다(`status`만 바꾸면 모든 API가 403이다).
  `UPDATE tb_admin SET status = 'ACCEPTED', authority = 'ROLE_ADMIN' WHERE nickname = '...';`

## API 문서(Swagger)

기본은 꺼짐이다. 서버에서 환경변수로 켠다. 둘 다 선택 값이라 없어도 기동에 영향이 없다.

- `SWAGGER_ENABLED=true`: `/auth/v3/api-docs`(OpenAPI JSON)와 `/auth/swagger-ui.html`을 연다. Gateway는 경로 접두사로만 서비스를 찾기 때문에 문서를 `/auth` 접두사 아래에 둔다.
- `SWAGGER_INCLUDE_INTERNAL=true`: `/internal` 하위 API와 `X-Internal-Token` 인증 방식을 문서에 넣는다(기본 `false`, `SWAGGER_ENABLED`가 꺼져 있으면 의미 없다). 문서 예시에는 토큰 값이나 실제 전화번호, `code`를 넣지 않는다.

## 박람회 프로젝션 (`KAFKA_EXPO_PROJECTION_ENABLED`)

기본은 꺼짐이다. 끄면 지금처럼 박람회 기간(`GET /internal/expo/{expo_id}`)과 사전등록 회차(`GET /internal/expo/{expo_id}/preregister-sessions/{session_id}`)를 Expo에 동기로 묻는다.

- `true`면 `expo.expo.changed` 컨슈머(그룹 `expo-user-server.expo-changed`)를 띄우고, 위 두 조회를 로컬 프로젝션(`tb_expo_projection`, `tb_expo_projection_preregister_session`)에서 읽는다. 프로젝션에 없는 박람회는 기존 Expo 호출(서킷브레이커 포함)로 넘어가고, 처음 한 번만 WARN을 남긴다.
- 박람회마다 마지막으로 반영한 `version`을 두고 그 이하의 이벤트는 버린다. tombstone은 기간과 회차를 지우고 `version`은 남긴다. 해석할 수 없는 이벤트는 `expo.expo.changed.dlt`로 간다.
- 켠 뒤에는 Expo에 `POST /internal/expo-events/republish`를 한 번 요청해 전체 상태를 채운다. 오래 껐다 다시 켤 때도 같다.
- 토픽·dead letter·그룹 이름은 `KAFKA_EXPO_CHANGED_TOPIC`, `KAFKA_EXPO_CHANGED_DEAD_LETTER_TOPIC`, `KAFKA_EXPO_CHANGED_GROUP_ID`로 바꿀 수 있다.

## 방문일 등록 연계 (User #202)

아래 API는 `X-Internal-Token` 전용이다. Application V41의 방문일 원장이 원천이며, User는 프로필과 QR을 변경하지 않는 projection을 보관한다.

- `POST /internal/expos/{expoId}/field-visit-participants`: `{phoneNumber,requestId,participants:[{clientReference,name,occupation,school,region}],personalInformationStatus}` → 200 `[{participantId,clientReference}]`. 신규 사람만 보낸다. 기존 참가자는 managed/select의 ID를 선택한다. 첫 신규 일행의 첫 사람이 대표자가 되고 기존 일행에는 기존 대표자 밑으로 추가된다.
- optional root `formId`와 `questions`는 기존 등록 API와 동일한 문항 snapshot이며 새 사람에게만 저장한다. 각 participants 항목의 optional `informationJson`은 기본 `"{}"`로, 자기 답변만 저장한다. 원본 snapshot과 각자 답변은 모두 멱등 payload에 포함되므로 Application은 최초 제출 값을 durable 저장해 그대로 재전송한다. 기존 참가자의 답변·폼·프로필은 갱신하지 않는다.
- 같은 expo+requestId는 ACTIVE 반영 전이나 응답 유실 뒤에도 최초 exact mapping을 재현한다. 다른 payload는 409다. 복구에는 **동일 POST**를 사용한다. 기존 `registration-status`는 이전 생성 API 전용이다. pending이 있는 일행에 다른 requestId로 신규 생성을 우회하면 409다. 참가자·매핑·아웃박스는 한 트랜잭션이다.
- `PUT /internal/expos/{expoId}/participants/{participantId}/field-visits/{registrationId}`: `{visitDate,generation,status,operationId}` → 204, 응답 본문 없음. 날짜는 KST 날짜, generation은 양수, status는 `ACTIVE|CANCELLED`, operationId와 registrationId는 UUID다. 등록 UUID의 참가자·날짜는 불변이며 같은 세대 CANCELLED는 terminal이다. 이전 세대는 acknowledge하고 현재 세대를 바꾸지 않는다. 같은 operationId의 다른 payload는 409다.
- managed/select는 inactive와 pending을 포함한 **역사적 소유권** 목록이다. 대표자가 있으면 모두 inactive여도 `registered=true`다. 목록은 5명을 넘을 수 있다. `applicationType`은 원본 프로필이며 오늘의 입장·프로그램 자격을 뜻하지 않는다. Application이 오늘 원장으로 판단한다.
- 생성/활성화의 5명 한도는 대표자 1명, 유효 PRE 동행자, 하나 이상의 ACTIVE 방문이 있는 동행자, projection 미완료 신규 동행자를 사람별로 센다. 방문일 여러 건은 인원에 더하지 않는다. 취소된 신규 동행자는 다른 ACTIVE 방문이 없을 때만 자리를 반환한다. 과거 FIELD의 생성일을 방문일로 추정하지 않는다.
- QR/문자 아웃박스는 신규 생성 때만 기록한다. projection·재방문·같은 키 복구는 이벤트를 만들지 않는다. 취소 알림 정책은 별도 작업이며 여기서 추가하지 않는다.

Attendance의 `POST /internal/entries`는 optional `attendanceDate`, `fieldVisitRegistrationId`, `fieldVisitGeneration`을 받는다. FIELD는 세 값을 함께 보내고 회차는 보내지 않는다. User의 ACTIVE projection 날짜·세대·참가자가 일치해야 기록한다. PRE는 기존 `preregisterSessionId`를 유지하며 `attendanceDate`만 추가할 수 있다. 전달된 날짜를 사용하고, 날짜 생략 시 기존처럼 KST 오늘을 쓴다. 기존 날짜+회차 unique와 409 계약은 유지된다. 누적 입장 여부나 프로필을 변경하지 않는다.

새 생성 API의 참가자는 회차 없는 입장에 FIELD 권위 필드를 생략할 수 없다. 날짜를 명시한 기존 FIELD도 회차 없는 입장에서 등록 UUID와 세대가 없으면 409다. Attendance가 승인한 PRE 회차는 원본 프로필 구분과 무관하게 보존한다. 방문일 미상 과거 FIELD를 오늘로 추정하지 않는다. 날짜를 생략한 이전 API 호출은 기존 참가자에 한해 종전 계약을 유지하므로 Attendance 새 계약 배포 후 feature gate를 열어야 한다.

PRE 전화번호 경로는 Attendance가 오늘 ACTIVE PRE를 확인한 뒤 시간 검사 없이 earliest-start session ID를 전달하는 새 계약을 지원한다. User는 전달한 ID와 날짜를 그대로 기록하므로 전화번호 입장도 그 PRE 회차 집계에 포함된다. QR은 기존처럼 검증한 session ID를 전달한다.

`POST /internal/standard-participants/entry-status`는 `{expoId,participantId,attendanceDate}` → `{participantId,attendanceDate,entered}`로 정확한 날짜를 echo한다. 참가자가 없거나 다른 행사면 404다. `false`는 조회 시점의 관측이며 timeout 요청의 지연 commit을 배제하지 않는다. Attendance는 durable PENDING scan fence를 유지하고 불확실한 취소 판단을 503으로 닫아야 한다. 기존 cancellation-status의 누적 `entered`는 방문일 취소 판단에 쓰지 않는다.

entry의 409는 중복뿐 아니라 FIELD projection 취소·세대·날짜 불일치도 뜻한다. 409 단독으로 PENDING을 CONFIRMED로 바꾸면 안 된다. 같은 날짜 entry-status가 `entered=true`일 때만 회복하고, false나 조회 실패는 PENDING과 503을 유지한다.

배포 순서는 Application V41 원장 → User V24 및 Attendance 새 계약 → fake-provider 및 실제 서비스 통합 검증 → `APPLICATION_FIELD_VISITS_ENABLED=true`다. 그 전에는 false와 registration rollout gate를 유지한다. Docker 없는 환경에서는 service/provider 회귀와 컴파일·린트를 실행하고 Testcontainers persistence 검증은 CI에 남긴다.

## 명찰 자동 출력 큐 (User #237)

연수자 전원과 일반 참가자 중 `TEACHER`·`PRE_SERVICE_TEACHER`가 입장하면 `POST /internal/entries`가 같은 트랜잭션에서 출력 작업을 저장한다. 저장이 실패하면 입장도 되돌린다(500). 응답의 `badgePrint`는 `QUEUED`(이번 입장으로 저장), `DUPLICATE`(같은 사람의 같은 방문일 작업이 이미 있음), `null`(대상 아님. 409 재입장 응답에는 없음)이다. 같은 사람은 일반 참가자 `S:{participantId}`, 참가자와 연결된 연수자 `S:{standardParticipantId}`, 연결 없는 연수자 `T:{traineeId}`로 묶어 방문일(KST)마다 한 번만 저장한다. 출력대가 없어도 작업은 `WAITING`으로 남는다. 행사별 설정이나 기능 플래그는 없다.

관리자 API는 `/admin/badge-prints/{expoId}` 아래이고 `ROLE_ADMIN` access token을 이 서비스가 직접 검증한다. 다른 박람회의 출력대·작업은 404다.

| 메서드·경로 | 요청 → 응답 |
|---|---|
| `POST /stations` | `{label?}`(본문 생략 가능) → `{stationId, label, sessionId}`. `label`이 없으면 `출력대 N` |
| `GET /stations` | → `{stations:[{stationId, label, paused, lastSeenAt}]}` |
| `POST /stations/{stationId}/sessions` | → `{sessionId}`. 이전 세션은 무효, 이전 세션이 점유 중이던 작업은 `NEEDS_CHECK`(`TAKEN_OVER`) |
| `PATCH /stations/{stationId}` | `{paused}` → 204. 일시정지 중 claim은 204, 점유 중 작업의 결과 보고는 받음 |
| `POST /stations/{stationId}/claim` | `{sessionId, requestId?}` → 200 `{jobId, claimToken, name, school}` / 204 없음 / 409 이전 세션 |
| `POST /jobs/{jobId}/result` | `{sessionId, claimToken, outcome: SUBMITTED\|FAILED_BEFORE_PRINT, reason?}` → 204 / 409 |
| `GET /jobs?status=NEEDS_CHECK,FAILED` | → 오늘(KST) 작업 `{jobs:[{jobId, name, school, status, updatedAt}]}`. `status` 생략 시 `NEEDS_CHECK,FAILED` |
| `GET /jobs/{jobId}/attempts` | → `{attempts:[{stationId, claimedAt, outcome, reason, finishedAt}]}` |
| `POST /jobs/{jobId}/resolve` | `{action: REQUEUE\|DONE, requestKey}` → 204 / 409 |

상태는 `WAITING → CLAIMED → SUBMITTED`다. `SUBMITTED`는 출력 화면이 인쇄 요청을 브라우저에 넘겼다는 뜻이고 종이가 나왔다는 보장은 아니다.

- claim은 오늘(KST) `WAITING` 중 재시도 시각이 된 가장 오래된 작업을 `FOR UPDATE SKIP LOCKED`로 점유한다. 지난 날짜 작업은 자동으로 주지 않는다. 출력대당 `CLAIMED`는 하나뿐이다(DB 부분 unique).
- `FAILED_BEFORE_PRINT`는 인쇄 호출 전 확정 실패만 보낸다. 1·2·3번째 실패 뒤 2·5·15초 후 다시 `WAITING`, 4번째 실패에서 `FAILED`다(최초 1회 + 재시도 3회).
- 점유 후 30초 안에 결과가 없으면 `NEEDS_CHECK`(`EXPIRED`)다. claim 요청 때와 5초 주기 작업(`badge-print.sweeper.*`) 둘 다 처리한다. `NEEDS_CHECK`는 자동으로 다시 출력하지 않는다.
- `REQUEUE`는 원본을 `REQUEUED`로 닫고 원본을 가리키는 새 `WAITING` 작업을 오늘 날짜로 만든다. 한 작업은 한 번만 재출력되고, 같은 `requestKey` 재요청은 204, 다른 키는 409다. `DONE`은 처리 완료로 닫는다. 둘 다 `NEEDS_CHECK`·`FAILED`만 처리한다.

출력 화면(Client) 계약. 이미 인쇄했을 수 있는 작업을 다시 실행하지 않는 것이 목적이다.

1. 화면을 열거나 새로고침할 때마다 `POST /sessions`로 새 `sessionId`를 받는다. 이전 화면이 쥐고 있던 작업은 서버가 `NEEDS_CHECK`로 돌린다.
2. claim마다 새 `requestId`(UUID)를 만든다. 응답을 못 받았을 때만 **같은 `requestId`**로 다시 보낸다. 그러면 같은 작업·같은 `claimToken`이 온다. 이때 받은 작업은 아직 인쇄하지 않은 것이다.
3. 받은 작업의 결과를 204로 확인받기 전에는 다음 claim을 보내지 않는다. 결과 응답을 못 받으면 같은 본문으로 다시 보낸다(같은 결과는 204). 결과 없이 새 `requestId`로 claim하면 서버는 이전 작업을 `NEEDS_CHECK`(`ABANDONED`)로 닫고 다음 작업을 준다.
4. 결과 보고가 409면 그 작업은 서버가 이미 닫았다(세션 교체·30초 만료). 다시 인쇄하지 않는다. 관리자 목록에서 확인한다.
5. 테스트 출력은 화면 안에서만 하고 큐 작업을 만들지 않는다. 기존 수동 "명찰 출력" 버튼은 그대로다.

박람회 삭제(`DELETE /internal/expos/{expoId}`)는 출력대·작업·시도 이력을 함께 지운다. 시도 이력에는 이름·소속을 남기지 않는다. 스캐너 노트북 6대와 출력 노트북 5대(11대)가 같은 관리자 계정으로 로그인하려면 `JWT_MAX_REFRESH_TOKENS`를 20으로 올려야 한다(기본 10).
