-- 명찰 자동 출력 큐(#237). 출력대(station)는 박람회마다 관리자가 등록하고, 한 출력대에서는 마지막으로 연 세션만 유효하다.
-- 박람회 삭제는 expo_id로 두 테이블을 지우고, 시도 이력은 작업에 ON DELETE CASCADE로 딸려 지워진다.
CREATE TABLE tb_badge_print_station
(
    id           UUID PRIMARY KEY,
    expo_id      VARCHAR(36) NOT NULL,
    label        VARCHAR(50) NOT NULL,
    session_id   UUID        NOT NULL,
    paused       BOOLEAN     NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL
);

CREATE INDEX ix_badge_print_station_expo ON tb_badge_print_station (expo_id, created_at);

-- 입장 트랜잭션이 저장하는 자동 원본 작업(original_job_id IS NULL)은 (박람회, 동일인, 방문일)마다 하나다. person_key는
-- 일반 참가자 `S:{id}`, 참가자와 연결된 연수자 `S:{standardParticipantId}`, 연결 없는 연수자 `T:{traineeId}`이다.
-- 재출력 작업은 original_job_id로 처리한 작업을 가리키는 새 행이고, 한 작업은 한 번만 재출력된다(uk_badge_print_job_reprint).
-- request_key는 재출력 요청의 멱등키라 같은 키로 다시 오면 이미 만든 작업으로 답한다.
--
-- failures는 인쇄 호출 전 확정 실패(FAILED_BEFORE_PRINT) 횟수다. 1~3번째 실패 뒤에는 2/5/15초 뒤(next_attempt_at)
-- 다시 WAITING이 되고 4번째 실패에서 FAILED가 된다(최초 시도 1번 + 재시도 3번).
-- station_id·claim_token·claimed_at은 마지막 점유 정보다. CLAIMED인 작업은 출력대마다 하나뿐이다(uk_badge_print_job_active).
CREATE TABLE tb_badge_print_job
(
    id                 BIGSERIAL PRIMARY KEY,
    expo_id            VARCHAR(36)  NOT NULL,
    person_key         VARCHAR(30)  NOT NULL,
    attendance_date    DATE         NOT NULL,
    participation_type VARCHAR(10)  NOT NULL,
    subject_id         BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    school             VARCHAR(100),
    status             VARCHAR(20)  NOT NULL,
    failures           INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMP,
    station_id         UUID REFERENCES tb_badge_print_station (id) ON DELETE SET NULL,
    claim_token        UUID,
    claim_request_id   UUID,
    claimed_at         TIMESTAMP,
    original_job_id    BIGINT REFERENCES tb_badge_print_job (id) ON DELETE CASCADE,
    request_key        UUID,
    resolved_by        BIGINT,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    CHECK ((original_job_id IS NULL) = (request_key IS NULL))
);

CREATE UNIQUE INDEX uk_badge_print_job_daily ON tb_badge_print_job (expo_id, person_key, attendance_date) WHERE original_job_id IS NULL;
CREATE UNIQUE INDEX uk_badge_print_job_reprint ON tb_badge_print_job (original_job_id) WHERE original_job_id IS NOT NULL;
CREATE UNIQUE INDEX uk_badge_print_job_active ON tb_badge_print_job (station_id) WHERE status = 'CLAIMED';
CREATE INDEX ix_badge_print_job_queue ON tb_badge_print_job (expo_id, attendance_date, status, id);
CREATE INDEX ix_badge_print_job_claimed ON tb_badge_print_job (claimed_at) WHERE status = 'CLAIMED';

-- 점유 한 번이 시도 하나다. outcome이 비어 있으면 아직 결과가 없는 점유다. 이름·소속은 남기지 않는다.
CREATE TABLE tb_badge_print_attempt
(
    claim_token UUID PRIMARY KEY,
    job_id      BIGINT      NOT NULL REFERENCES tb_badge_print_job (id) ON DELETE CASCADE,
    station_id  UUID        NOT NULL,
    claimed_at  TIMESTAMP   NOT NULL,
    outcome     VARCHAR(20),
    reason      VARCHAR(200),
    finished_at TIMESTAMP
);

CREATE INDEX ix_badge_print_attempt_job ON tb_badge_print_attempt (job_id, claimed_at);
