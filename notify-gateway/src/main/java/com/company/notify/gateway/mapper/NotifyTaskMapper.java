package com.company.notify.gateway.mapper;

import com.company.notify.common.model.NotifyTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知任务 Mapper
 */
@Mapper
public interface NotifyTaskMapper extends BaseMapper<NotifyTask> {
    
    /**
     * 查找最近的 PENDING 任务（用于补偿）
     */
    List<NotifyTask> findRecentPendingTasks(@Param("since") LocalDateTime since);
    
    /**
     * 根据业务 ID 查找任务
     */
    NotifyTask findByBizId(@Param("bizId") String bizId);
}
