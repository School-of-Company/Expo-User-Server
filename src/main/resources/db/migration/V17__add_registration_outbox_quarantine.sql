-- 등록 아웃박스의 지속 실패 이벤트 격리. 릴레이는 같은 참가자의 이벤트를 id 순서대로만 발행하고, 실패한 참가자의 뒤 이벤트만
-- 멈춘 채 다른 참가자는 계속 발행한다. 시도 상한을 넘기거나 payload를 읽을 수 없는 이벤트는 quarantined_at을 채워 격리하고,
-- 격리된 이벤트의 참가자는 뒤 이벤트까지 계속 멈춘다. 격리해도 지우거나 발행 완료로 표시하지 않는다.
--
-- 격리된 이벤트 확인:
--   SELECT id, event_id, participation_type, participant_id, attempts, last_error, quarantined_at
--   FROM tb_registration_outbox WHERE quarantined_at IS NOT NULL AND published_at IS NULL ORDER BY id;
-- 원인을 고친 뒤 재생(같은 event_id로 다시 발행되고, 멈춰 있던 같은 참가자의 뒤 이벤트가 이어서 나간다):
--   UPDATE tb_registration_outbox SET quarantined_at = NULL WHERE event_id = ? AND published_at IS NULL;
ALTER TABLE tb_registration_outbox
    ADD COLUMN quarantined_at TIMESTAMP,
    ADD COLUMN last_error     TEXT;

-- 릴레이가 같은 참가자의 앞선 격리 이벤트를 찾는다
CREATE INDEX idx_registration_outbox_participant_pending
    ON tb_registration_outbox (participation_type, participant_id, id) WHERE published_at IS NULL;
