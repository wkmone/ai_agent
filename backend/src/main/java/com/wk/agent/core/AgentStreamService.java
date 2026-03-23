package com.wk.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AgentStreamService {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamService.class);

    private static final long SSE_TIMEOUT = 300000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, List<StreamMessage>> messageBuffer = new ConcurrentHashMap<>();
    private final List<StreamListener> listeners = new ArrayList<>();

    public static class StreamMessage {
        private final String id;
        private final String type;
        private final String content;
        private final long timestamp;
        private final Map<String, Object> metadata;

        public StreamMessage(String type, String content) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.type = type;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
        }

        public StreamMessage withMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    public enum MessageType {
        START,
        THINKING,
        PROGRESS,
        TOOL_CALL,
        TOOL_RESULT,
        CONTENT,
        ERROR,
        COMPLETE
    }

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE 连接完成: sessionId={}", sessionId);
            emitters.remove(sessionId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: sessionId={}", sessionId);
            emitters.remove(sessionId);
        });

        emitter.onError(ex -> {
            log.error("SSE 连接错误: sessionId={}", sessionId, ex);
            emitters.remove(sessionId);
        });

        emitters.put(sessionId, emitter);
        log.info("创建 SSE 连接: sessionId={}", sessionId);

        sendMessage(sessionId, new StreamMessage(MessageType.START.name(), "连接已建立"));

        return emitter;
    }

    public void sendMessage(String sessionId, StreamMessage message) {
        SseEmitter emitter = emitters.get(sessionId);
        
        if (emitter != null) {
            try {
                Map<String, Object> eventData = new LinkedHashMap<>();
                eventData.put("id", message.getId());
                eventData.put("type", message.getType());
                eventData.put("content", message.getContent());
                eventData.put("timestamp", message.getTimestamp());
                if (!message.getMetadata().isEmpty()) {
                    eventData.put("metadata", message.getMetadata());
                }

                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(eventData));

                log.debug("发送 SSE 消息: sessionId={}, type={}", sessionId, message.getType());

            } catch (IOException e) {
                log.error("发送 SSE 消息失败: sessionId={}", sessionId, e);
                emitters.remove(sessionId);
            }
        }

        messageBuffer.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        notifyListeners(sessionId, message);
    }

    public void sendThinking(String sessionId, String thought) {
        sendMessage(sessionId, new StreamMessage(MessageType.THINKING.name(), thought));
    }

    public void sendProgress(String sessionId, double progress, String phase) {
        StreamMessage message = new StreamMessage(MessageType.PROGRESS.name(), phase)
                .withMetadata("progress", progress);
        sendMessage(sessionId, message);
    }

    public void sendToolCall(String sessionId, String toolName, Map<String, Object> params) {
        StreamMessage message = new StreamMessage(MessageType.TOOL_CALL.name(), toolName)
                .withMetadata("params", params);
        sendMessage(sessionId, message);
    }

    public void sendToolResult(String sessionId, String toolName, String result) {
        StreamMessage message = new StreamMessage(MessageType.TOOL_RESULT.name(), result)
                .withMetadata("toolName", toolName);
        sendMessage(sessionId, message);
    }

    public void sendContent(String sessionId, String content) {
        sendMessage(sessionId, new StreamMessage(MessageType.CONTENT.name(), content));
    }

    public void sendError(String sessionId, String error) {
        sendMessage(sessionId, new StreamMessage(MessageType.ERROR.name(), error));
    }

    public void sendComplete(String sessionId, String summary) {
        sendMessage(sessionId, new StreamMessage(MessageType.COMPLETE.name(), summary));
        
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("完成 SSE 连接失败: sessionId={}", sessionId, e);
            }
        }
    }

    public void closeEmitter(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.error("关闭 SSE 连接失败: sessionId={}", sessionId, e);
            }
        }
        messageBuffer.remove(sessionId);
    }

    public List<StreamMessage> getMessageHistory(String sessionId) {
        return new ArrayList<>(messageBuffer.getOrDefault(sessionId, Collections.emptyList()));
    }

    public boolean hasActiveConnection(String sessionId) {
        return emitters.containsKey(sessionId);
    }

    public int getActiveConnectionCount() {
        return emitters.size();
    }

    public Set<String> getActiveSessions() {
        return new HashSet<>(emitters.keySet());
    }

    public void addListener(StreamListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StreamListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String sessionId, StreamMessage message) {
        for (StreamListener listener : listeners) {
            try {
                listener.onMessage(sessionId, message);
            } catch (Exception e) {
                log.error("流消息监听器执行失败", e);
            }
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeConnections", emitters.size());
        stats.put("bufferedSessions", messageBuffer.size());
        stats.put("sseTimeout", SSE_TIMEOUT);

        Map<String, Integer> bufferSizes = new LinkedHashMap<>();
        messageBuffer.forEach((sessionId, messages) -> 
            bufferSizes.put(sessionId, messages.size()));
        stats.put("bufferSizes", bufferSizes);

        return stats;
    }

    public void clearBuffer(String sessionId) {
        messageBuffer.remove(sessionId);
    }

    public void clearAllBuffers() {
        messageBuffer.clear();
    }

    @FunctionalInterface
    public interface StreamListener {
        void onMessage(String sessionId, StreamMessage message);
    }
}
