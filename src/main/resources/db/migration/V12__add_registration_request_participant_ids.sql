-- 같은 requestId 재시도가 처음 응답과 같은 참가자 ID 목록을 돌려주도록 보관한다. 이전에 쌓인 행은 NULL이다.
ALTER TABLE tb_registration_request
    ADD COLUMN participant_ids TEXT;
