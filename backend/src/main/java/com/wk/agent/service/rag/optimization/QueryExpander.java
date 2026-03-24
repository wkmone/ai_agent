package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryExpander {
    private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);
    
    @Autowired(required = false)
    private ChatClient chatClient;
    
    @Value("${rag.query-optimization.mqe.enabled:false}")
    private boolean mqeEnabled;
    
    @Value("${rag.query-optimization.mqe.num-queries:3}")
    private int defaultNumQueries;
    
    @Value("${rag.query-optimization.mqe.prompt:请为以下查询生成 %d 个语义等价但表述不同的查询，每行一个，不要编号：}")
    private String mqePromptTemplate;
    
    public List<String> expand(String query) {
        return expand(query, defaultNumQueries);
    }
    
    public List<String> expand(String query, int numQueries) {
        if (!mqeEnabled || chatClient == null || numQueries <= 0) {
            log.debug("MQE is disabled or ChatClient not available, returning original query");
            List<String> result = new ArrayList<>();
            result.add(query);
            return result;
        }
        
        try {
            log.info("Expanding query with MQE: {}, num queries: {}", query, numQueries);
            
            String prompt = String.format(mqePromptTemplate, numQueries) + "\n" + query;
            
            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
            
            List<String> expandedQueries = parseExpandedQueries(response, query);
            
            log.info("Expanded to {} queries", expandedQueries.size());
            
            return expandedQueries;
            
        } catch (Exception e) {
            log.warn("MQE expansion failed: {}", e.getMessage());
            List<String> result = new ArrayList<>();
            result.add(query);
            return result;
        }
    }
    
    private List<String> parseExpandedQueries(String response, String originalQuery) {
        List<String> queries = new ArrayList<>();
        queries.add(originalQuery);
        
        if (response == null || response.trim().isEmpty()) {
            return queries;
        }
        
        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                String cleanQuery = removeNumbering(trimmed);
                if (!cleanQuery.equals(originalQuery) && !queries.contains(cleanQuery)) {
                    queries.add(cleanQuery);
                }
            }
        }
        
        return queries;
    }
    
    private String removeNumbering(String query) {
        String result = query;
        
        result = result.replaceAll("^\\d+\\.\\s*", "");
        result = result.replaceAll("^\\d+\\)\\s*", "");
        result = result.replaceAll("^[-*•]\\s*", "");
        
        result = result.replaceAll("^\\d+\\.", "");
        result = result.replaceAll("^\\d+\\)", "");
        result = result.replaceAll("^[-*•]", "");
        
        return result.trim();
    }
    
    public boolean isEnabled() {
        return mqeEnabled && chatClient != null;
    }
}
