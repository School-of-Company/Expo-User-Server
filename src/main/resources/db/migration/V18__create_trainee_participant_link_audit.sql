-- 이어지지 않은(레거시) 연수자를 운영자가 확인한 참가자에 잇거나, 그렇게 이은 연결을 해제한 기록. 이름·전화번호·대표자 번호로
-- 자동으로 잇지 않으므로 모든 귀속은 누가(actor_admin_id) 무슨 근거로(reason) 했는지 여기에 남는다. 같은 상태로의 재시도는
-- 아무것도 바꾸지 않아 기록도 남기지 않는다. 해제는 이 기록의 마지막 행이 같은 참가자로의 LINK인 연결만 할 수 있어,
-- 참가자에서 만든 연수자(resolve-or-create-by-participant)의 연결은 풀리지 않는다.
--
-- 연수자를 지우면(박람회 삭제) 기록도 함께 지운다. 참가자는 연결이 바뀐 뒤의 기록이라 FK를 두지 않는다.
CREATE TABLE tb_trainee_participant_link_audit
(
    id                      BIGSERIAL PRIMARY KEY,
    trainee_id              BIGINT       NOT NULL REFERENCES tb_trainee (id) ON DELETE CASCADE,
    standard_participant_id BIGINT       NOT NULL,
    expo_id                 VARCHAR(36)  NOT NULL,
    action                  VARCHAR(10)  NOT NULL,
    actor_admin_id          BIGINT       NOT NULL,
    reason                  VARCHAR(500) NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_trainee_participant_link_audit_trainee ON tb_trainee_participant_link_audit (trainee_id, id);
