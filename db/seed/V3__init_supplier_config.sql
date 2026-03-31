-- V3__init_supplier_config.sql
-- 初始化供应商配置数据

INSERT INTO `notify_supplier_config` 
(`supplier_type`, `supplier_name`, `base_url`, `auth_type`, `timeout_ms`, `max_retry_count`, `rate_limit_qps`, `remark`) 
VALUES
('AD_SYSTEM', '广告系统', 'http://ad-system.internal/api', 'NONE', 30000, 10, 100, '用户注册通知广告系统'),
('CRM', 'CRM 系统', 'http://crm.internal/api', 'NONE', 30000, 10, 100, '支付成功通知 CRM'),
('INVENTORY', '库存系统', 'http://inventory.internal/api', 'NONE', 30000, 10, 100, '订单完成通知库存系统'),
('EMAIL', '邮件服务', 'http://email.internal/api', 'NONE', 30000, 10, 50, '邮件通知服务'),
('SMS', '短信服务', 'http://sms.internal/api', 'NONE', 30000, 10, 50, '短信通知服务')
ON DUPLICATE KEY UPDATE 
`supplier_name` = VALUES(`supplier_name`),
`base_url` = VALUES(`base_url`),
`remark` = VALUES(`remark`);
