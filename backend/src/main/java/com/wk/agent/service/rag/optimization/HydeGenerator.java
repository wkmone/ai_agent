package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HydeGenerator {
    private static final Logger log = LoggerFactory.getLogger(HydeGenerator.class);
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Value("${rag.query-optimization.hyde.enabled:false}")
    private boolean hydeEnabled;
    
    @Value("${rag.query-optimization.hyde.prompt:请根据以下问题，生成一段可能的答案段落（不要分析过程，直接给出答案）：}")
    private String hydePromptTemplate;
    
    public String generate(String query) {
        if (!hydeEnabled || chatClient == null) {
            log.debug("HyDE is disabled or ChatClient not available, returning null");
            return null;
        }
        
        try {
            log.info("Generating HyDE document for query: {}", query);
            
            String prompt = hydePromptTemplate + "\n" + query;
            
            String hypotheticalDoc = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            log.debug("Generated HyDE document: {}", 
                hypotheticalDoc != null && hypotheticalDoc.length() > 100 
                    ? hypotheticalDoc.substring(0, 100) + "..." 
                    : hypotheticalDoc);
            
            return hypotheticalDoc;
            
        } catch (Exception e) {
            log.warn("HyDE generation failed: {}", e.getMessage());
            return null;
        }
    }
    
    public boolean isEnabled() {
        return hydeEnabled && chatClient != null;
    }
}
