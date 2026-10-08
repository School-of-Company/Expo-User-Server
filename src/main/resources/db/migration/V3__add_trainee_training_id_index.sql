-- 내부 조회 API가 박람회와 연수 번호(`training_id`)로 연수자를 찾는다. 유일 제약은 걸지 않는다: v1은 중복을
-- 서비스에서만 막았고 기존 데이터에 중복이 있을 수 있으며, 중복은 조회가 409로 구분한다.
CREATE INDEX idx_trainee_expo_training_id ON tb_trainee (expo_id, training_id);
