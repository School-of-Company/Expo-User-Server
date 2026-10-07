-- 직업(유형)을 사전등록 공식 요구사항의 6개(유, 초, 중고, 일반, 교사, 예비교사)로 맞춘다. Form 서비스의 Occupation과
-- 같은 값이다. 컬럼은 VARCHAR라서 값만 옮긴다: 중학생과 고등학생은 중고로, 교직원과 보호자는 일반으로 합친다.
-- 이전 값은 enum에서 없어졌으므로 남겨 두면 그 참가자를 읽을 때 오류가 난다.
UPDATE tb_standard_participant
SET occupation = CASE occupation
                     WHEN 'MIDDLE_SCHOOL_STUDENT' THEN 'MIDDLE_HIGH_SCHOOL_STUDENT'
                     WHEN 'HIGH_SCHOOL_STUDENT' THEN 'MIDDLE_HIGH_SCHOOL_STUDENT'
                     WHEN 'SCHOOL_STAFF' THEN 'GENERAL'
                     WHEN 'PARENT' THEN 'GENERAL'
    END
WHERE occupation IN ('MIDDLE_SCHOOL_STUDENT', 'HIGH_SCHOOL_STUDENT', 'SCHOOL_STAFF', 'PARENT');
