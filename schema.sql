-- 短链接服务 · 建库建表
-- 用法：Get-Content .\schema.sql | mysql -u root -p
-- 注意：PowerShell 不支持 "<" 输入重定向，用管道

CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARSET utf8mb4;
USE shortlink;

-- 主表：短码 -> 长链接
CREATE TABLE IF NOT EXISTS short_link (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(10)  NOT NULL,
    long_url    VARCHAR(2048) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at   DATETIME NULL,
    status      TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_code (code),
    KEY idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 统计表：按天汇总，避免高频 UPDATE 主表
CREATE TABLE IF NOT EXISTS link_click (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(10) NOT NULL,
    click_date  DATE NOT NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_code_date (code, click_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 验证
SHOW TABLES;
DESC short_link;
