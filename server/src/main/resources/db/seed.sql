-- iOJ seed data
-- Default administrator: admin / admin123456 (BCrypt hash). Change the password after the first login.
-- Usage: mysql -u ioj -p ioj < seed.sql
-- INSERT IGNORE keeps the script idempotent (no duplicate-key errors).

INSERT IGNORE INTO users (username, email, password_hash, nickname, role, status, created_at, updated_at)
VALUES ('admin', 'admin@example.com',
        '$2a$10$p5S9jKELfHSubkAuQtRhn.L/oiSLbQ/SNOTP2tjgChJhqFOPOGcUG',
        '管理员', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO tags (name) VALUES
    ('入门'), ('模拟'), ('数学'), ('贪心'), ('二分'),
    ('动态规划'), ('图论'), ('数据结构'), ('字符串'), ('搜索');
