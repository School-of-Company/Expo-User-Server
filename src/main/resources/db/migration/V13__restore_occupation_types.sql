-- 직업(유형)을 중학생, 고등학생, 교직원, 학부모를 따로 두는 9개 값으로 되돌린다(Form의 Occupation과 같은 이름).
-- V10이 중학생과 고등학생을 MIDDLE_HIGH_SCHOOL_STUDENT로, 교직원과 학부모를 GENERAL로 합쳐 저장했다. 합치면서 원래 값을
-- 컬럼에는 남기지 않았지만 신청 답변(information_json)은 그대로라, 답변에 원래 값의 키가 문자열로 남아 있으면 그것으로
-- 복원한다. 컬럼은 VARCHAR라서 값만 옮긴다.
--
-- 복원할 근거가 없는 행:
--  - MIDDLE_HIGH_SCHOOL_STUDENT는 enum에서 없어졌으므로 남겨 둘 수 없다(읽을 때 오류). 중학생인지 고등학생인지 알 수
--    없으면 MIDDLE_SCHOOL_STUDENT로 옮긴다. 이 경우 고등학생이었던 행이 중학생으로 남는다.
--  - GENERAL은 유효한 값이라 그대로 둔다. 교직원이나 학부모였어도 근거가 없으면 일반인으로 남는다.
-- 이미 옮긴 행은 조건에 걸리지 않으므로 여러 번 실행해도 결과가 같다.
UPDATE tb_standard_participant
SET occupation = CASE
                     WHEN information_json::text LIKE '%"HIGH_SCHOOL_STUDENT"%' THEN 'HIGH_SCHOOL_STUDENT'
                     ELSE 'MIDDLE_SCHOOL_STUDENT'
    END
WHERE occupation = 'MIDDLE_HIGH_SCHOOL_STUDENT';

UPDATE tb_standard_participant
SET occupation = CASE
                     WHEN information_json::text LIKE '%"SCHOOL_STAFF"%' THEN 'SCHOOL_STAFF'
                     ELSE 'PARENT'
    END
WHERE occupation = 'GENERAL'
  AND (information_json::text LIKE '%"SCHOOL_STAFF"%' OR information_json::text LIKE '%"PARENT"%');
