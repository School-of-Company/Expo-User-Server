-- 같은 requestId 재시도가 처음 응답과 같은 "대표자 포함 일행 전체" 참가자 ID 목록을 돌려주도록 보관한다. 문자 대상(participant_ids)과 다르다:
-- 기존 대표자에게 동행자를 추가하면 문자 대상은 새 동행자뿐이지만 이 목록은 대표자와 기존·신규 동행자 전체다. 이전에 쌓인 행은 NULL이다.
ALTER TABLE tb_registration_request
    ADD COLUMN group_participant_ids TEXT;
