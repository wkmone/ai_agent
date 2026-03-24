package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LLMIntentClassifier implements IntentClassifier {
    private static final Logger log = LoggerFactory.getLogger(LLMIntentClassifier.class);
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Value("${rag.query.intent-classification.enabled:false}")
    private boolean enabled;
    
    @Override
    public QueryIntent classify(String query) {
        if (!enabled || chatClient == null) {
            return QueryIntent.OPEN_DOMAIN;
        }
        
        try {
            String prompt = """
            请判断以下查询的意图类型：
            %s
            
            可选类型：FACTUAL, EXPLANATORY, COMPARATIVE, PROCEDURAL, OPEN_DOMAIN
            只返回类型名称：
            """.formatted(query);
            
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result != null) {
                String intentStr = result.trim().toUpperCase();
                try {
                    QueryIntent intent = QueryIntent.valueOf(intentStr);
                    log.info("意图识别: 查询='{}', 意图={}", query, intent);
                    return intent;
                } catch (IllegalArgumentException e) {
                    log.warn("无法解析意图类型: {}, 使用默认 OPEN_DOMAIN", intentStr);
                }
            }
            
        } catch (Exception e) {
            log.warn("意图识别失败，使用默认 OPEN_DOMAIN: {}", e.getMessage());
        }
        
        return QueryIntent.OPEN_DOMAIN;
    }
}
