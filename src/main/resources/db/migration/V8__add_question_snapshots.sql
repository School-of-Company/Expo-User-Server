-- 제출 당시 문항 스냅샷. Form은 폼이나 설문을 수정하면 문항을 다시 만들어 문항 ID가 바뀌므로, 수정 뒤에도 제출 당시의
-- 문항 정의와 선택지를 복원할 수 있도록 답변과 함께 보존한다. 신청 답변(information_json)은 문항 제목을, 설문 답변
-- (answer_json)은 문항 ID를 키로 쓰며 이 변경은 그 저장 형식을 바꾸지 않는다.
-- 모두 nullable이다. 스냅샷 없이 저장된 행(스냅샷을 보내지 않은 호출, v1 이벤트, 기존 행)을 NULL로 구분한다.
ALTER TABLE tb_standard_participant
    ADD COLUMN information_form_id   VARCHAR(36),
    ADD COLUMN information_questions JSONB;

ALTER TABLE tb_trainee
    ADD COLUMN information_form_id   VARCHAR(36),
    ADD COLUMN information_questions JSONB;

ALTER TABLE tb_standard_participant_survey_answer
    ADD COLUMN answer_questions JSONB;

ALTER TABLE tb_trainee_survey_answer
    ADD COLUMN answer_questions JSONB;
