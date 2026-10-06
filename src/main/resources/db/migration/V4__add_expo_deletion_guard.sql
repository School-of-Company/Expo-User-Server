-- 박람회 삭제를 시작했다는 기록. 삭제가 끝난 뒤에도 남겨서, 늦게 도착한 요청이나 이벤트가 이미 지운
-- 박람회의 참가자를 다시 만들지 못하게 한다.
CREATE TABLE tb_expo_deletion
(
    expo_id      VARCHAR(36) PRIMARY KEY,
    started_at   TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

-- 삭제 중이거나 삭제된 박람회의 참가자를 만들거나 그 박람회로 옮기는 것을 막는다.
-- 코드가 아니라 DB가 막으므로 앞으로 어떤 쓰기 경로가 생겨도 우회할 수 없다.
--
-- 삭제와 쓰기가 동시에 일어나는 경우: 쓰는 쪽은 박람회별 공유 advisory lock을, 삭제하는 쪽은 같은 키의
-- 배타 lock을 트랜잭션 끝까지 쥔다(키 네임스페이스 26은 ExpoDeletionLock.NAMESPACE와 같아야 한다).
--  - 쓰기가 먼저면 삭제가 쓰기의 커밋을 기다렸다가 그 행까지 지운다.
--  - 삭제가 먼저면 쓰기가 삭제의 커밋을 기다렸다가, 기록을 보고 거부된다.
-- 기록 행만으로는 아직 커밋되지 않은 삭제를 쓰기 쪽이 볼 수 없어서 lock이 필요하다.
CREATE FUNCTION reject_participant_of_deleted_expo() RETURNS trigger AS
$$
BEGIN
    PERFORM pg_advisory_xact_lock_shared(26, hashtext(NEW.expo_id));
    IF EXISTS (SELECT 1 FROM tb_expo_deletion WHERE expo_id = NEW.expo_id) THEN
        RAISE EXCEPTION 'expo % is deleted or being deleted', NEW.expo_id USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_trainee_expo_deletion_guard
    BEFORE INSERT OR UPDATE OF expo_id
    ON tb_trainee
    FOR EACH ROW
EXECUTE FUNCTION reject_participant_of_deleted_expo();

CREATE TRIGGER trg_standard_participant_expo_deletion_guard
    BEFORE INSERT OR UPDATE OF expo_id
    ON tb_standard_participant
    FOR EACH ROW
EXECUTE FUNCTION reject_participant_of_deleted_expo();
