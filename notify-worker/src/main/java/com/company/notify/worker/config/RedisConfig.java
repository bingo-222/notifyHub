package com.company.notify.worker.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// import org.springframework.data.redis.connection.RedisConnectionFactory;
// import org.springframework.data.redis.core.RedisTemplate;
// import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
// import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 * 注意：已禁用 RedisTemplate，改用 Redisson
 */
@Configuration
public class RedisConfig {
    
    // @Bean
    // public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    //     RedisTemplate<String, String> template = new RedisTemplate<>();
    //     template.setConnectionFactory(connectionFactory);
        
    //     // Key 序列化
    //     StringRedisSerializer stringSerializer = new StringRedisSerializer();
    //     template.setKeySerializer(stringSerializer);
    //     template.setHashKeySerializer(stringSerializer);
        
    //     // Value 序列化
    //     Jackson2JsonRedisSerializer<String> jacksonSerializer = new Jackson2JsonRedisSerializer<>(String.class);
    //     ObjectMapper objectMapper = new ObjectMapper();
    //     objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    //     objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
    //     jacksonSerializer.setObjectMapper(objectMapper);
        
    //     template.setValueSerializer(jacksonSerializer);
    //     template.setHashValueSerializer(jacksonSerializer);
        
    //     template.afterPropertiesSet();
    //     return template;
    // }
}