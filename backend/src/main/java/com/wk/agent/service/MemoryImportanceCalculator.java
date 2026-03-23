package com.wk.agent.service;

import com.wk.agent.entity.SessionMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MemoryImportanceCalculator {

    private static final Logger log = LoggerFactory.getLogger(MemoryImportanceCalculator.class);

    @Autowired(required = false)
    private ChatClient.Builder chatClientBuilder;

    public double calculateCombinedImportance(SessionMemory memory, LocalDateTime currentTime, String query) {
        double recency = calculateRecency(memory.getAccessedAt() != null ? memory.getAccessedAt() : memory.getCreatedAt(), currentTime);
        double importance = memory.getImportance() != null ? memory.getImportance() : 0.5;
        double relevance = calculateRelevance(memory.getContent(), query);
        
        return 0.4 * recency + 0.3 * importance + 0.3 * relevance;
    }

    public double calculateImportanceScore(String content) {
        try {
            if (chatClientBuilder != null) {
                ChatClient chatClient = chatClientBuilder.build();
                String response = chatClient.prompt()
                        .user("请评估以下对话内容的重要性，从1-10分打分，其中：\n" +
                                "1-2分：日常闲聊、无意义内容\n" +
                                "3-4分：普通信息、轻度参考价值\n" +
                                "5-6分：有用信息、中等参考价值\n" +
                                "7-8分：重要信息、高参考价值\n" +
                                "9-10分：关键信息、核心知识、用户偏好/决定/问题\n\n" +
                                "只返回数字，不要其他解释。\n\n" +
                                "对话内容：\n" + content)
                        .call()
                        .content();
                
                String cleanedResponse = response.trim().replaceAll("[^0-9]", "");
                if (!cleanedResponse.isEmpty()) {
                    int score = Integer.parseInt(cleanedResponse);
                    return Math.max(0.1, Math.min(1.0, score / 10.0));
                }
            }
        } catch (Exception e) {
            log.warn("LLM重要度评分失败，使用降级方法: {}", e.getMessage());
        }
        
        return calculateImportanceFallback(content);
    }

    private double calculateRecency(LocalDateTime accessedAt, LocalDateTime currentTime) {
        if (accessedAt == null) {
            return 0.0;
        }
        
        long hoursSinceAccess = Duration.between(accessedAt, currentTime).toHours();
        double decayFactor = 0.995;
        
        return Math.pow(decayFactor, hoursSinceAccess);
    }

    private double calculateRelevance(String content, String query) {
        if (content == null || query == null || content.isEmpty() || query.isEmpty()) {
            return 0.0;
        }
        
        String[] contentWords = content.toLowerCase().split("\\s+");
        String[] queryWords = query.toLowerCase().split("\\s+");
        
        int matchCount = 0;
        for (String queryWord : queryWords) {
            for (String contentWord : contentWords) {
                if (contentWord.contains(queryWord) || queryWord.contains(contentWord)) {
                    matchCount++;
                    break;
                }
            }
        }
        
        return Math.min(1.0, matchCount / (double) Math.max(queryWords.length, 1));
    }

    private double calculateImportanceFallback(String content) {
        double score = 0.5;
        if (content == null || content.isEmpty()) {
            return score;
        }

        String lowerContent = content.toLowerCase();
        String[] importantKeywords = {"重要", "关键", "必须", "紧急", "记住", "不要", "偏好", "喜欢",
                "important", "critical", "must", "urgent", "remember", "user", "preference",
                "决定", "选择", "我的", "我想", "我需要", "问题", "错误", "解决"};

        for (String keyword : importantKeywords) {
            if (lowerContent.contains(keyword.toLowerCase())) {
                score += 0.08;
            }
        }

        if (content.length() > 100) {
            score += 0.05;
        }
        if (content.length() > 200) {
            score += 0.05;
        }

        return Math.min(score, 1.0);
    }

    public String extractKeywords(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        try {
            if (chatClientBuilder != null) {
                ChatClient chatClient = chatClientBuilder.build();
                String response = chatClient.prompt()
                        .user("请从以下内容中提取 3-8 个关键词，关键词应该能够概括内容的核心主题。" +
                                "只返回关键词，用逗号分隔，不要其他解释。\n\n" +
                                "内容：\n" + content)
                        .call()
                        .content();
                
                String cleanedResponse = response.trim();
                if (!cleanedResponse.isEmpty()) {
                    log.debug("LLM 提取关键词: {}", cleanedResponse);
                    return cleanedResponse;
                }
            }
        } catch (Exception e) {
            log.warn("LLM 关键词提取失败，使用降级方法: {}", e.getMessage());
        }
        
        return extractKeywordsFallback(content);
    }

    private String extractKeywordsFallback(String content) {
        StringBuilder keywords = new StringBuilder();
        String[] importantWords = {"用户", "项目", "配置", "文件", "命令", "错误", "问题", "解决",
                "user", "project", "config", "file", "command", "error", "issue", "solution",
                "重要", "关键", "必须", "记住", "偏好", "选择", "决定", "需求", "preference",
                "choice", "decision", "requirement", "need"};

        for (String word : importantWords) {
            if (content.contains(word)) {
                if (keywords.length() > 0) {
                    keywords.append(",");
                }
                keywords.append(word);
            }
        }

        return keywords.toString();
    }

    public String extractKnowledge(String content) {
        if (content == null || content.length() <= 200) {
            return content;
        }

        String[] sentences = content.split("[。！？.!?]");
        StringBuilder knowledge = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence.length() > 20 && sentence.length() < 200) {
                if (knowledge.length() + sentence.length() > 300) {
                    break;
                }
                knowledge.append(sentence.trim()).append("。");
            }
        }

        return knowledge.length() > 0 ? knowledge.toString() : content.substring(0, Math.min(200, content.length()));
    }
}
