package com.wk.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        // 配置 Long 类型序列化为字符串，避免 JavaScript 精度丢失
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, new com.fasterxml.jackson.databind.ser.std.ToStringSerializer());
        module.addSerializer(long.class, new com.fasterxml.jackson.databind.ser.std.ToStringSerializer());
        mapper.registerModule(module);
        
        return mapper;
    }
}
