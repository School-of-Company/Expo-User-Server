-- 일반 참가자 입장 기록에 사전등록 회차를 남긴다. 회차는 Expo 서비스 소유 ID라 FK를 두지 않는다.
-- 입장 스캔(Attendance)이 신청한 회차의 입장 시간임을 확인한 입장만 회차가 있고, 현장 입장·신청 없음·시간 밖 입장은 NULL이다.
-- 이 마이그레이션 이전의 입장 기록은 회차를 알 수 없으므로 추정해 채우지 않고 NULL로 둔다.
ALTER TABLE tb_standard_participant_participation
    ADD COLUMN preregister_session_id BIGINT;

-- 유일 키를 (박람회, 참가자, 날짜)에서 (박람회, 참가자, 날짜, 회차)로 바꾼다. 회차 없는 입장도 하루 한 번만 남도록
-- NULL끼리 같은 값으로 본다(PostgreSQL 15 이상). 같은 날 회차 없이 먼저 입장했어도 회차 입장 시간에는 다시 기록된다.
ALTER TABLE tb_standard_participant_participation
    DROP CONSTRAINT uk_standard_participant_participation;
ALTER TABLE tb_standard_participant_participation
    ADD CONSTRAINT uk_standard_participant_participation
        UNIQUE NULLS NOT DISTINCT (expo_id, standard_participant_id, attendance_date, preregister_session_id);

-- 회차별 입장자 목록은 `WHERE expo_id = ? AND preregister_session_id = ?`를 참가자 id 순서로 읽는다
CREATE INDEX idx_standard_participant_participation_session
    ON tb_standard_participant_participation (expo_id, preregister_session_id, standard_participant_id)
    WHERE preregister_session_id IS NOT NULL;
