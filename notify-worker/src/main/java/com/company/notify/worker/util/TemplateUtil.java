package com.company.notify.worker.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板工具类
 * 用于替换模板中的变量
 */
public class TemplateUtil {
    
    /**
     * 变量占位符正则：{{variableName}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    /**
     * 替换模板中的变量
     * 
     * @param template 模板字符串
     * @param variables 变量映射表
     * @return 替换后的字符串
     */
    public static String replaceVariables(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            
            // 如果变量存在，替换它；否则保留原样
            String replacement = (value != null) ? String.valueOf(value) : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 替换模板中的变量（支持默认值）
     * 格式：{{variableName:defaultValue}}
     * 
     * @param template 模板字符串
     * @param variables 变量映射表
     * @return 替换后的字符串
     */
    public static String replaceVariablesWithDefault(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        
        // 支持默认值的正则：{{variableName:defaultValue}}
        Pattern pattern = Pattern.compile("\\{\\{([^}:]+)(?::([^}]*))?\\}\\}");
        StringBuffer result = new StringBuffer();
        Matcher matcher = pattern.matcher(template);
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String defaultValue = matcher.group(2);
            
            Object value = variables.get(variableName);
            String replacement;
            
            if (value != null) {
                replacement = String.valueOf(value);
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                replacement = matcher.group(0); // 保留原样
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
}
