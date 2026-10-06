-- 내부 조회 API는 저장된 표기와 정확히 같은 번호를 먼저 찾고, 없으면 숫자만 남겨 비교한다.
-- v1은 전화번호를 형식 검증 없이 저장했으므로 하이픈이 든 값이 있을 수 있다.
-- 숫자 비교가 박람회 전체를 훑지 않도록 같은 식의 표현식 인덱스를 둔다(쿼리의 식과 정확히 같아야 한다).
CREATE INDEX idx_standard_participant_expo_phone_digits
    ON tb_standard_participant (expo_id, (regexp_replace(phone_number, '[^0-9]', '', 'g')));

CREATE INDEX idx_trainee_expo_phone_digits
    ON tb_trainee (expo_id, (regexp_replace(phone_number, '[^0-9]', '', 'g')));
