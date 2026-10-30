-- iOJ 初始数据
-- 默认管理员：admin / admin123456（BCrypt 哈希），首次登录后请立即修改密码。
-- 使用：mysql -u ioj -p ioj < seed.sql
-- 使用 INSERT IGNORE，重复执行不会报主键/唯一键冲突。

INSERT IGNORE INTO users (username, email, password_hash, nickname, role, status, created_at, updated_at)
VALUES ('admin', 'admin@example.com',
        '$2a$10$p5S9jKELfHSubkAuQtRhn.L/oiSLbQ/SNOTP2tjgChJhqFOPOGcUG',
        '管理员', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO tags (name) VALUES
    ('入门'), ('模拟'), ('数学'), ('贪心'), ('二分'),
    ('动态规划'), ('图论'), ('数据结构'), ('字符串'), ('搜索');
