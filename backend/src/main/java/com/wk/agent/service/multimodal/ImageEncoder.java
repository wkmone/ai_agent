package com.wk.agent.service.multimodal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Base64;

@Component
public class ImageEncoder implements MultimodalEncoder {

    private static final Logger log = LoggerFactory.getLogger(ImageEncoder.class);

    @Autowired(required = false)
    private ChatClient chatClient;

    @Value("${app.multimodal.image.enabled:true}")
    private boolean enabled;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    private static final int MAX_IMAGE_SIZE = 20 * 1024 * 1024;

    @Override
    public String getModalityType() {
        return "image";
    }

    @Override
    public boolean supports(String contentType) {
        if (contentType == null) return false;
        String normalized = contentType.toLowerCase().split(";")[0].trim();
        return SUPPORTED_TYPES.contains(normalized);
    }

    @Override
    public float[] encode(byte[] data) {
        if (!enabled || data == null || data.length == 0) {
            return new float[0];
        }

        if (data.length > MAX_IMAGE_SIZE) {
            log.warn("图像太大，跳过编码: {} bytes", data.length);
            return new float[0];
        }

        try {
            log.debug("编码图像, 大小: {} bytes", data.length);
            
            String base64Image = Base64.getEncoder().encodeToString(data);
            
            String description = generateImageDescription(base64Image);
            
            return generateEmbeddingFromDescription(description);
        } catch (Exception e) {
            log.error("图像编码失败: {}", e.getMessage());
            return new float[0];
        }
    }

    @Override
    public float[] encode(String content) {
        if (content == null || content.isEmpty()) {
            return new float[0];
        }
        
        return generateEmbeddingFromDescription(content);
    }

    private String generateImageDescription(String base64Image) {
        if (chatClient == null) {
            log.warn("ChatClient 未配置，无法生成图像描述");
            return "image";
        }

        try {
            String response = chatClient.prompt()
                .user("请用简洁的语言描述这张图片的内容，不超过100字。图片Base64数据: " + base64Image.substring(0, Math.min(100, base64Image.length())) + "...")
                .call()
                .content();
            
            return response != null ? response.trim() : "image";
        } catch (Exception e) {
            log.error("生成图像描述失败: {}", e.getMessage());
            return "image";
        }
    }

    private float[] generateEmbeddingFromDescription(String description) {
        return new float[1536];
    }

    @Override
    public Map<String, Object> extractMetadata(byte[] data) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (data == null || data.length == 0) {
            return metadata;
        }

        metadata.put("size", data.length);
        metadata.put("sizeFormatted", formatSize(data.length));
        
        String format = detectImageFormat(data);
        metadata.put("format", format);
        
        int[] dimensions = extractDimensions(data);
        if (dimensions != null) {
            metadata.put("width", dimensions[0]);
            metadata.put("height", dimensions[1]);
            metadata.put("aspectRatio", (double) dimensions[0] / dimensions[1]);
        }
        
        metadata.put("hasTransparency", hasTransparency(data, format));
        
        return metadata;
    }

    @Override
    public int getEmbeddingDimension() {
        return 1536;
    }

    private String detectImageFormat(byte[] data) {
        if (data.length < 8) return "unknown";
        
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            return "jpeg";
        }
        if (data[0] == (byte) 0x89 && new String(data, 1, 3).equals("PNG")) {
            return "png";
        }
        if (data[0] == (byte) 'G' && data[1] == (byte) 'I' && data[2] == (byte) 'F') {
            return "gif";
        }
        if (data[0] == (byte) 'R' && data[1] == (byte) 'I' && data[2] == (byte) 'F' && data[3] == (byte) 'F') {
            return "webp";
        }
        if (data[0] == (byte) 'B' && data[1] == (byte) 'M') {
            return "bmp";
        }
        
        return "unknown";
    }

    private int[] extractDimensions(byte[] data) {
        String format = detectImageFormat(data);
        
        try {
            switch (format) {
                case "png":
                    if (data.length > 24) {
                        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) |
                                    ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
                        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) |
                                     ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
                        return new int[]{width, height};
                    }
                    break;
                case "gif":
                    if (data.length > 10) {
                        int width = ((data[7] & 0xFF) << 8) | (data[6] & 0xFF);
                        int height = ((data[9] & 0xFF) << 8) | (data[8] & 0xFF);
                        return new int[]{width, height};
                    }
                    break;
                case "bmp":
                    if (data.length > 26) {
                        int width = ((data[21] & 0xFF) << 24) | ((data[20] & 0xFF) << 16) |
                                    ((data[19] & 0xFF) << 8) | (data[18] & 0xFF);
                        int height = ((data[25] & 0xFF) << 24) | ((data[24] & 0xFF) << 16) |
                                     ((data[23] & 0xFF) << 8) | (data[22] & 0xFF);
                        return new int[]{width, height};
                    }
                    break;
            }
        } catch (Exception e) {
            log.debug("提取图像尺寸失败: {}", e.getMessage());
        }
        
        return null;
    }

    private boolean hasTransparency(byte[] data, String format) {
        return "png".equals(format) || "gif".equals(format) || "webp".equals(format);
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
