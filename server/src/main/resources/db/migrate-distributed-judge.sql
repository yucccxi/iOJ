-- Migration for the distributed judge system.
--
-- Run this once on an existing `ioj` database before starting the new server. A fresh database can
-- use db/schema.sql directly and does not need this file.
--
-- Back up the database first:
--   mysqldump -uioj -p --single-transaction ioj > ioj-before-distributed-judge.sql
--
-- The script is intentionally not idempotent for the ALTER TABLE statements: rerunning it after a
-- successful migration reports duplicate column/index errors. That is safer than silently skipping a
-- half-applied migration.

CREATE TABLE IF NOT EXISTS judge_keys (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    key_prefix   VARCHAR(16)  NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   DATETIME     NULL,
    last_used_at DATETIME     NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL,
    CONSTRAINT uk_judge_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_judge_keys_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_judge_keys_prefix ON judge_keys (key_prefix);

CREATE TABLE IF NOT EXISTS judge_nodes (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    key_id            BIGINT       NOT NULL,
    hostname          VARCHAR(128) NULL,
    version           VARCHAR(32)  NULL,
    languages         VARCHAR(255) NULL,
    max_concurrency   INT          NOT NULL DEFAULT 1,
    running_tasks     INT          NOT NULL DEFAULT 0,
    isolate_version   VARCHAR(64)  NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'ONLINE',
    last_heartbeat_at DATETIME     NULL,
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    CONSTRAINT uk_judge_nodes_key UNIQUE (key_id),
    CONSTRAINT fk_judge_nodes_key FOREIGN KEY (key_id) REFERENCES judge_keys (id) ON DELETE CASCADE
);

CREATE INDEX idx_judge_nodes_heartbeat ON judge_nodes (last_heartbeat_at);

ALTER TABLE submissions
    ADD COLUMN judge_key_id     BIGINT      NULL,
    ADD COLUMN judge_node_id    VARCHAR(64) NULL,
    ADD COLUMN claim_token      VARCHAR(64) NULL,
    ADD COLUMN claimed_at       DATETIME    NULL,
    ADD COLUMN lease_expires_at DATETIME    NULL,
    ADD COLUMN attempt_count    INT         NOT NULL DEFAULT 0;

ALTER TABLE submissions
    ADD CONSTRAINT fk_submissions_judge_key
        FOREIGN KEY (judge_key_id) REFERENCES judge_keys (id) ON DELETE SET NULL;

CREATE INDEX idx_submissions_lease ON submissions (status, lease_expires_at);
CREATE INDEX idx_submissions_judge_key ON submissions (judge_key_id, status);
