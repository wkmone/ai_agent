package com.wk.agent.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);

    @Autowired
    private ChatClient chatClient;

    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        log.info("重排序: query={}, documents={}, topK={}", query, documents.size(), topK);

        try {
            List<Map<String, Object>> scoredDocs = new ArrayList<>();
            
            for (int i = 0; i < documents.size(); i++) {
                Map<String, Object> doc = documents.get(i);
                String content = (String) doc.get("content");
                
                double score = calculateRelevanceScore(query, content);
                
                Map<String, Object> scoredDoc = new HashMap<>(doc);
                scoredDoc.put("rerankScore", score);
                scoredDocs.add(scoredDoc);
            }
            
            scoredDocs.sort((a, b) -> {
                Double scoreA = (Double) a.get("rerankScore");
                Double scoreB = (Double) b.get("rerankScore");
                return Double.compare(scoreB != null ? scoreB : 0, scoreA != null ? scoreA : 0);
            });
            
            if (topK > 0 && scoredDocs.size() > topK) {
                scoredDocs = scoredDocs.subList(0, topK);
            }
            
            return scoredDocs;
            
        } catch (Exception e) {
            log.error("重排序失败: {}", e.getMessage());
            return documents;
        }
    }

    public List<Map<String, Object>> llmRerank(String query, List<Map<String, Object>> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        log.info("LLM重排序: query={}, documents={}", query, documents.size());

        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("请对以下文档与查询的相关性进行评分（0-10分），返回JSON格式的评分结果。\n\n");
            prompt.append("查询: ").append(query).append("\n\n");
            prompt.append("文档列表:\n");
            
            for (int i = 0; i < documents.size(); i++) {
                String content = (String) documents.get(i).get("content");
                String truncated = content != null && content.length() > 300 
                    ? content.substring(0, 300) + "..." 
                    : content;
                prompt.append("[").append(i).append("] ").append(truncated).append("\n\n");
            }
            
            prompt.append("请返回JSON格式: {\"scores\": [{\"index\": 0, \"score\": 8}, ...]}");
            
            String response = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();
            
            Map<Integer, Double> scoreMap = parseLlmScores(response);
            
            List<Map<String, Object>> scoredDocs = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                Map<String, Object> doc = new HashMap<>(documents.get(i));
                doc.put("llmRerankScore", scoreMap.getOrDefault(i, 5.0));
                scoredDocs.add(doc);
            }
            
            scoredDocs.sort((a, b) -> {
                Double scoreA = (Double) a.get("llmRerankScore");
                Double scoreB = (Double) b.get("llmRerankScore");
                return Double.compare(scoreB != null ? scoreB : 0, scoreA != null ? scoreA : 0);
            });
            
            if (topK > 0 && scoredDocs.size() > topK) {
                scoredDocs = scoredDocs.subList(0, topK);
            }
            
            return scoredDocs;
            
        } catch (Exception e) {
            log.error("LLM重排序失败: {}", e.getMessage());
            return rerank(query, documents, topK);
        }
    }

    private double calculateRelevanceScore(String query, String content) {
        if (query == null || content == null) return 0.5;
        
        double score = 0.0;
        
        String[] queryTerms = query.toLowerCase().split("\\s+");
        String lowerContent = content.toLowerCase();
        
        int matchCount = 0;
        for (String term : queryTerms) {
            if (term.length() > 1 && lowerContent.contains(term)) {
                matchCount++;
                int freq = countOccurrences(lowerContent, term);
                score += Math.min(freq * 0.1, 0.3);
            }
        }
        
        double coverage = queryTerms.length > 0 ? (double) matchCount / queryTerms.length : 0;
        score += coverage * 0.4;
        
        if (lowerContent.contains(query.toLowerCase())) {
            score += 0.3;
        }
        
        return Math.min(score, 1.0);
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) != -1) {
            count++;
            index += term.length();
        }
        return count;
    }

    private Map<Integer, Double> parseLlmScores(String response) {
        Map<Integer, Double> scores = new HashMap<>();
        
        if (response == null || response.isEmpty()) return scores;
        
        try {
            String jsonStr = response;
            int start = response.indexOf("{");
            int end = response.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonStr = response.substring(start, end + 1);
            }
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"index\"\\s*:\\s*(\\d+).*?\"score\"\\s*:\\s*([\\d.]+)");
            java.util.regex.Matcher matcher = pattern.matcher(jsonStr);
            
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1));
                double score = Double.parseDouble(matcher.group(2));
                scores.put(index, score / 10.0);
            }
        } catch (Exception e) {
            log.warn("解析LLM评分失败: {}", e.getMessage());
        }
        
        return scores;
    }

    public List<Map<String, Object>> crossEncoderRerank(String query, List<Map<String, Object>> documents, int topK) {
        return rerank(query, documents, topK);
    }
}
