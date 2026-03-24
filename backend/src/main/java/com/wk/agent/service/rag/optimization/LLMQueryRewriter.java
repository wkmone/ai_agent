package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LLMQueryRewriter implements QueryRewriter {
    private static final Logger log = LoggerFactory.getLogger(LLMQueryRewriter.class);
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Value("${rag.query.rewrite.enabled:false}")
    private boolean enabled;
    
    @Value("${rag.query.rewrite.use-llm:true}")
    private boolean useLLM;
    
    @Override
    public String rewrite(String query, List<String> context) {
        if (!enabled || !useLLM || chatClient == null) {
            return query;
        }
        
        try {
            String prompt = buildRewritePrompt(query, context);
            String rewrittenQuery = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            log.info("查询重写: 原始查询='{}', 重写后='{}'", query, rewrittenQuery);
            return rewrittenQuery != null ? rewrittenQuery.trim() : query;
            
        } catch (Exception e) {
            log.warn("查询重写失败，使用原始查询: {}", e.getMessage());
            return query;
        }
    }
    
    private String buildRewritePrompt(String query, List<String> context) {
        String contextStr = context != null && !context.isEmpty() 
            ? String.join("\n", context) 
            : "无";
        
        return """
        你是一个查询优化专家。请将用户的查询改写为更适合检索的形式。
        
        要求：
        1. 保留核心语义
        2. 补充省略的主语和宾语
        3. 去除冗余词汇
        4. 使用更规范的表述
        
        对话历史：%s
        用户查询：%s
        
        改写后的查询：
        """.formatted(contextStr, query);
    }
}
