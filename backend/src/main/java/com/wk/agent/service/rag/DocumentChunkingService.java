package com.wk.agent.service.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);

    public List<ChunkResult> chunkText(String text, int chunkTokens, int overlapTokens) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<ParagraphInfo> paragraphs = splitParagraphsWithHeadings(text);
        
        List<ChunkResult> chunks = chunkParagraphs(paragraphs, chunkTokens, overlapTokens);
        
        return chunks;
    }

    private List<ParagraphInfo> splitParagraphsWithHeadings(String text) {
        List<ParagraphInfo> paragraphs = new ArrayList<>();
        String[] lines = text.split("\n");
        List<String> headingStack = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        int charPos = 0;

        for (String line : lines) {
            String trimmedLine = line.trim();
            
            if (trimmedLine.startsWith("#")) {
                flushBuffer(buffer, paragraphs, headingStack, charPos);
                buffer.clear();
                
                int level = 0;
                while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
                    level++;
                }
                
                String title = trimmedLine.substring(level).trim();
                
                while (headingStack.size() >= level && !headingStack.isEmpty()) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(title);
                
                charPos += line.length() + 1;
            } else if (trimmedLine.isEmpty()) {
                flushBuffer(buffer, paragraphs, headingStack, charPos);
                buffer.clear();
                charPos += line.length() + 1;
            } else {
                buffer.add(line);
                charPos += line.length() + 1;
            }
        }
        
        flushBuffer(buffer, paragraphs, headingStack, charPos);

        if (paragraphs.isEmpty()) {
            paragraphs.add(new ParagraphInfo(text, null, 0, text.length()));
        }

        return paragraphs;
    }

    private void flushBuffer(List<String> buffer, List<ParagraphInfo> paragraphs, 
                             List<String> headingStack, int endPos) {
        if (buffer.isEmpty()) return;
        
        String content = String.join("\n", buffer).trim();
        if (content.isEmpty()) return;
        
        String headingPath = headingStack.isEmpty() ? null : String.join(" > ", headingStack);
        int start = endPos - content.length();
        
        paragraphs.add(new ParagraphInfo(content, headingPath, Math.max(0, start), endPos));
    }

    private List<ChunkResult> chunkParagraphs(List<ParagraphInfo> paragraphs, 
                                               int chunkTokens, int overlapTokens) {
        List<ChunkResult> chunks = new ArrayList<>();
        List<ParagraphInfo> current = new ArrayList<>();
        int currentTokens = 0;
        int chunkIndex = 0;

        for (ParagraphInfo p : paragraphs) {
            int pTokens = approximateTokenLength(p.content);
            
            if (currentTokens + pTokens <= chunkTokens || current.isEmpty()) {
                current.add(p);
                currentTokens += pTokens;
            } else {
                chunks.add(createChunk(current, chunkIndex++));
                
                if (overlapTokens > 0 && !current.isEmpty()) {
                    List<ParagraphInfo> overlap = new ArrayList<>();
                    int overlapTokenCount = 0;
                    
                    for (int i = current.size() - 1; i >= 0; i--) {
                        ParagraphInfo op = current.get(i);
                        int opTokens = approximateTokenLength(op.content);
                        if (overlapTokenCount + opTokens > overlapTokens) break;
                        overlap.add(0, op);
                        overlapTokenCount += opTokens;
                    }
                    
                    current = overlap;
                    currentTokens = overlapTokenCount;
                } else {
                    current = new ArrayList<>();
                    currentTokens = 0;
                }
                
                current.add(p);
                currentTokens += pTokens;
            }
        }

        if (!current.isEmpty()) {
            chunks.add(createChunk(current, chunkIndex));
        }

        return chunks;
    }

    private ChunkResult createChunk(List<ParagraphInfo> paragraphs, int chunkIndex) {
        String content = String.join("\n\n", 
            paragraphs.stream().map(p -> p.content).toList());
        
        int start = paragraphs.get(0).start;
        int end = paragraphs.get(paragraphs.size() - 1).end;
        String headingPath = null;
        
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            if (paragraphs.get(i).headingPath != null) {
                headingPath = paragraphs.get(i).headingPath;
                break;
            }
        }
        
        return new ChunkResult(content, chunkIndex, headingPath, start, end, 
                               approximateTokenLength(content));
    }

    public int approximateTokenLength(String text) {
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

    public static class ParagraphInfo {
        public final String content;
        public final String headingPath;
        public final int start;
        public final int end;

        public ParagraphInfo(String content, String headingPath, int start, int end) {
            this.content = content;
            this.headingPath = headingPath;
            this.start = start;
            this.end = end;
        }
    }

    public static class ChunkResult {
        public final String content;
        public final int chunkIndex;
        public final String headingPath;
        public final int startOffset;
        public final int endOffset;
        public final int tokenCount;

        public ChunkResult(String content, int chunkIndex, String headingPath, 
                          int startOffset, int endOffset, int tokenCount) {
            this.content = content;
            this.chunkIndex = chunkIndex;
            this.headingPath = headingPath;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tokenCount = tokenCount;
        }
    }
}
