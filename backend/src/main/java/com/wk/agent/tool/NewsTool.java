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
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 新闻服务工具
 * 使用BoChaAPI搜索获取真实新闻数据
 */
@Component
public class NewsTool {
    
    private static final Logger logger = LoggerFactory.getLogger(NewsTool.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.bocha.api-url:https://api.bocha.cn/v1/web-search}")
    private String bochaApiUrl;
    
    @Value("${app.bocha.api-key:}")
    private String bochaApiKey;
    
    public NewsTool(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Tool(description = "获取最新新闻信息，返回指定类别的真实新闻列表")
    public String getNews(
            @ToolParam(description = "新闻类别，如：科技、体育、娱乐、新闻等") String category,
            @ToolParam(description = "返回新闻数量，默认5条", required = false) Integer count) {
        
        try {
            logger.info("调用getNews工具，category: {}, count: {}", category, count);
            
            // 如果count为null，默认返回5条
            int newsCount = (count != null && count > 0) ? count : 5;
            
            // 构建搜索查询
            String query = String.format("%s最新新闻", category != null ? category : "");
            
            // 调用BoChaAPI搜索新闻
            String searchResult = searchNews(query, newsCount);
            
            logger.info("getNews工具返回成功，查询: {}", query);
            return searchResult;
            
        } catch (Exception e) {
            logger.error("获取新闻失败", e);
            return "获取新闻失败: " + e.getMessage();
        }
    }
    
    private String searchNews(String query, int count) {
        if (bochaApiKey == null || bochaApiKey.isEmpty()) {
            return "错误：BoChaAPI密钥未配置，请在application.yml中设置 app.bocha.api-key";
        }
        
        try {
            logger.info("正在搜索新闻: {}", query);
            
            // 构建请求参数
            Map<String, Object> apiParams = new HashMap<>();
            apiParams.put("query", query);
            apiParams.put("summary", true);
            apiParams.put("count", count);
            
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
                logger.info("新闻搜索成功，响应状态: {}", response.getStatusCode());
                
                // 解析JSON响应
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                
                // 提取搜索结果并格式化为字符串
                if (result.containsKey("data") && result.get("data") instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    
                    if (data.containsKey("webPages") && data.get("webPages") instanceof List) {
                        List<Map<String, Object>> webPages = (List<Map<String, Object>>) data.get("webPages");
                        
                        if (!webPages.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("📰 最新新闻\n");
                            sb.append("═══════════════════════════════════════\n\n");
                            
                            int displayCount = Math.min(count, webPages.size());
                            for (int i = 0; i < displayCount; i++) {
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
                            
                            sb.append("共 ").append(displayCount).append(" 条新闻");
                            
                            return sb.toString();
                        } else {
                            return "未找到相关新闻。";
                        }
                    } else {
                        return "新闻搜索结果格式异常。";
                    }
                } else {
                    return "新闻搜索响应解析失败。";
                }
                
            } else {
                return "新闻搜索失败，HTTP状态码: " + response.getStatusCode();
            }
            
        } catch (Exception e) {
            logger.error("新闻搜索发生异常", e);
            return "新闻搜索时发生错误: " + e.getMessage();
        }
    }
}
