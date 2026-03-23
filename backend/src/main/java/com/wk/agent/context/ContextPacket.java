package com.wk.agent.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class ContextPacket {

    private String content;
    private LocalDateTime timestamp;
    private int tokenCount;
    private double relevanceScore;
    private Map<String, Object> metadata;

    public ContextPacket() {
        this.timestamp = LocalDateTime.now();
        this.relevanceScore = 0.5;
        this.metadata = new HashMap<>();
    }

    public ContextPacket(String content) {
        this();
        this.content = content;
        this.tokenCount = estimateTokenCount(content);
    }

    public ContextPacket(String content, String type) {
        this(content);
        this.metadata.put("type", type);
    }

    public ContextPacket(String content, String type, double relevanceScore) {
        this(content, type);
        this.relevanceScore = Math.max(0.0, Math.min(1.0, relevanceScore));
    }

    public static ContextPacket systemInstruction(String content) {
        ContextPacket packet = new ContextPacket(content, "system_instruction", 1.0);
        packet.getMetadata().put("priority", "high");
        return packet;
    }

    public static ContextPacket userQuery(String content) {
        ContextPacket packet = new ContextPacket(content, "user_query", 1.0);
        return packet;
    }

    public static ContextPacket ragResult(String content, double score) {
        ContextPacket packet = new ContextPacket(content, "rag_result", score);
        packet.getMetadata().put("source", "rag");
        return packet;
    }

    public static ContextPacket memory(String content, String memoryType, double relevanceScore) {
        ContextPacket packet = new ContextPacket(content, "memory", relevanceScore);
        packet.getMetadata().put("memory_type", memoryType);
        return packet;
    }

    public static ContextPacket conversationHistory(String content, String role) {
        ContextPacket packet = new ContextPacket(content, "conversation_history", 0.6);
        packet.getMetadata().put("role", role);
        return packet;
    }

    public static ContextPacket note(String content, String noteType, double relevanceScore) {
        ContextPacket packet = new ContextPacket(content, "note", relevanceScore);
        packet.getMetadata().put("note_type", noteType);
        return packet;
    }

    public static ContextPacket custom(String content, String type, double relevanceScore) {
        ContextPacket packet = new ContextPacket(content, type, relevanceScore);
        return packet;
    }

    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int chineseChars = 0;
        int englishWords = 0;
        boolean inWord = false;
        
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chineseChars++;
                inWord = false;
            } else if (Character.isLetter(c)) {
                if (!inWord) {
                    englishWords++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        
        return (int) (chineseChars + englishWords * 1.3);
    }

    public void setMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }

    public String getType() {
        Object type = getMetadata("type");
        return type != null ? type.toString() : "general";
    }

    public boolean isSystemInstruction() {
        return "system_instruction".equals(getType());
    }

    public double getCombinedScore(double relevanceWeight, double recencyScore) {
        double recencyWeight = 1.0 - relevanceWeight;
        return relevanceWeight * this.relevanceScore + recencyWeight * recencyScore;
    }
}
