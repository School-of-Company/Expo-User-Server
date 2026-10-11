-- 사전등록 참가자와 연수자를 같은 사람으로 잇는다. 연수마다 사람을 새로 만들지 않고, 같은 참가자는 다른 연수에서도 같은
-- 연수자를 다시 쓴다. 대표자와 동행자가 같은 수신 번호를 써도 서로 다른 연수자를 갖도록, 이어진 연수자는 전화번호를 비워 둔다
-- ((expo_id, phone_number) 유일 제약은 NULL을 여러 개 허용한다). 문자는 notification_phone_number로 보낸다.
ALTER TABLE tb_trainee
    ALTER COLUMN phone_number DROP NOT NULL;

-- 연수원 아이디는 업무상 필요한 폼에서 수집한다. 참가자에서 이어 만든 연수자는 아직 모를 수 있다.
ALTER TABLE tb_trainee
    ALTER COLUMN training_id DROP NOT NULL;

ALTER TABLE tb_trainee
    ADD COLUMN notification_phone_number VARCHAR(15);

-- 참가자를 지우면(박람회 삭제 등) 이어진 연수자도 함께 지운다. 신청 취소는 연결을 지우지 않는다.
ALTER TABLE tb_trainee
    ADD COLUMN standard_participant_id BIGINT REFERENCES tb_standard_participant (id) ON DELETE CASCADE;

-- 참가자 한 명에 연수자 한 명(1:1). 이어지지 않은 연수자(기존 행과 번호로 등록한 연수자)는 걸리지 않는다.
CREATE UNIQUE INDEX uq_trainee_standard_participant
    ON tb_trainee (standard_participant_id)
    WHERE standard_participant_id IS NOT NULL;
