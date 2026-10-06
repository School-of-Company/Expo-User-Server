-- Form 서비스가 같은 eventId를 재발행할 수 있으므로(결과 이벤트를 못 받은 건을 5분 뒤 다시 보낸다),
-- 처리한 이벤트와 그 결과를 남겨 두었다가 중복 이벤트에는 저장 없이 같은 결과를 다시 돌려준다.
-- survey_id, expo_id는 다른 서비스 소유 ID라 FK를 두지 않는다.
CREATE TABLE tb_survey_answer_event
(
    event_id   VARCHAR(36)  PRIMARY KEY,
    survey_id  VARCHAR(36)  NOT NULL,
    expo_id    VARCHAR(36)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_survey_answer_event_expo_id ON tb_survey_answer_event (expo_id);
