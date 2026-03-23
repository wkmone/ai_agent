package com.wk.agent.service.multimodal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class TextEncoder implements MultimodalEncoder {

    private static final Logger log = LoggerFactory.getLogger(TextEncoder.class);

    @Autowired
    private EmbeddingModel embeddingModel;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "text/plain", "text/html", "text/markdown", "text/xml",
        "application/json", "application/xml"
    );

    @Override
    public String getModalityType() {
        return "text";
    }

    @Override
    public boolean supports(String contentType) {
        if (contentType == null) return true;
        String normalized = contentType.toLowerCase().split(";")[0].trim();
        return SUPPORTED_TYPES.contains(normalized) || normalized.startsWith("text/");
    }

    @Override
    public float[] encode(byte[] data) {
        if (data == null || data.length == 0) {
            return new float[0];
        }
        String text = new String(data, StandardCharsets.UTF_8);
        return encode(text);
    }

    @Override
    public float[] encode(String content) {
        if (content == null || content.isEmpty()) {
            return new float[0];
        }

        try {
            log.debug("编码文本内容, 长度: {}", content.length());
            
            float[] embedding = embeddingModel.embed(content);
            
            log.debug("文本编码完成, 向量维度: {}", embedding.length);
            return embedding;
        } catch (Exception e) {
            log.error("文本编码失败: {}", e.getMessage());
            return new float[0];
        }
    }

    @Override
    public Map<String, Object> extractMetadata(byte[] data) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (data == null) {
            return metadata;
        }

        String text = new String(data, StandardCharsets.UTF_8);
        
        metadata.put("charCount", text.length());
        metadata.put("byteCount", data.length);
        metadata.put("wordCount", countWords(text));
        metadata.put("lineCount", countLines(text));
        metadata.put("language", detectLanguage(text));
        metadata.put("hasEmoji", containsEmoji(text));
        
        return metadata;
    }

    @Override
    public int getEmbeddingDimension() {
        try {
            float[] sample = embeddingModel.embed("test");
            return sample.length;
        } catch (Exception e) {
            return 1536;
        }
    }

    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        String[] words = text.split("\\s+");
        int count = 0;
        
        for (String word : words) {
            if (!word.isEmpty()) {
                if (word.matches(".*[\\u4e00-\\u9fa5].*")) {
                    count += word.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
                } else {
                    count++;
                }
            }
        }
        
        return count;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\r?\\n").length;
    }

    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) return "unknown";
        
        int chineseCount = 0;
        int englishCount = 0;
        
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                chineseCount++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                englishCount++;
            }
        }
        
        if (chineseCount > englishCount) {
            return "zh";
        } else if (englishCount > 0) {
            return "en";
        }
        
        return "unknown";
    }

    private boolean containsEmoji(String text) {
        if (text == null) return false;
        
        return text.codePoints()
            .anyMatch(cp -> cp > 0x1F000 || (cp >= 0x1F600 && cp <= 0x1F64F));
    }
}
