package com.wk.agent.service.multimodal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MultimodalEncoderService {

    private static final Logger log = LoggerFactory.getLogger(MultimodalEncoderService.class);

    private final List<MultimodalEncoder> encoders;

    private final Map<String, MultimodalEncoder> encoderMap;

    @Autowired
    public MultimodalEncoderService(List<MultimodalEncoder> encoderList) {
        this.encoders = encoderList;
        this.encoderMap = new HashMap<>();
        
        for (MultimodalEncoder encoder : encoderList) {
            encoderMap.put(encoder.getModalityType(), encoder);
            log.info("注册多模态编码器: {} -> {}", encoder.getModalityType(), encoder.getClass().getSimpleName());
        }
    }

    public float[] encode(byte[] data, String contentType) {
        MultimodalEncoder encoder = findEncoder(contentType);
        
        if (encoder == null) {
            log.warn("未找到支持 {} 的编码器", contentType);
            return new float[0];
        }
        
        return encoder.encode(data);
    }

    public float[] encodeText(String text) {
        MultimodalEncoder textEncoder = encoderMap.get("text");
        if (textEncoder == null) {
            log.warn("文本编码器未注册");
            return new float[0];
        }
        
        return textEncoder.encode(text);
    }

    public float[] encodeImage(byte[] imageData) {
        MultimodalEncoder imageEncoder = encoderMap.get("image");
        if (imageEncoder == null) {
            log.warn("图像编码器未注册");
            return new float[0];
        }
        
        return imageEncoder.encode(imageData);
    }

    public float[] encodeAudio(byte[] audioData) {
        MultimodalEncoder audioEncoder = encoderMap.get("audio");
        if (audioEncoder == null) {
            log.warn("音频编码器未注册");
            return new float[0];
        }
        
        return audioEncoder.encode(audioData);
    }

    public Map<String, Object> extractMetadata(byte[] data, String contentType) {
        MultimodalEncoder encoder = findEncoder(contentType);
        
        if (encoder == null) {
            return new HashMap<>();
        }
        
        return encoder.extractMetadata(data);
    }

    public String detectModality(String contentType) {
        if (contentType == null) {
            return "text";
        }
        
        for (MultimodalEncoder encoder : encoders) {
            if (encoder.supports(contentType)) {
                return encoder.getModalityType();
            }
        }
        
        return "unknown";
    }

    public List<String> getSupportedModalities() {
        return new ArrayList<>(encoderMap.keySet());
    }

    public List<String> getSupportedContentTypes() {
        List<String> types = new ArrayList<>();
        
        for (MultimodalEncoder encoder : encoders) {
            types.add(encoder.getModalityType() + ": " + getSupportedContentTypesForEncoder(encoder));
        }
        
        return types;
    }

    private MultimodalEncoder findEncoder(String contentType) {
        if (contentType == null) {
            return encoderMap.get("text");
        }
        
        for (MultimodalEncoder encoder : encoders) {
            if (encoder.supports(contentType)) {
                return encoder;
            }
        }
        
        return null;
    }

    private String getSupportedContentTypesForEncoder(MultimodalEncoder encoder) {
        return switch (encoder.getModalityType()) {
            case "text" -> "text/plain, text/html, text/markdown, application/json";
            case "image" -> "image/jpeg, image/png, image/gif, image/webp";
            case "audio" -> "audio/mpeg, audio/wav, audio/ogg, audio/aac";
            default -> "unknown";
        };
    }

    public float[] fuseEmbeddings(Map<String, float[]> modalityEmbeddings) {
        if (modalityEmbeddings == null || modalityEmbeddings.isEmpty()) {
            return new float[0];
        }

        int dimension = getCommonDimension(modalityEmbeddings);
        if (dimension == 0) {
            return new float[0];
        }

        float[] fused = new float[dimension];
        int count = 0;

        for (Map.Entry<String, float[]> entry : modalityEmbeddings.entrySet()) {
            float[] embedding = entry.getValue();
            if (embedding != null && embedding.length == dimension) {
                for (int i = 0; i < dimension; i++) {
                    fused[i] += embedding[i];
                }
                count++;
            }
        }

        if (count > 0) {
            for (int i = 0; i < dimension; i++) {
                fused[i] /= count;
            }
        }

        return normalizeVector(fused);
    }

    public float[] weightedFuseEmbeddings(Map<String, float[]> modalityEmbeddings, 
                                          Map<String, Double> weights) {
        if (modalityEmbeddings == null || modalityEmbeddings.isEmpty()) {
            return new float[0];
        }

        int dimension = getCommonDimension(modalityEmbeddings);
        if (dimension == 0) {
            return new float[0];
        }

        float[] fused = new float[dimension];
        double totalWeight = 0;

        for (Map.Entry<String, float[]> entry : modalityEmbeddings.entrySet()) {
            String modality = entry.getKey();
            float[] embedding = entry.getValue();
            
            if (embedding != null && embedding.length == dimension) {
                double weight = weights != null ? weights.getOrDefault(modality, 1.0) : 1.0;
                
                for (int i = 0; i < dimension; i++) {
                    fused[i] += embedding[i] * weight;
                }
                totalWeight += weight;
            }
        }

        if (totalWeight > 0) {
            for (int i = 0; i < dimension; i++) {
                fused[i] /= totalWeight;
            }
        }

        return normalizeVector(fused);
    }

    private int getCommonDimension(Map<String, float[]> embeddings) {
        for (float[] embedding : embeddings.values()) {
            if (embedding != null && embedding.length > 0) {
                return embedding.length;
            }
        }
        return 0;
    }

    private float[] normalizeVector(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    public double calculateSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null || 
            embedding1.length != embedding2.length) {
            return 0.0;
        }

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public Map<String, Object> analyzeContent(byte[] data, String contentType) {
        Map<String, Object> analysis = new HashMap<>();
        
        String modality = detectModality(contentType);
        analysis.put("modality", modality);
        analysis.put("contentType", contentType);
        
        Map<String, Object> metadata = extractMetadata(data, contentType);
        analysis.put("metadata", metadata);
        
        float[] embedding = encode(data, contentType);
        analysis.put("embeddingDimension", embedding.length);
        analysis.put("hasEmbedding", embedding.length > 0);
        
        return analysis;
    }
}
