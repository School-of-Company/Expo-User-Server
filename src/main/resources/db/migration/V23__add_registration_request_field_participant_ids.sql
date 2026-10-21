-- 같은 requestId 재시도가 처음 응답과 같은 "이번에 FIELD가 된 참가자" ID 목록을 돌려주도록 보관한다. 신청 서비스가 이 목록으로
-- 입장 서비스에 현장등록을 알린다(Application #275). 연수자 등록과 이전에 쌓인 행은 NULL이며, 재시도 응답에서는 이 필드를 뺀다.
ALTER TABLE tb_registration_request
    ADD COLUMN field_participant_ids TEXT;
