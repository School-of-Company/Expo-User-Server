-- 등록 요청 취소 기록(tombstone): `registration-status`가 NOT_SAVED를 확정한 requestId를 남겨,
-- 이후 같은 requestId의 등록이 저장되지 않게 한다. 박람회 삭제 때 expo_id로 지운다.
CREATE TABLE tb_registration_cancellation
(
    request_id VARCHAR(100) PRIMARY KEY,
    expo_id    VARCHAR(36) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_registration_cancellation_expo_id ON tb_registration_cancellation (expo_id);
