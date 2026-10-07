-- 등록 요청 멱등: Application이 같은 등록을 재시도하면(`Idempotency-Key` -> request_id) 저장 없이 처음의 결과를
-- 돌려준다. fingerprint는 요청 본문(request_id 제외)의 해시라, 같은 키를 다른 내용에 쓰면 거부할 수 있다.
-- 참가자는 일반/연수 두 테이블에 나뉘어 있어 participant_id에는 FK를 두지 않는다. 박람회 삭제 때 expo_id로 지운다.
CREATE TABLE tb_registration_request
(
    request_id         VARCHAR(100) PRIMARY KEY,
    participation_type VARCHAR(20)  NOT NULL,
    expo_id            VARCHAR(36)  NOT NULL,
    fingerprint        VARCHAR(64)  NOT NULL,
    participant_id     BIGINT       NOT NULL,
    phone_number       VARCHAR(30)  NOT NULL,
    created            BOOLEAN      NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_registration_request_expo_id ON tb_registration_request (expo_id);

-- 등록 완료 이벤트의 트랜잭션 아웃박스. 참가자 저장과 같은 트랜잭션에서 기록하고, 릴레이가 Kafka로 발행한 뒤
-- published_at을 채운다. event_id는 등록 때 한 번 만들어 재발행에도 그대로 쓰므로 소비자가 멱등키로 쓴다.
CREATE TABLE tb_registration_outbox
(
    id                 BIGSERIAL PRIMARY KEY,
    event_id           VARCHAR(36)  NOT NULL UNIQUE,
    event_type         VARCHAR(30)  NOT NULL,
    expo_id            VARCHAR(36)  NOT NULL,
    participation_type VARCHAR(20)  NOT NULL,
    participant_id     BIGINT       NOT NULL,
    phone_number       VARCHAR(30)  NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    published_at       TIMESTAMP,
    attempts           INTEGER      NOT NULL DEFAULT 0
);

-- 릴레이가 아직 발행하지 않은 행만 순서대로 읽는다
CREATE INDEX idx_registration_outbox_pending ON tb_registration_outbox (id) WHERE published_at IS NULL;
CREATE INDEX idx_registration_outbox_expo_id ON tb_registration_outbox (expo_id);

-- QR 문자 발송 횟수 증가의 멱등: 같은 event_id는 한 번만 올린다. 참가자를 지우면 같이 지워진다.
CREATE TABLE tb_sms_try_event
(
    event_id       VARCHAR(36) PRIMARY KEY,
    participant_id BIGINT      NOT NULL REFERENCES tb_standard_participant (id) ON DELETE CASCADE,
    created_at     TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_try_event_participant_id ON tb_sms_try_event (participant_id);
