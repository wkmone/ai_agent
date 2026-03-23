package com.wk.agent.service.multimodal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AudioEncoder implements MultimodalEncoder {

    private static final Logger log = LoggerFactory.getLogger(AudioEncoder.class);

    @Value("${app.multimodal.audio.enabled:true}")
    private boolean enabled;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "audio/mpeg", "audio/mp3", "audio/wav", "audio/wave",
        "audio/ogg", "audio/aac", "audio/flac", "audio/m4a"
    );

    private static final int MAX_AUDIO_SIZE = 50 * 1024 * 1024;

    @Override
    public String getModalityType() {
        return "audio";
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

        if (data.length > MAX_AUDIO_SIZE) {
            log.warn("音频太大，跳过编码: {} bytes", data.length);
            return new float[0];
        }

        try {
            log.debug("编码音频, 大小: {} bytes", data.length);
            
            String audioFeatures = extractAudioFeatures(data);
            
            return generateEmbeddingFromFeatures(audioFeatures);
        } catch (Exception e) {
            log.error("音频编码失败: {}", e.getMessage());
            return new float[0];
        }
    }

    @Override
    public float[] encode(String content) {
        if (content == null || content.isEmpty()) {
            return new float[0];
        }
        
        return generateEmbeddingFromFeatures(content);
    }

    private String extractAudioFeatures(byte[] data) {
        StringBuilder features = new StringBuilder();
        
        String format = detectAudioFormat(data);
        features.append("format:").append(format).append(" ");
        
        features.append("size:").append(data.length).append(" ");
        
        double avgAmplitude = calculateAverageAmplitude(data);
        features.append("avgAmplitude:").append(String.format("%.2f", avgAmplitude)).append(" ");
        
        double energy = calculateEnergy(data);
        features.append("energy:").append(String.format("%.2f", energy)).append(" ");
        
        double zeroCrossingRate = calculateZeroCrossingRate(data);
        features.append("zeroCrossingRate:").append(String.format("%.4f", zeroCrossingRate));
        
        return features.toString();
    }

    private float[] generateEmbeddingFromFeatures(String features) {
        float[] embedding = new float[1536];
        Random random = new Random(features.hashCode());
        
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (random.nextFloat() * 2 - 1) * 0.1f;
        }
        
        return normalizeVector(embedding);
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

    @Override
    public Map<String, Object> extractMetadata(byte[] data) {
        Map<String, Object> metadata = new HashMap<>();
        
        if (data == null || data.length == 0) {
            return metadata;
        }

        metadata.put("size", data.length);
        metadata.put("sizeFormatted", formatSize(data.length));
        
        String format = detectAudioFormat(data);
        metadata.put("format", format);
        
        double duration = estimateDuration(data, format);
        metadata.put("estimatedDuration", duration);
        metadata.put("estimatedDurationFormatted", formatDuration(duration));
        
        metadata.put("avgAmplitude", calculateAverageAmplitude(data));
        metadata.put("energy", calculateEnergy(data));
        metadata.put("zeroCrossingRate", calculateZeroCrossingRate(data));
        
        String quality = assessQuality(data);
        metadata.put("quality", quality);
        
        return metadata;
    }

    @Override
    public int getEmbeddingDimension() {
        return 1536;
    }

    private String detectAudioFormat(byte[] data) {
        if (data.length < 12) return "unknown";
        
        if (data[0] == (byte) 0xFF && (data[1] & 0xE0) == 0xE0) {
            return "mp3";
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            return "wav";
        }
        if (data[0] == 'O' && data[1] == 'g' && data[2] == 'g') {
            return "ogg";
        }
        if (data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
            return "m4a";
        }
        if (data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') {
            return "flac";
        }
        
        return "unknown";
    }

    private double calculateAverageAmplitude(byte[] data) {
        long sum = 0;
        int sampleCount = Math.min(data.length, 10000);
        
        for (int i = 0; i < sampleCount; i++) {
            sum += Math.abs(data[i]);
        }
        
        return (double) sum / sampleCount;
    }

    private double calculateEnergy(byte[] data) {
        double energy = 0;
        int sampleCount = Math.min(data.length, 10000);
        
        for (int i = 0; i < sampleCount; i++) {
            energy += data[i] * data[i];
        }
        
        return energy / sampleCount;
    }

    private double calculateZeroCrossingRate(byte[] data) {
        int crossings = 0;
        int sampleCount = Math.min(data.length, 10000);
        
        for (int i = 1; i < sampleCount; i++) {
            if ((data[i] >= 0 && data[i-1] < 0) || (data[i] < 0 && data[i-1] >= 0)) {
                crossings++;
            }
        }
        
        return (double) crossings / sampleCount;
    }

    private double estimateDuration(byte[] data, String format) {
        int bitrate = getEstimatedBitrate(format);
        if (bitrate == 0) return 0;
        
        return (double) data.length * 8 / bitrate;
    }

    private int getEstimatedBitrate(String format) {
        return switch (format) {
            case "mp3" -> 128000;
            case "wav" -> 1411200;
            case "ogg" -> 112000;
            case "aac", "m4a" -> 128000;
            case "flac" -> 800000;
            default -> 128000;
        };
    }

    private String assessQuality(byte[] data) {
        double energy = calculateEnergy(data);
        double zcr = calculateZeroCrossingRate(data);
        
        if (energy > 5000 && zcr > 0.05 && zcr < 0.5) {
            return "high";
        } else if (energy > 1000 && zcr > 0.01) {
            return "medium";
        } else {
            return "low";
        }
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String formatDuration(double seconds) {
        if (seconds < 60) {
            return String.format("%.1f秒", seconds);
        }
        int minutes = (int) (seconds / 60);
        double remainingSeconds = seconds % 60;
        return String.format("%d分%.0f秒", minutes, remainingSeconds);
    }
}
