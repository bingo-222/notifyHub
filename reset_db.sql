-- 删除旧表
DROP TABLE IF EXISTS notify_task_log;
DROP TABLE IF EXISTS notify_supplier_config;
DROP TABLE IF EXISTS notify_task;

-- 创建新表（使用修改后的 schema）
CREATE TABLE IF NOT EXISTS `notify_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务 ID',
    `biz_id` VARCHAR(64) NOT NULL COMMENT '业务 ID（用于幂等性校验）',
    `supplier_type` VARCHAR(32) NOT NULL COMMENT '供应商类型',
    `target_url` VARCHAR(512) COMMENT '目标 URL（为空则从供应商配置读取）',
    `http_method` VARCHAR(10) DEFAULT 'POST' COMMENT '请求方法',
    `headers` TEXT COMMENT '请求头（JSON）',
    `body` TEXT NOT NULL COMMENT '请求体（JSON）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry_count` INT NOT NULL DEFAULT 10 COMMENT '最大重试次数',
    `next_retry_at` DATETIME COMMENT '下次重试时间',
    `last_delivered_at` DATETIME COMMENT '最后投递时间',
    `failure_reason` VARCHAR(64) COMMENT '失败原因',
    `last_error_message` TEXT COMMENT '最后错误信息',
    `ext_data` TEXT COMMENT '扩展数据（JSON）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_id` (`biz_id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_at`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_supplier_type` (`supplier_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知任务表';

SELECT 'Table created successfully!' AS result;
