package com.company.notify.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商配置服务
 */
@Service
public class SupplierConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(SupplierConfigService.class);
    
    @Value("${app.notify.worker.supplier-config-path:classpath:supplier-config.yml}")
    private String configPath;
    
    /**
     * 供应商配置映射表：supplierCode -> SupplierConfig
     */
    private final Map<String, SupplierConfig> supplierConfigMap = new HashMap<>();
    
    /**
     * 启动时加载配置文件
     */
    @PostConstruct
    public void init() {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    @SuppressWarnings("unchecked")
    private void loadConfig() {
        try {
            log.info("开始加载供应商配置文件：{}", configPath);
            
            // 从 classpath 加载 YAML 文件
            InputStream inputStream = getClass().getClassLoader()
                    .getResourceAsStream("supplier-config.yml");
            
            if (inputStream == null) {
                log.warn("配置文件不存在：supplier-config.yml，将使用默认配置");
                return;
            }
            
            // 使用 SnakeYAML 解析（Spring Boot 已包含）
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> yamlMap = yaml.load(inputStream);
            
            if (yamlMap == null) {
                log.warn("配置文件为空");
                return;
            }
            
            List<Map<String, Object>> suppliers = 
                    (List<Map<String, Object>>) yamlMap.get("suppliers");
            
            if (suppliers == null || suppliers.isEmpty()) {
                log.warn("配置文件中没有定义供应商");
                return;
            }
            
            // 解析每个供应商配置
            for (Map<String, Object> supplier : suppliers) {
                SupplierConfig config = parseSupplierConfig(supplier);
                supplierConfigMap.put(config.getSupplierCode(), config);
                log.info("加载供应商配置：{} -> {}", config.getSupplierCode(), config.getTargetUrl());
            }
            
            log.info("成功加载 {} 个供应商配置", supplierConfigMap.size());
            
        } catch (Exception e) {
            log.error("加载供应商配置文件失败", e);
        }
    }
    
    /**
     * 解析单个供应商配置
     */
    @SuppressWarnings("unchecked")
    private SupplierConfig parseSupplierConfig(Map<String, Object> supplier) {
        SupplierConfig config = new SupplierConfig();
        
        config.setSupplierCode((String) supplier.get("supplierCode"));
        config.setTargetUrl((String) supplier.get("targetUrl"));
        config.setHttpMethod((String) supplier.get("httpMethod"));
        
        // 解析 headersTemplate
        Object headersObj = supplier.get("headersTemplate");
        if (headersObj instanceof Map) {
            Map<String, String> headers = new HashMap<>();
            Map<String, Object> headersMap = (Map<String, Object>) headersObj;
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                headers.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            config.setHeadersTemplate(headers);
        }
        
        // 解析 bodyTemplate（可能是 Map 或 String）
        Object bodyObj = supplier.get("bodyTemplate");
        if (bodyObj instanceof String) {
            config.setBodyTemplate((String) bodyObj);
        } else if (bodyObj instanceof Map) {
            // 将 Map 转换为 JSON 字符串
            config.setBodyTemplate(mapToJson(bodyObj));
        }
        
        // 解析数值类型
        Object timeoutObj = supplier.get("timeoutMs");
        if (timeoutObj instanceof Number) {
            config.setTimeoutMs(((Number) timeoutObj).intValue());
        }
        
        Object retryObj = supplier.get("maxRetryCount");
        if (retryObj instanceof Number) {
            config.setMaxRetryCount(((Number) retryObj).intValue());
        }
        
        return config;
    }
    
    /**
     * 简单的 Map 转 JSON（不依赖额外库）
     */
    private String mapToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) obj;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value == null) {
                json.append("null");
            } else {
                json.append(String.valueOf(value));
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 根据供应商代码获取配置
     */
    public SupplierConfig getSupplierConfig(String supplierCode) {
        SupplierConfig config = supplierConfigMap.get(supplierCode);
        if (config == null) {
            log.warn("未找到供应商配置：{}", supplierCode);
        }
        return config;
    }
    
    /**
     * 获取所有供应商配置
     */
    public Map<String, SupplierConfig> getAllConfigs() {
        return new HashMap<>(supplierConfigMap);
    }
    
    /**
     * 重新加载配置（用于热更新）
     */
    public void reload() {
        supplierConfigMap.clear();
        loadConfig();
    }
}
