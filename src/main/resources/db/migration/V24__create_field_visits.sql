CREATE TABLE tb_field_visit_creation (
    expo_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY (expo_id, request_id)
);

CREATE TABLE tb_field_visit_member (
    participant_id BIGINT PRIMARY KEY REFERENCES tb_standard_participant(id) ON DELETE CASCADE
);

CREATE TABLE tb_field_visit (
    registration_id UUID PRIMARY KEY,
    expo_id VARCHAR(36) NOT NULL,
    participant_id BIGINT NOT NULL REFERENCES tb_standard_participant(id) ON DELETE CASCADE,
    visit_date DATE NOT NULL,
    generation BIGINT NOT NULL CHECK (generation > 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    operation_id UUID NOT NULL
);
CREATE INDEX idx_field_visit_participant ON tb_field_visit(participant_id, status, visit_date);

CREATE TABLE tb_field_visit_operation (
    operation_id UUID PRIMARY KEY,
    expo_id VARCHAR(36) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL
);
