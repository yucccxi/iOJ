-- Migration: cache the SHA-256 of each problem's test data bundle.
--
-- Run this once on an existing `ioj` database before starting the server version that uses the
-- hash cache. Fresh databases can use db/schema.sql directly and do not need this file.
--
-- The script is idempotent because it only creates a new table; it does not modify existing ones.

CREATE TABLE IF NOT EXISTS problem_testcase_bundles (
    problem_id       BIGINT      NOT NULL PRIMARY KEY,
    bundle_sha256    VARCHAR(64) NULL,
    testcase_version BIGINT      NOT NULL DEFAULT 0,
    updated_at       DATETIME    NOT NULL,
    CONSTRAINT fk_problem_testcase_bundles_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
);
