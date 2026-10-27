-- Expo가 `expo.expo.changed`로 발행하는 박람회 변경 이벤트의 로컬 복사본이다. 이 서비스가 읽는 값(기간, 삭제 중 여부,
-- 사전등록 회차 id)만 둔다. expo_id는 Expo 소유 ID라 다른 테이블과 FK를 두지 않는다.
-- version은 마지막으로 반영한 이벤트의 version이다. 그 값 이하의 이벤트는 버린다. tombstone을 받으면 상태를 비우고
-- deleted=true로 행을 남겨, 늦게 도착한 옛 이벤트로 되살리지 않는다.
CREATE TABLE tb_expo_projection
(
    expo_id       VARCHAR(36) PRIMARY KEY,
    version       BIGINT      NOT NULL,
    last_event_id VARCHAR(36) NOT NULL,
    deleted       BOOLEAN     NOT NULL,
    started_day   DATE,
    finished_day  DATE,
    deleting      BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CHECK (deleted OR (started_day IS NOT NULL AND finished_day IS NOT NULL))
);

CREATE TABLE tb_expo_projection_preregister_session
(
    expo_id    VARCHAR(36) NOT NULL REFERENCES tb_expo_projection (expo_id) ON DELETE CASCADE,
    session_id BIGINT      NOT NULL,
    PRIMARY KEY (expo_id, session_id)
);
