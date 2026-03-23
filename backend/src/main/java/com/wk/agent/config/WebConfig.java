package com.wk.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Web配置类
 * 提供RestTemplate等Web相关的Bean
 */
@Configuration
public class WebConfig {
    
    /**
     * 提供RestTemplate Bean
     * 用于HTTP请求
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
