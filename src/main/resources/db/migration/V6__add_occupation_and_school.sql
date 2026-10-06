-- 명찰 대상 판정과 출력에 쓰는 값. v1에는 없었고 informationJson과 별도로 저장한다.
-- 직업은 일반 참가자만, 소속 학교는 일반 참가자와 연수자 모두 받는다. 기존 행은 값이 없으므로 NULL이다.
ALTER TABLE tb_standard_participant
    ADD COLUMN occupation VARCHAR(30),
    ADD COLUMN school     VARCHAR(100);

ALTER TABLE tb_trainee
    ADD COLUMN school VARCHAR(100);
