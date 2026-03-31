-- V2__add_index_optimization.sql
-- 添加索引优化查询性能

-- 为死信任务添加索引
ALTER TABLE `notify_task` 
ADD KEY `idx_status_dead_letter` (`status`, `updated_at`) 
COMMENT '死信任务查询索引';

-- 为任务状态统计添加索引
ALTER TABLE `notify_task` 
ADD KEY `idx_status_created` (`status`, `created_at`) 
COMMENT '状态统计索引';
