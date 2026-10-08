CREATE TABLE tb_admin
(
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(10)  NOT NULL,
    nickname     VARCHAR(50)  NOT NULL UNIQUE,
    email        VARCHAR(100) NOT NULL UNIQUE,
    password     VARCHAR(100) NOT NULL,
    phone_number VARCHAR(15)  NOT NULL UNIQUE,
    authority    VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL
);

-- expo_id는 Expo 서비스 소유 ID라 FK를 두지 않는다
CREATE TABLE tb_trainee
(
    id                          BIGSERIAL PRIMARY KEY,
    expo_id                     VARCHAR(36) NOT NULL,
    name                        VARCHAR(10) NOT NULL,
    phone_number                VARCHAR(15) NOT NULL,
    training_id                 VARCHAR(15) NOT NULL,
    information_json            JSONB,
    personal_information_status BOOLEAN     NOT NULL,
    application_type            VARCHAR(10) NOT NULL,
    application_date            TIMESTAMP   NOT NULL,
    CONSTRAINT uk_trainee_expo_phone UNIQUE (expo_id, phone_number)
);

CREATE INDEX idx_trainee_expo_name ON tb_trainee (expo_id, name);

CREATE TABLE tb_trainee_participation
(
    id              BIGSERIAL PRIMARY KEY,
    entry_time      TIMESTAMP   NOT NULL,
    attendance_date DATE        NOT NULL,
    trainee_id      BIGINT      NOT NULL REFERENCES tb_trainee (id) ON DELETE CASCADE,
    expo_id         VARCHAR(36) NOT NULL,
    CONSTRAINT uk_trainee_participation UNIQUE (expo_id, trainee_id, attendance_date)
);

CREATE TABLE tb_standard_participant
(
    id                          BIGSERIAL PRIMARY KEY,
    expo_id                     VARCHAR(36) NOT NULL,
    name                        VARCHAR(10) NOT NULL,
    phone_number                VARCHAR(15) NOT NULL,
    information_json            JSONB,
    personal_information_status BOOLEAN     NOT NULL,
    application_type            VARCHAR(10) NOT NULL,
    sms_try_time                INTEGER     NOT NULL DEFAULT 0,
    application_date            TIMESTAMP   NOT NULL,
    CONSTRAINT uk_standard_participant_expo_phone UNIQUE (expo_id, phone_number)
);

CREATE INDEX idx_standard_participant_phone ON tb_standard_participant (phone_number);

CREATE TABLE tb_standard_participant_participation
(
    id                      BIGSERIAL PRIMARY KEY,
    entry_time              TIMESTAMP   NOT NULL,
    attendance_date         DATE        NOT NULL,
    standard_participant_id BIGINT      NOT NULL REFERENCES tb_standard_participant (id) ON DELETE CASCADE,
    expo_id                 VARCHAR(36) NOT NULL,
    CONSTRAINT uk_standard_participant_participation UNIQUE (expo_id, standard_participant_id, attendance_date)
);

-- survey_id는 Form 서비스 소유 ID라 FK를 두지 않는다. 답변 JSON은 Form 서비스가 검증한 값을 그대로 저장한다
CREATE TABLE tb_trainee_survey_answer
(
    id                          BIGSERIAL PRIMARY KEY,
    survey_id                   VARCHAR(36) NOT NULL,
    trainee_id                  BIGINT      NOT NULL REFERENCES tb_trainee (id) ON DELETE CASCADE,
    answer_json                 JSONB       NOT NULL,
    personal_information_status BOOLEAN     NOT NULL,
    CONSTRAINT uk_trainee_survey_answer UNIQUE (survey_id, trainee_id)
);

CREATE TABLE tb_standard_participant_survey_answer
(
    id                          BIGSERIAL PRIMARY KEY,
    survey_id                   VARCHAR(36) NOT NULL,
    standard_participant_id     BIGINT      NOT NULL REFERENCES tb_standard_participant (id) ON DELETE CASCADE,
    answer_json                 JSONB       NOT NULL,
    personal_information_status BOOLEAN     NOT NULL,
    CONSTRAINT uk_standard_participant_survey_answer UNIQUE (survey_id, standard_participant_id)
);
