-- 리포트용 상세 조회가 쓰는 인덱스.
-- 페이지 조회는 `WHERE expo_id = ? AND id > ? ORDER BY id LIMIT ?`라서 (expo_id, id)가 있어야 다른 박람회의 행을
-- 건너뛰거나 행사 행을 다시 정렬하지 않고 인덱스 순서대로 페이지 크기만큼만 읽는다.
CREATE INDEX idx_standard_participant_expo_id_id ON tb_standard_participant (expo_id, id);
CREATE INDEX idx_trainee_expo_id_id ON tb_trainee (expo_id, id);

-- 페이지의 참가자 id 목록으로 설문 답변을 찾는다. 유일 인덱스는 (survey_id, standard_participant_id) 순서라 이 조건에
-- 맞지 않아, 없으면 페이지마다 답변 테이블을 넓게 읽는다. 같은 참가자의 답변을 id 순서로 읽으므로 id까지 포함한다.
CREATE INDEX idx_standard_participant_survey_answer_participant ON tb_standard_participant_survey_answer (standard_participant_id, id);
