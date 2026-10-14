-- 박람회별 휴대전화 인증 완료 번호(고유). 관리자 화면의 "휴대전화 인증 건수" 집계용이다.
-- 전화번호 원문은 저장하지 않고 키가 있는 해시(HMAC-SHA256)만 저장한다. expo_id는 Expo 서비스 소유 ID라 FK를 두지 않는다.
CREATE TABLE tb_participant_phone_verification
(
    id                BIGSERIAL PRIMARY KEY,
    expo_id           VARCHAR(36) NOT NULL,
    phone_hash        CHAR(64)    NOT NULL,
    first_verified_at TIMESTAMP   NOT NULL,
    CONSTRAINT uk_participant_phone_verification UNIQUE (expo_id, phone_hash)
);
