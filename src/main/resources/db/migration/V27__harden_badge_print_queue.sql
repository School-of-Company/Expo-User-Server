-- 명찰 출력 큐 보완(#245, #246).

-- 출력대 등록 멱등키(#245). 같은 박람회에서 같은 키로 다시 등록하면 새로 만들지 않고 기존 출력대를 준다. 키 없는 등록은
-- 지금처럼 매번 새 출력대다. 동시 등록은 이 유일 인덱스에 대한 INSERT ... ON CONFLICT DO NOTHING이 판정한다.
ALTER TABLE tb_badge_print_station
    ADD COLUMN request_key UUID;

CREATE UNIQUE INDEX uk_badge_print_station_request ON tb_badge_print_station (expo_id, request_key) WHERE request_key IS NOT NULL;

-- 결과 모름(EXPIRED·TAKEN_OVER·ABANDONED)으로 닫힌 시도에 늦은 결과 보고를 반영하면 outcome·reason·finished_at은 보고값이
-- 되고, 서버가 닫았던 사유와 시각을 여기에 옮겨 둔다(#245). 늦게 반영한 시도만 값이 있다.
ALTER TABLE tb_badge_print_attempt
    ADD COLUMN previous_outcome     VARCHAR(20),
    ADD COLUMN previous_finished_at TIMESTAMP,
    ADD CHECK ((previous_outcome IS NULL) = (previous_finished_at IS NULL));

-- 대기 순서(#246). 자동 원본 작업은 저장 시각, 재출력 작업은 원본의 값을 복사해 재출력도 원래 자리에서 나간다. claim은
-- queued_at, id 순서다. 시각은 다른 큐 시각과 같이 DB 시계의 한국 시간이다. 기존 행은 재출력 사슬의 원본 created_at으로 채운다.
ALTER TABLE tb_badge_print_job
    ADD COLUMN queued_at TIMESTAMP DEFAULT (now() AT TIME ZONE 'Asia/Seoul');

WITH RECURSIVE chain (id, root_created_at) AS (
    SELECT id, created_at FROM tb_badge_print_job WHERE original_job_id IS NULL
    UNION ALL
    SELECT j.id, c.root_created_at FROM tb_badge_print_job j JOIN chain c ON j.original_job_id = c.id
)
UPDATE tb_badge_print_job j
SET queued_at = c.root_created_at
FROM chain c
WHERE j.id = c.id;

ALTER TABLE tb_badge_print_job
    ALTER COLUMN queued_at SET NOT NULL;

DROP INDEX ix_badge_print_job_queue;
CREATE INDEX ix_badge_print_job_queue ON tb_badge_print_job (expo_id, attendance_date, status, queued_at, id);
