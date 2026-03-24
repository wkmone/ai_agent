package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SemanticChunkingStrategy implements ChunkingStrategy {
    private static final Logger log = LoggerFactory.getLogger(SemanticChunkingStrategy.class);
    
    @Value("${rag.chunking.max-chunk-size:500}")
    private int defaultMaxChunkSize;
    
    @Value("${rag.chunking.min-chunk-size:100}")
    private int defaultMinChunkSize;
    
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+");
    
    @Override
    public List<ChunkResult> chunk(String content, Map<String, Object> options) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        int maxChunkSize = options != null && options.containsKey("maxChunkSize") 
            ? (Integer) options.get("maxChunkSize") 
            : defaultMaxChunkSize;
        
        int minChunkSize = options != null && options.containsKey("minChunkSize") 
            ? (Integer) options.get("minChunkSize") 
            : defaultMinChunkSize;
        
        List<Paragraph> paragraphs = splitByParagraph(content);
        
        List<ChunkResult> chunks = new ArrayList<>();
        List<Paragraph> currentChunk = new ArrayList<>();
        int currentSize = 0;
        int chunkIndex = 0;
        
        for (Paragraph paragraph : paragraphs) {
            int paragraphSize = approximateTokenLength(paragraph.content);
            
            if (currentSize + paragraphSize > maxChunkSize && currentSize >= minChunkSize) {
                chunks.add(createChunkResult(currentChunk, chunkIndex++));
                currentChunk = new ArrayList<>();
                currentSize = 0;
            }
            
            currentChunk.add(paragraph);
            currentSize += paragraphSize;
        }
        
        if (!currentChunk.isEmpty()) {
            chunks.add(createChunkResult(currentChunk, chunkIndex));
        }
        
        log.info("语义分块: 原文token数={}, 分块数={}", approximateTokenLength(content), chunks.size());
        return chunks;
    }
    
    private List<Paragraph> splitByParagraph(String content) {
        List<Paragraph> paragraphs = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder currentParagraph = new StringBuilder();
        int currentStart = 0;
        int charPos = 0;
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (HEADING_PATTERN.matcher(trimmedLine).find()) {
                if (currentParagraph.length() > 0) {
                    paragraphs.add(createParagraph(
                        currentParagraph.toString(),
                        headingStack.isEmpty() ? null : String.join(" > ", headingStack),
                        currentStart,
                        charPos
                    ));
                    currentParagraph = new StringBuilder();
                }
                
                int level = 0;
                while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
                    level++;
                }
                
                String title = trimmedLine.substring(level).trim();
                
                while (headingStack.size() >= level && !headingStack.isEmpty()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(title);
                
                currentStart = charPos + line.length() + 1;
                
            } else if (trimmedLine.isEmpty()) {
                if (currentParagraph.length() > 0) {
                    paragraphs.add(createParagraph(
                        currentParagraph.toString(),
                        headingStack.isEmpty() ? null : String.join(" > ", headingStack),
                        currentStart,
                        charPos
                    ));
                    currentParagraph = new StringBuilder();
                    currentStart = charPos + line.length() + 1;
                }
            } else {
                if (currentParagraph.length() > 0) {
                    currentParagraph.append("\n");
                }
                currentParagraph.append(line);
            }
            
            charPos += line.length() + 1;
        }
        
        if (currentParagraph.length() > 0) {
            paragraphs.add(createParagraph(
                currentParagraph.toString(),
                headingStack.isEmpty() ? null : String.join(" > ", headingStack),
                currentStart,
                charPos
            ));
        }
        
        if (paragraphs.isEmpty()) {
            paragraphs.add(createParagraph(content, null, 0, content.length()));
        }
        
        return paragraphs;
    }
    
    private Paragraph createParagraph(String content, String headingPath, int start, int end) {
        return new Paragraph(content.trim(), headingPath, start, end);
    }
    
    private ChunkResult createChunkResult(List<Paragraph> paragraphs, int chunkIndex) {
        String content = paragraphs.stream()
            .map(p -> p.content)
            .collect(Collectors.joining("\n\n"));
        
        String headingPath = null;
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            if (paragraphs.get(i).headingPath != null) {
                headingPath = paragraphs.get(i).headingPath;
                break;
            }
        }
        
        int startOffset = paragraphs.get(0).start;
        int endOffset = paragraphs.get(paragraphs.size() - 1).end;
        int tokenCount = approximateTokenLength(content);
        
        return new ChunkResult(content, chunkIndex, headingPath, startOffset, endOffset, tokenCount);
    }
    
    private int approximateTokenLength(String text) {
        if (text == null || text.isEmpty()) return 0;
        
        int cjkCount = 0;
        int wordCount = 0;
        
        for (char c : text.toCharArray()) {
            if (isCJK(c)) {
                cjkCount++;
            }
        }
        
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && !word.matches("^[\\u4e00-\\u9fa5]+$")) {
                wordCount++;
            }
        }
        
        return cjkCount + wordCount;
    }
    
    private boolean isCJK(char c) {
        int code = c;
        return (0x4E00 <= code && code <= 0x9FFF) ||
               (0x3400 <= code && code <= 0x4DBF) ||
               (0x20000 <= code && code <= 0x2A6DF);
    }
    
    private static class Paragraph {
        final String content;
        final String headingPath;
        final int start;
        final int end;
        
        Paragraph(String content, String headingPath, int start, int end) {
            this.content = content;
            this.headingPath = headingPath;
            this.start = start;
            this.end = end;
        }
    }
}
