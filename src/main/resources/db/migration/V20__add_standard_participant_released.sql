-- 신청이 취소된 뒤 다시 신청하지 않은 동행자를 표시한다. 행과 code는 지우지 않고 대표자의 관리 목록(managed)에서만 뺀다.
-- 기존 행은 모두 FALSE다. 이 마이그레이션 이전에 이미 취소된 동행자는 알 수 없으므로 추정해 채우지 않고,
-- 대표자가 다음에 재신청할 때 신청 서비스가 알려 주는 releasedParticipantIds로 표시된다.
ALTER TABLE tb_standard_participant
    ADD COLUMN released BOOLEAN NOT NULL DEFAULT FALSE;
