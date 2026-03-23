package com.wk.agent.controller;

import com.wk.agent.entity.RagKnowledgeBase;
import com.wk.agent.service.RagKnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@Tag(name = "知识库管理", description = "知识库管理接口")
public class RagKnowledgeBaseController {

    @Autowired
    private RagKnowledgeBaseService knowledgeBaseService;

    @GetMapping
    @Operation(summary = "获取所有知识库", description = "获取所有知识库列表")
    public ResponseEntity<List<RagKnowledgeBase>> getAllKnowledgeBases() {
        return ResponseEntity.ok(knowledgeBaseService.getAllKnowledgeBases());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取知识库详情", description = "根据ID获取知识库详情")
    public ResponseEntity<RagKnowledgeBase> getKnowledgeBaseById(
            @Parameter(description = "知识库ID") @PathVariable Long id) {
        RagKnowledgeBase kb = knowledgeBaseService.getKnowledgeBaseById(id);
        if (kb == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(kb);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "获取知识库统计", description = "获取知识库的统计信息")
    public ResponseEntity<Map<String, Object>> getKnowledgeBaseStats(
            @Parameter(description = "知识库ID") @PathVariable Long id) {
        try {
            Map<String, Object> stats = knowledgeBaseService.getKnowledgeBaseStats(id);
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @Operation(summary = "创建知识库", description = "创建新的知识库")
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(
            @Parameter(description = "知识库名称") @RequestParam String name,
            @Parameter(description = "知识库描述") @RequestParam(required = false) String description,
            @Parameter(description = "命名空间") @RequestParam(required = false) String namespace) {
        try {
            RagKnowledgeBase kb = knowledgeBaseService.createKnowledgeBase(name, description, namespace);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "knowledgeBase", kb
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库信息")
    public ResponseEntity<Map<String, Object>> updateKnowledgeBase(
            @Parameter(description = "知识库ID") @PathVariable Long id,
            @Parameter(description = "知识库名称") @RequestParam(required = false) String name,
            @Parameter(description = "知识库描述") @RequestParam(required = false) String description) {
        try {
            knowledgeBaseService.updateKnowledgeBase(id, name, description);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "删除知识库及其所有内容")
    public ResponseEntity<Map<String, Object>> deleteKnowledgeBase(
            @Parameter(description = "知识库ID") @PathVariable Long id) {
        try {
            knowledgeBaseService.deleteKnowledgeBase(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
