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

CREATE TABLE scopes (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    created_at DATETIME     NOT NULL,
    CONSTRAINT uk_scopes_name UNIQUE (name)
);

CREATE TABLE problems (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(128) NOT NULL,
    difficulty      VARCHAR(16)  NOT NULL DEFAULT 'EASY',
    -- Numeric difficulty rating (0-5000) used for filtering and sorting; NULL when never rated.
    rating          INT          NULL,
    description     MEDIUMTEXT   NOT NULL,
    input_format    MEDIUMTEXT   NULL,
    output_format   MEDIUMTEXT   NULL,
    hint            MEDIUMTEXT   NULL,
    -- Reference to scopes. Local problems always point at the reserved LOCAL row; the column stays
    -- nullable so schemas created before LOCAL existed can be migrated without a table rebuild.
    scope_id        BIGINT       NULL,
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
    CONSTRAINT fk_problems_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_problems_scope FOREIGN KEY (scope_id) REFERENCES scopes (id)
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

-- External OJ catalog: metadata only (no statement, no test data, never judged here).
-- scope_id points at scopes (the same admin-managed table local problems use), so the
-- platform/contest list has a single source of truth. url is unique to stop duplicate imports.
CREATE TABLE external_problems (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope_id       BIGINT       NOT NULL,
    -- Short identifier used on the original OJ, for example CF_1234E or AT_ABC124F. NULL for rows
    -- imported before this column existed; the unique key still accepts any number of NULLs.
    code           VARCHAR(32)  NULL,
    title          VARCHAR(200) NOT NULL,
    rating         INT          NULL,
    -- Coarse difficulty derived from rating and the source platform:
    -- Codeforces <1400 / 1400-1999 / >=2000, AtCoder <800 / 800-1599 / >=1600.
    -- A missing rating cannot be classified and is stored as UNKNOWN.
    difficulty     VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    -- Contest time reported by clist (usually the contest start, possibly a mirror contest for
    -- AtCoder training problems). NULL when clist did not provide it.
    time           DATETIME     NULL,
    -- Statistics copied from the source OJ (or entered by an administrator). They are NOT
    -- incremented by this system, because external problems are never judged here.
    submit_count   INT          NOT NULL DEFAULT 0,
    accepted_count INT          NOT NULL DEFAULT 0,
    url            VARCHAR(512) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    CONSTRAINT uk_external_problems_url UNIQUE (url),
    CONSTRAINT uk_external_problems_code UNIQUE (code),
    CONSTRAINT fk_external_problems_scope FOREIGN KEY (scope_id) REFERENCES scopes (id)
);

CREATE TABLE external_problem_tags (
    external_problem_id BIGINT NOT NULL,
    tag_id              BIGINT NOT NULL,
    PRIMARY KEY (external_problem_id, tag_id),
    CONSTRAINT fk_ept_external FOREIGN KEY (external_problem_id) REFERENCES external_problems (id) ON DELETE CASCADE,
    CONSTRAINT fk_ept_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
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

-- Email verification codes. One row per send request: the newest row for an (email, purpose)
-- pair is the one that can be used, and consumed_at marks a code as no longer usable.
-- Purpose is REGISTER today; a password reset flow can reuse the same table.
CREATE TABLE email_verification_codes (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(128) NOT NULL,
    purpose     VARCHAR(16)  NOT NULL DEFAULT 'REGISTER',
    code        VARCHAR(16)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  DATETIME     NOT NULL,
    consumed_at DATETIME     NULL,
    created_at  DATETIME     NOT NULL
);

CREATE INDEX idx_problems_status_id ON problems (status, id);
CREATE INDEX idx_problems_status_rating ON problems (status, rating);
CREATE INDEX idx_problems_author ON problems (author_id, id);
CREATE INDEX idx_problems_scope ON problems (scope_id, id);
CREATE INDEX idx_problems_difficulty ON problems (difficulty);
CREATE INDEX idx_problem_tags_tag ON problem_tags (tag_id);
CREATE INDEX idx_external_problems_scope_rating ON external_problems (scope_id, rating);
CREATE INDEX idx_external_problems_rating ON external_problems (rating, id);
CREATE INDEX idx_external_problems_difficulty ON external_problems (difficulty);
CREATE INDEX idx_external_problems_time ON external_problems (time);
CREATE INDEX idx_external_problem_tags_tag ON external_problem_tags (tag_id);
CREATE INDEX idx_submissions_problem ON submissions (problem_id, id);
CREATE INDEX idx_submissions_user ON submissions (user_id, id);
CREATE INDEX idx_submissions_status ON submissions (status, id);
CREATE INDEX idx_email_verification_codes_email ON email_verification_codes (email, purpose, id);
CREATE INDEX idx_email_verification_codes_expires ON email_verification_codes (expires_at);

-- Reserved scope for locally authored problems. Every problems.scope_id points here; the scope
-- is created with the schema (not only in seed.sql) so a fresh database always has it, including
-- the H2 databases used by integration tests.
INSERT INTO scopes (name, created_at) VALUES ('LOCAL', CURRENT_TIMESTAMP);
