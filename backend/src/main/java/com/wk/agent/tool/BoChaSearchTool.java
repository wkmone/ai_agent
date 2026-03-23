package com.wk.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * BoChaAPI网页搜索工具
 * 基于BoChaAPI实现的实战网页搜索引擎，支持智能搜索和知识图谱查询
 * 
 * 使用Spring AI的@Tool注解，自动被Spring AI发现和注册
 */
@Component
public class BoChaSearchTool {
    
    private static final Logger logger = LoggerFactory.getLogger(BoChaSearchTool.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.bocha.api-url:https://api.bocha.cn/v1/web-search}")
    private String bochaApiUrl;
    
    @Value("${app.bocha.api-key:}")
    private String bochaApiKey;
    
    public BoChaSearchTool(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 执行网页搜索
     * 
     * @param query 搜索查询
     * @return 搜索结果
     */
    @Tool(description = "网页搜索引擎，用于搜索互联网上的最新信息。支持智能搜索和知识图谱查询。")
    public String webSearch(
            @ToolParam(description = "搜索关键词或问题") String query) {
        
        if (bochaApiKey == null || bochaApiKey.isEmpty()) {
            return "错误：BoChaAPI密钥未配置，请在application.yml中设置 app.bocha.api-key";
        }
        
        try {
            logger.info("正在执行 BoChaAPI 网页搜索: {}", query);
            
            // 构建请求参数
            Map<String, Object> apiParams = new HashMap<>();
            apiParams.put("query", query);
            apiParams.put("summary", true);
            apiParams.put("count", 5);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + bochaApiKey);
            
            // 发送请求
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(apiParams, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                bochaApiUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            // 处理响应
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                logger.info("BoChaAPI 搜索成功，响应状态: {}", response.getStatusCode());
                
                // 解析JSON响应
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                
                // 提取搜索结果并格式化为字符串
                if (result.containsKey("data") && result.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    
                    if (data.containsKey("webPages") && data.get("webPages") instanceof List) {
                        List<Map<String, Object>> webPages = (List<Map<String, Object>>) data.get("webPages");
                        
                        if (!webPages.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("🔍 搜索结果 (").append(query).append("):\n\n");
                            
                            for (int i = 0; i < webPages.size(); i++) {
                                Map<String, Object> page = webPages.get(i);
                                String title = (String) page.getOrDefault("name", "未知标题");
                                String snippet = (String) page.getOrDefault("snippet", "无摘要");
                                String url = (String) page.getOrDefault("url", "");
                                
                                sb.append(String.format("%d. %s\n", i + 1, title));
                                if (!snippet.isEmpty()) {
                                    sb.append(String.format("   %s\n", snippet));
                                }
                                if (!url.isEmpty()) {
                                    sb.append(String.format("   🔗 %s\n", url));
                                }
                                sb.append("\n");
                            }
                            
                            return sb.toString();
                        } else {
                            return "未找到相关搜索结果。";
                        }
                    } else {
                        return "搜索结果格式异常。";
                    }
                } else {
                    return "搜索响应解析失败。";
                }
                
            } else {
                return "搜索失败，HTTP状态码: " + response.getStatusCode();
            }
            
        } catch (Exception e) {
            logger.error("BoChaAPI 搜索发生异常", e);
            return "搜索时发生错误: " + e.getMessage();
        }
    }
}
