package com.company.notify.gateway.controller;

import com.company.notify.common.dto.ApiResponse;
import com.company.notify.common.dto.SubmitNotifyRequest;
import com.company.notify.gateway.service.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 通知提交控制器
 */
@RestController
@RequestMapping("/notify")
public class NotifyController {
    
    private static final Logger log = LoggerFactory.getLogger(NotifyController.class);
    
    @Autowired
    private NotifyService notifyService;
    
    /**
     * 提交通知任务
     */
    @PostMapping("/submit")
    public ApiResponse<Long> submit(@RequestBody @Validated SubmitNotifyRequest request) {
        log.info("收到通知提交请求：bizId={}, supplierCode={}", 
                request.getBizId(), request.getSupplierCode());
        
        try {
            Long taskId = notifyService.submit(request);
            log.info("通知任务提交成功：taskId={}, bizId={}", taskId, request.getBizId());
            return ApiResponse.success(taskId);
        } catch (Exception e) {
            log.error("通知任务提交失败：bizId={}", request.getBizId(), e);
            return ApiResponse.error(500, "提交失败：" + e.getMessage());
        }
    }
    
    /**
     * 查询任务状态
     */
    @GetMapping("/status/{taskId}")
    public ApiResponse<Object> getStatus(@PathVariable Long taskId) {
        try {
            Object taskInfo = notifyService.getTaskStatus(taskId);
            if (taskInfo == null) {
                return ApiResponse.error(404, "任务不存在");
            }
            return ApiResponse.success(taskInfo);
        } catch (Exception e) {
            log.error("查询任务状态失败：taskId={}", taskId, e);
            return ApiResponse.error(500, "查询失败：" + e.getMessage());
        }
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP");
    }
}