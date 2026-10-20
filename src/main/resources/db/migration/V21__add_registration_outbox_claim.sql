-- 등록 아웃박스 점유 기한. 릴레이는 짧은 트랜잭션으로 이벤트를 골라 claimed_until을 채우고 커밋한 뒤 트랜잭션 밖에서
-- Kafka로 보내고, 다시 짧은 트랜잭션으로 결과를 남기며 비운다. Kafka 응답을 기다리는 동안 행을 잠그지 않으므로 박람회
-- 삭제의 DELETE가 기다리지 않는다. 기한이 남은 행은 다른 릴레이가 가져가지 않고, 결과를 남기기 전에 죽은 릴레이의 행은
-- 기한이 지나면 다시 가져간다.
ALTER TABLE tb_registration_outbox
    ADD COLUMN claimed_until TIMESTAMP;
