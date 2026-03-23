package com.wk.agent.core;

import com.wk.agent.entity.neo4j.ConceptNode;
import com.wk.agent.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class KnowledgeVersionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVersionService.class);

    @Value("${agent.knowledge-version.max-versions:10}")
    private int maxVersions;

    @Value("${agent.knowledge-version.auto-update-enabled:true}")
    private boolean autoUpdateEnabled;

    @Value("${agent.knowledge-version.update-threshold:0.1}")
    private double updateThreshold;

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    private final Map<String, List<KnowledgeVersion>> versionHistory = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeChangeLog> changeLogs = new ConcurrentHashMap<>();
    private final List<KnowledgeUpdateListener> listeners = new ArrayList<>();

    public static class KnowledgeVersion {
        private final String versionId;
        private final String conceptName;
        private final String content;
        private final double importance;
        private final double confidence;
        private final LocalDateTime createdAt;
        private final String changeReason;
        private final String changedBy;

        public KnowledgeVersion(String conceptName, String content, double importance, 
                double confidence, String changeReason, String changedBy) {
            this.versionId = "v_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 4);
            this.conceptName = conceptName;
            this.content = content;
            this.importance = importance;
            this.confidence = confidence;
            this.createdAt = LocalDateTime.now();
            this.changeReason = changeReason;
            this.changedBy = changedBy;
        }

        public String getVersionId() { return versionId; }
        public String getConceptName() { return conceptName; }
        public String getContent() { return content; }
        public double getImportance() { return importance; }
        public double getConfidence() { return confidence; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getChangeReason() { return changeReason; }
        public String getChangedBy() { return changedBy; }
    }

    public static class KnowledgeChangeLog {
        private final String conceptName;
        private final List<ChangeRecord> changes;
        private LocalDateTime lastUpdated;

        public KnowledgeChangeLog(String conceptName) {
            this.conceptName = conceptName;
            this.changes = new ArrayList<>();
            this.lastUpdated = LocalDateTime.now();
        }

        public void addChange(ChangeRecord record) {
            this.changes.add(record);
            this.lastUpdated = LocalDateTime.now();
        }

        public String getConceptName() { return conceptName; }
        public List<ChangeRecord> getChanges() { return changes; }
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public int getChangeCount() { return changes.size(); }
    }

    public static class ChangeRecord {
        private final LocalDateTime timestamp;
        private final String changeType;
        private final String oldValue;
        private final String newValue;
        private final String reason;

        public ChangeRecord(String changeType, String oldValue, String newValue, String reason) {
            this.timestamp = LocalDateTime.now();
            this.changeType = changeType;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public String getChangeType() { return changeType; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public String getReason() { return reason; }
    }

    public String createVersion(String conceptName, String content, double importance, 
            double confidence, String reason, String changedBy) {
        
        KnowledgeVersion version = new KnowledgeVersion(conceptName, content, importance, confidence, reason, changedBy);
        
        List<KnowledgeVersion> versions = versionHistory.computeIfAbsent(conceptName, k -> new ArrayList<>());
        versions.add(version);

        if (versions.size() > maxVersions) {
            versions.remove(0);
        }

        log.info("创建知识版本: concept={}, versionId={}", conceptName, version.getVersionId());
        notifyListeners(conceptName, "VERSION_CREATED", version);

        return version.getVersionId();
    }

    public Optional<KnowledgeVersion> getVersion(String conceptName, String versionId) {
        List<KnowledgeVersion> versions = versionHistory.get(conceptName);
        if (versions == null) {
            return Optional.empty();
        }
        
        return versions.stream()
                .filter(v -> v.getVersionId().equals(versionId))
                .findFirst();
    }

    public List<KnowledgeVersion> getVersionHistory(String conceptName) {
        return new ArrayList<>(versionHistory.getOrDefault(conceptName, Collections.emptyList()));
    }

    public Optional<KnowledgeVersion> getLatestVersion(String conceptName) {
        List<KnowledgeVersion> versions = versionHistory.get(conceptName);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(versions.get(versions.size() - 1));
    }

    public boolean rollbackToVersion(String conceptName, String versionId, String reason) {
        Optional<KnowledgeVersion> versionOpt = getVersion(conceptName, versionId);
        if (versionOpt.isEmpty()) {
            log.warn("版本不存在: concept={}, versionId={}", conceptName, versionId);
            return false;
        }

        KnowledgeVersion version = versionOpt.get();

        Optional<ConceptNode> conceptOpt = knowledgeGraphService.findConceptByName(conceptName);
        if (conceptOpt.isEmpty()) {
            log.warn("概念不存在: {}", conceptName);
            return false;
        }

        ConceptNode concept = conceptOpt.get();
        
        recordChange(conceptName, "DEFINITION", concept.getDefinition(), version.getContent(), "回滚: " + reason);
        recordChange(conceptName, "IMPORTANCE", 
                String.valueOf(concept.getImportance()), 
                String.valueOf(version.getImportance()), 
                "回滚: " + reason);

        concept.setDefinition(version.getContent());
        concept.setImportance(version.getImportance());
        concept.setConfidence(version.getConfidence());
        concept.setUpdatedAt(LocalDateTime.now());

        knowledgeGraphService.createOrUpdateConcept(
                concept.getName(),
                concept.getCategory(),
                concept.getDefinition(),
                concept.getSessionId()
        );

        log.info("知识回滚成功: concept={}, versionId={}", conceptName, versionId);
        notifyListeners(conceptName, "VERSION_ROLLBACK", version);

        return true;
    }

    public boolean updateKnowledge(String conceptName, String newContent, 
            double newImportance, String reason, String updatedBy) {
        
        Optional<ConceptNode> conceptOpt = knowledgeGraphService.findConceptByName(conceptName);
        if (conceptOpt.isEmpty()) {
            log.warn("概念不存在: {}", conceptName);
            return false;
        }

        ConceptNode concept = conceptOpt.get();
        
        String oldContent = concept.getDefinition();
        double oldImportance = concept.getImportance() != null ? concept.getImportance() : 0.5;
        double oldConfidence = concept.getConfidence() != null ? concept.getConfidence() : 0.5;

        double contentChange = calculateContentChange(oldContent, newContent);
        double importanceChange = Math.abs(oldImportance - newImportance);

        if (contentChange < updateThreshold && importanceChange < updateThreshold) {
            log.debug("知识变化不足，跳过更新: concept={}", conceptName);
            return false;
        }

        createVersion(conceptName, oldContent, oldImportance, oldConfidence, "更新前备份", "system");

        if (!oldContent.equals(newContent)) {
            recordChange(conceptName, "CONTENT", oldContent, newContent, reason);
        }
        if (importanceChange >= updateThreshold) {
            recordChange(conceptName, "IMPORTANCE", 
                    String.valueOf(oldImportance), 
                    String.valueOf(newImportance), 
                    reason);
        }

        concept.setDefinition(newContent);
        concept.setImportance(newImportance);
        concept.setUpdatedAt(LocalDateTime.now());

        knowledgeGraphService.createOrUpdateConcept(
                concept.getName(),
                concept.getCategory(),
                concept.getDefinition(),
                concept.getSessionId()
        );

        log.info("知识更新成功: concept={}, reason={}", conceptName, reason);
        notifyListeners(conceptName, "KNOWLEDGE_UPDATED", Map.of(
                "oldContent", oldContent != null ? oldContent : "",
                "newContent", newContent,
                "reason", reason
        ));

        return true;
    }

    private double calculateContentChange(String oldContent, String newContent) {
        if (oldContent == null && newContent == null) return 0.0;
        if (oldContent == null || newContent == null) return 1.0;
        if (oldContent.equals(newContent)) return 0.0;

        Set<String> oldWords = Arrays.stream(oldContent.toLowerCase().split("\\s+"))
                .collect(Collectors.toSet());
        Set<String> newWords = Arrays.stream(newContent.toLowerCase().split("\\s+"))
                .collect(Collectors.toSet());

        Set<String> union = new HashSet<>(oldWords);
        union.addAll(newWords);

        Set<String> intersection = new HashSet<>(oldWords);
        intersection.retainAll(newWords);

        return 1.0 - (double) intersection.size() / union.size();
    }

    private void recordChange(String conceptName, String changeType, 
            String oldValue, String newValue, String reason) {
        
        KnowledgeChangeLog changeLog = changeLogs.computeIfAbsent(conceptName, KnowledgeChangeLog::new);
        changeLog.addChange(new ChangeRecord(changeType, oldValue, newValue, reason));
    }

    public KnowledgeChangeLog getChangeLog(String conceptName) {
        return changeLogs.get(conceptName);
    }

    public Map<String, KnowledgeChangeLog> getAllChangeLogs() {
        return new HashMap<>(changeLogs);
    }

    @Scheduled(cron = "${agent.knowledge-version.cleanup-schedule:0 0 5 * * ?}")
    public void cleanupOldVersions() {
        int cleaned = 0;
        
        for (List<KnowledgeVersion> versions : versionHistory.values()) {
            while (versions.size() > maxVersions) {
                versions.remove(0);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("清理旧版本: {} 个", cleaned);
        }
    }

    public void addListener(KnowledgeUpdateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(KnowledgeUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String conceptName, String event, Object data) {
        for (KnowledgeUpdateListener listener : listeners) {
            try {
                listener.onKnowledgeUpdate(conceptName, event, data);
            } catch (Exception e) {
                log.error("知识更新监听器执行失败", e);
            }
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        int totalVersions = versionHistory.values().stream()
                .mapToInt(List::size)
                .sum();
        
        int totalChanges = changeLogs.values().stream()
                .mapToInt(KnowledgeChangeLog::getChangeCount)
                .sum();

        stats.put("autoUpdateEnabled", autoUpdateEnabled);
        stats.put("maxVersions", maxVersions);
        stats.put("updateThreshold", updateThreshold);
        stats.put("trackedConcepts", versionHistory.size());
        stats.put("totalVersions", totalVersions);
        stats.put("totalChanges", totalChanges);

        return stats;
    }

    public void clearVersionHistory(String conceptName) {
        versionHistory.remove(conceptName);
        changeLogs.remove(conceptName);
        log.info("清除知识版本历史: {}", conceptName);
    }

    public void clearAllHistory() {
        versionHistory.clear();
        changeLogs.clear();
        log.info("清除所有知识版本历史");
    }

    @FunctionalInterface
    public interface KnowledgeUpdateListener {
        void onKnowledgeUpdate(String conceptName, String event, Object data);
    }
}
