package com.company.notify.worker.mapper;

import com.company.notify.common.model.NotifyTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知任务 Mapper
 */
@Mapper
public interface NotifyTaskMapper extends BaseMapper<NotifyTask> {
    // 继承 BaseMapper，无需额外方法
}
