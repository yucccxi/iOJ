-- iOJ database schema
-- Notes:
--   1. Production uses MySQL 8.x (utf8mb4 / utf8mb4_general_ci recommended).
--   2. The same script must also run on H2 (MySQL mode) for integration tests, so MySQL-specific
--      syntax such as ENGINE/CHARSET/inline COMMENT is avoided; indexes use CREATE INDEX.
--   3. See docs/server/04-deployment.md for the database setup commands.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    email         VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    last_login_at DATETIME     NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE problems (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(128) NOT NULL,
    difficulty      VARCHAR(16)  NOT NULL DEFAULT 'EASY',
    description     MEDIUMTEXT   NOT NULL,
    input_format    MEDIUMTEXT   NULL,
    output_format   MEDIUMTEXT   NULL,
    hint            MEDIUMTEXT   NULL,
    source          VARCHAR(128) NULL,
    -- Custom checker (special judge) source code, compiled and run by the judge.
    -- NULL or blank means the built-in OutputComparator is used.
    checker_code    MEDIUMTEXT   NULL,
    time_limit_ms   INT          NOT NULL DEFAULT 1000,
    memory_limit_mb INT          NOT NULL DEFAULT 256,
    author_id       BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING_REVIEW',
    submit_count    INT          NOT NULL DEFAULT 0,
    accepted_count  INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    CONSTRAINT fk_problems_author FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE TABLE problem_samples (
    id          BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    problem_id  BIGINT     NOT NULL,
    ordinal     INT        NOT NULL,
    input_data  MEDIUMTEXT NOT NULL,
    output_data MEDIUMTEXT NOT NULL,
    explanation MEDIUMTEXT NULL,
    CONSTRAINT uk_samples_problem_ordinal UNIQUE (problem_id, ordinal),
    CONSTRAINT fk_samples_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
);

CREATE TABLE problem_testcases (
    id          BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
    problem_id  BIGINT     NOT NULL,
    ordinal     INT        NOT NULL,
    input_data  MEDIUMTEXT NOT NULL,
    output_data MEDIUMTEXT NOT NULL,
    created_at  DATETIME   NOT NULL,
    CONSTRAINT uk_testcases_problem_ordinal UNIQUE (problem_id, ordinal),
    CONSTRAINT fk_testcases_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
);

CREATE TABLE tags (
    id   BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL,
    CONSTRAINT uk_tags_name UNIQUE (name)
);

CREATE TABLE problem_tags (
    problem_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_problem_tags_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE submissions (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    problem_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    language        VARCHAR(16)  NOT NULL,
    code            MEDIUMTEXT   NOT NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    time_used_ms    INT          NULL,
    memory_used_kb  INT          NULL,
    compile_message MEDIUMTEXT   NULL,
    -- Holds system-level errors, including the compiler output of a broken custom checker.
    judge_message   TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    judged_at       DATETIME     NULL,
    CONSTRAINT fk_submissions_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE submission_cases (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT        NOT NULL,
    case_ordinal  INT           NOT NULL,
    status        VARCHAR(24)   NOT NULL,
    time_used_ms  INT           NULL,
    memory_used_kb INT          NULL,
    message       VARCHAR(1024) NULL,
    CONSTRAINT uk_submission_cases_ordinal UNIQUE (submission_id, case_ordinal),
    CONSTRAINT fk_submission_cases_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_problems_status_id ON problems (status, id);
CREATE INDEX idx_problems_author ON problems (author_id, id);
CREATE INDEX idx_problems_difficulty ON problems (difficulty);
CREATE INDEX idx_problem_tags_tag ON problem_tags (tag_id);
CREATE INDEX idx_submissions_problem ON submissions (problem_id, id);
CREATE INDEX idx_submissions_user ON submissions (user_id, id);
CREATE INDEX idx_submissions_status ON submissions (status, id);
