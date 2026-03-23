package com.wk.agent.controller;

import com.wk.agent.entity.SessionMemory;
import com.wk.agent.service.MultiLayerMemoryManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memory")
@Tag(name = "Memory", description = "多层记忆管理接口")
public class MemoryController {

    @Autowired
    private MultiLayerMemoryManager multiLayerMemoryManager;

    @GetMapping("/session")
    @Operation(summary = "获取所有会话记忆", description = "获取指定会话的所有层级记忆")
    public ResponseEntity<Map<String, Object>> getAllMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        List<SessionMemory> working = multiLayerMemoryManager.getWorkingMemories(sessionId);
        List<SessionMemory> episodic = multiLayerMemoryManager.getEpisodicMemories(sessionId);
        List<SessionMemory> semantic = multiLayerMemoryManager.getSemanticMemories(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("working", working);
        result.put("episodic", episodic);
        result.put("semantic", semantic);
        result.put("totalWorking", working.size());
        result.put("totalEpisodic", episodic.size());
        result.put("totalSemantic", semantic.size());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/session/working")
    @Operation(summary = "获取工作记忆", description = "获取指定会话的工作记忆 (Layer 2)")
    public ResponseEntity<List<SessionMemory>> getWorkingMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        List<SessionMemory> memories = multiLayerMemoryManager.getWorkingMemories(sessionId);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/session/episodic")
    @Operation(summary = "获取情景记忆", description = "获取指定会话的情景记忆 (Layer 3)")
    public ResponseEntity<List<SessionMemory>> getEpisodicMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        List<SessionMemory> memories = multiLayerMemoryManager.getEpisodicMemories(sessionId);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/session/semantic")
    @Operation(summary = "获取语义记忆", description = "获取指定会话的语义记忆 (Layer 4)")
    public ResponseEntity<List<SessionMemory>> getSemanticMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        List<SessionMemory> memories = multiLayerMemoryManager.getSemanticMemories(sessionId);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/session/important")
    @Operation(summary = "获取重要记忆", description = "获取所有层级的重要记忆")
    public ResponseEntity<List<SessionMemory>> getImportantMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId,
            @Parameter(description = "返回数量限制") @RequestParam(required = false, defaultValue = "10") int limit) {
        List<SessionMemory> all = multiLayerMemoryManager.getRecentMemories(sessionId, limit * 3);
        List<SessionMemory> important = all.stream()
                .filter(m -> m.getImportance() != null && m.getImportance() >= 0.6)
                .limit(limit)
                .toList();
        return ResponseEntity.ok(important);
    }

    @GetMapping("/session/search")
    @Operation(summary = "搜索记忆", description = "根据关键词搜索记忆")
    public ResponseEntity<List<SessionMemory>> searchMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId,
            @Parameter(description = "搜索关键词") @RequestParam String query) {
        List<SessionMemory> memories = multiLayerMemoryManager.searchMemories(sessionId, query);
        return ResponseEntity.ok(memories);
    }

    @GetMapping("/session/recent")
    @Operation(summary = "获取最近记忆", description = "获取最近的记忆")
    public ResponseEntity<List<SessionMemory>> getRecentMemories(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId,
            @Parameter(description = "返回数量限制") @RequestParam(required = false, defaultValue = "10") int limit) {
        List<SessionMemory> memories = multiLayerMemoryManager.getRecentMemories(sessionId, limit);
        return ResponseEntity.ok(memories);
    }

    @DeleteMapping("/session/{id}")
    @Operation(summary = "删除记忆", description = "删除指定ID的记忆")
    public ResponseEntity<Map<String, Object>> deleteMemory(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    @DeleteMapping("/session/working/clear")
    @Operation(summary = "清空工作记忆", description = "清空指定会话的工作记忆")
    public ResponseEntity<Map<String, Object>> clearWorkingMemory(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        multiLayerMemoryManager.clearWorkingMemory(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "message", "工作记忆已清空"));
    }

    @DeleteMapping("/session/episodic/clear")
    @Operation(summary = "清空情景记忆", description = "清空指定会话的情景记忆")
    public ResponseEntity<Map<String, Object>> clearEpisodicMemory(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        multiLayerMemoryManager.clearEpisodicMemory(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "message", "情景记忆已清空"));
    }

    @DeleteMapping("/session/semantic/clear")
    @Operation(summary = "清空语义记忆", description = "清空指定会话的语义记忆")
    public ResponseEntity<Map<String, Object>> clearSemanticMemory(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        multiLayerMemoryManager.clearSemanticMemory(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "message", "语义记忆已清空"));
    }

    @DeleteMapping("/session/clear")
    @Operation(summary = "清空所有记忆", description = "清空指定会话的所有层级记忆")
    public ResponseEntity<Map<String, Object>> clearAllMemory(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        multiLayerMemoryManager.clearAllMemory(sessionId);
        return ResponseEntity.ok(Map.of("success", true, "message", "所有记忆已清空"));
    }

    @GetMapping("/session/stats")
    @Operation(summary = "获取记忆统计", description = "获取各层级记忆的统计信息")
    public ResponseEntity<Map<String, Object>> getMemoryStats(
            @Parameter(description = "会话ID") @RequestParam(required = false, defaultValue = "default") String sessionId) {
        int workingCount = multiLayerMemoryManager.getWorkingMemoryCount(sessionId);
        int episodicCount = multiLayerMemoryManager.getEpisodicMemoryCount(sessionId);
        int semanticCount = multiLayerMemoryManager.getSemanticMemoryCount(sessionId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("sessionId", sessionId);
        stats.put("workingCount", workingCount);
        stats.put("episodicCount", episodicCount);
        stats.put("semanticCount", semanticCount);
        stats.put("totalCount", workingCount + episodicCount + semanticCount);

        long importantCount = 0;
        List<SessionMemory> recent = multiLayerMemoryManager.getRecentMemories(sessionId, 100);
        for (SessionMemory m : recent) {
            if (m.getImportance() != null && m.getImportance() >= 0.7) {
                importantCount++;
            }
        }
        stats.put("importantCount", importantCount);

        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("working", workingCount);
        distribution.put("episodic", episodicCount);
        distribution.put("semantic", semanticCount);
        stats.put("distribution", distribution);

        return ResponseEntity.ok(stats);
    }
}