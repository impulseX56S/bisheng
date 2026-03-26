-- 应用信息表
CREATE TABLE IF NOT EXISTS `app_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `app_id` VARCHAR(64) NOT NULL COMMENT '应用 ID (App ID)',
    `master_secret` VARCHAR(128) NOT NULL COMMENT '主密钥 (加密存储)',
    `secret_version` INT DEFAULT 1 COMMENT '派生密钥版本',
    `score_source` VARCHAR(128) NOT NULL COMMENT '学分来源',
    `app_name` VARCHAR(128) NOT NULL COMMENT '应用名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '应用描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `ip_whitelist` TEXT DEFAULT NULL COMMENT 'IP 白名单 (逗号分隔)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_id` (`app_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用信息表';

-- 插入测试数据
INSERT INTO `app_info` (`app_id`, `master_secret`, `secret_version`,score_source, `app_name`, `description`, `status`, `ip_whitelist`)
VALUES 
('test-app-001', 'my-super-secret-master-key-2024-test-app-001', 1, 'ebowin','测试应用', '用于测试的应用', 1, ''),
('edoctor', 'demo-master-secret-key-for-demo-app', 1, 'e-doctor','医博士', '用于演示的应用', 1, '127.0.0.1,0:0:0:0:0:0:0:1');
