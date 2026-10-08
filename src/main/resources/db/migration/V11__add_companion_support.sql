-- 동행자(동반자) 개별 등록. 동행자도 참가자 한 행이며 대표자(신청한 사람)에 딸린다.
-- 동행자는 전화번호가 없고(문자는 대표자 번호로 한 통), 입장 때 QR 속 참가자 ID와 code로 찾는다.
ALTER TABLE tb_standard_participant
    ALTER COLUMN phone_number DROP NOT NULL;

-- 동행자가 속한 대표자. 대표자는 NULL이다. 대표자를 지우면(박람회 삭제 포함) 동행자도 같이 지운다
ALTER TABLE tb_standard_participant
    ADD COLUMN representative_id BIGINT REFERENCES tb_standard_participant (id) ON DELETE CASCADE;

CREATE INDEX idx_standard_participant_representative_id ON tb_standard_participant (representative_id);

ALTER TABLE tb_standard_participant
    ADD COLUMN region VARCHAR(20);

-- QR에 담는 추측하기 어려운 값(22자 base64url). 참가자 ID는 순차라 ID만으로는 남의 QR을 만들 수 있어 함께 확인한다.
-- 기존 참가자도 채운다. 한 번 정하면 바꾸지 않는다.
ALTER TABLE tb_standard_participant
    ADD COLUMN code VARCHAR(22);

UPDATE tb_standard_participant
SET code = rtrim(translate(encode(decode(replace(gen_random_uuid()::text, '-', ''), 'hex'), 'base64'), '+/', '-_'), '=');

ALTER TABLE tb_standard_participant
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE tb_standard_participant
    ADD CONSTRAINT uq_standard_participant_code UNIQUE (code);

-- 등록 완료 이벤트에 이번 문자에 담을 참가자(id, code) 목록을 싣는다. 연수자는 NULL이다.
-- 동행자의 집계 이벤트(STANDARD_CREATED)는 전화번호가 없다.
ALTER TABLE tb_registration_outbox
    ADD COLUMN participants_json TEXT;

ALTER TABLE tb_registration_outbox
    ALTER COLUMN phone_number DROP NOT NULL;
