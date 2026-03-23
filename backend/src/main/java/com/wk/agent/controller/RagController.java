package com.wk.agent.controller;

import com.wk.agent.service.RagService;
import com.wk.agent.service.rag.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG", description = "检索增强生成接口")
public class RagController {

    @Autowired
    private RagService ragService;

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/text")
    @Operation(summary = "添加文本", description = "将文本内容添加到知识库")
    public ResponseEntity<Map<String, Object>> addText(
            @Parameter(description = "文本内容") @RequestBody String text,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "文档ID") @RequestParam(required = false) String documentId,
            @Parameter(description = "分块大小") @RequestParam(required = false, defaultValue = "500") int chunkSize,
            @Parameter(description = "重叠大小") @RequestParam(required = false, defaultValue = "50") int overlapSize) {

        Map<String, Object> result = ragService.addText(text, ragNamespace, documentId, chunkSize, overlapSize);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/document")
    @Operation(summary = "上传文档", description = "上传文档文件到知识库")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @Parameter(description = "文档文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "分块大小") @RequestParam(required = false, defaultValue = "500") int chunkSize,
            @Parameter(description = "重叠大小") @RequestParam(required = false, defaultValue = "50") int overlapSize) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "文件为空"));
        }

        String originalFilename = file.getOriginalFilename();
        if (!fileStorageService.isValidFileType(originalFilename)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "error", "不支持的文件类型。支持的格式: txt, md, html, pdf, doc, docx, ppt, pptx, json, csv"
            ));
        }

        Map<String, Object> result = ragService.addMultipartFile(file, ragNamespace, chunkSize, overlapSize);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            result.put("fileName", originalFilename);
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/document/path")
    @Operation(summary = "添加文档(路径)", description = "通过服务器文件路径添加文档到知识库")
    public ResponseEntity<Map<String, Object>> addDocumentByPath(
            @Parameter(description = "文件路径") @RequestParam String filePath,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "分块大小") @RequestParam(required = false, defaultValue = "500") int chunkSize,
            @Parameter(description = "重叠大小") @RequestParam(required = false, defaultValue = "50") int overlapSize) {

        Map<String, Object> result = ragService.addDocument(filePath, ragNamespace, chunkSize, overlapSize);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @Operation(summary = "检索", description = "从知识库中检索相关内容")
    public ResponseEntity<List<Map<String, Object>>> search(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "返回数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "启用重排序") @RequestParam(required = false, defaultValue = "false") boolean rerank) {

        List<Map<String, Object>> results = ragService.search(query, ragNamespace, topK, threshold, rerank);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/rerank")
    @Operation(summary = "重排序检索", description = "检索并重排序结果")
    public ResponseEntity<List<Map<String, Object>>> searchWithRerank(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "返回数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "重排序类型(rule/llm)") @RequestParam(required = false, defaultValue = "rule") String rerankType) {

        List<Map<String, Object>> results = ragService.searchWithRerank(query, ragNamespace, topK, threshold, rerankType);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/ask")
    @Operation(summary = "问答", description = "基于知识库回答问题")
    public ResponseEntity<Map<String, Object>> ask(
            @Parameter(description = "问题") @RequestParam String question,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "检索数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "启用重排序") @RequestParam(required = false, defaultValue = "false") boolean rerank) {

        String answer = ragService.ask(question, ragNamespace, topK, threshold, rerank);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", answer);
        result.put("namespace", ragNamespace);
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/advanced-search")
    @Operation(summary = "高级检索", description = "支持MQE、HyDE和重排序的高级检索")
    public ResponseEntity<List<Map<String, Object>>> advancedSearch(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "返回数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "启用MQE") @RequestParam(required = false, defaultValue = "false") boolean enableMqe,
            @Parameter(description = "MQE扩展数量") @RequestParam(required = false, defaultValue = "2") int mqeExpansions,
            @Parameter(description = "启用HyDE") @RequestParam(required = false, defaultValue = "false") boolean enableHyde,
            @Parameter(description = "启用重排序") @RequestParam(required = false, defaultValue = "false") boolean enableRerank) {

        List<Map<String, Object>> results = ragService.advancedSearch(
                query, ragNamespace, topK, threshold, enableMqe, mqeExpansions, enableHyde, enableRerank);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/stats")
    @Operation(summary = "统计信息", description = "获取知识库统计信息")
    public ResponseEntity<Map<String, Object>> getStats(
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace) {

        Map<String, Object> stats = ragService.getStats(ragNamespace);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/documents")
    @Operation(summary = "文档列表", description = "获取知识库中的文档列表")
    public ResponseEntity<List<Map<String, Object>>> getDocuments(
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace) {

        List<Map<String, Object>> documents = ragService.getDocuments(ragNamespace);
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/document/{documentId}")
    @Operation(summary = "删除文档", description = "从知识库中删除文档（包括向量存储）")
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "文档ID") @PathVariable String documentId) {

        boolean success = ragService.deleteDocument(documentId);
        return ResponseEntity.ok(Map.of("success", success));
    }

    @PostMapping("/expand-query")
    @Operation(summary = "查询扩展", description = "使用MQE扩展查询")
    public ResponseEntity<List<String>> expandQuery(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "扩展数量") @RequestParam(required = false, defaultValue = "3") int expansions) {

        List<String> expandedQueries = ragService.expandQuery(query, expansions);
        return ResponseEntity.ok(expandedQueries);
    }

    @PostMapping("/hyde")
    @Operation(summary = "生成假设文档", description = "使用HyDE生成假设性答案文档")
    public ResponseEntity<String> generateHypotheticalDocument(
            @Parameter(description = "查询内容") @RequestParam String query) {

        String hypotheticalDoc = ragService.generateHypotheticalDocument(query);
        return ResponseEntity.ok(hypotheticalDoc);
    }

    @GetMapping("/report")
    @Operation(summary = "生成学习报告", description = "生成知识库学习报告，包含统计分析和内容分布")
    public ResponseEntity<Map<String, Object>> generateReport(
            @Parameter(description = "命名空间") @RequestParam(required = false, defaultValue = "default") String ragNamespace,
            @Parameter(description = "会话ID") @RequestParam(required = false) String sessionId) {

        Map<String, Object> report = ragService.generateReport(ragNamespace, sessionId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/text/kb")
    @Operation(summary = "添加文本到知识库", description = "将文本内容添加到指定知识库")
    public ResponseEntity<Map<String, Object>> addTextToKnowledgeBase(
            @Parameter(description = "文本内容") @RequestBody String text,
            @Parameter(description = "知识库ID") @RequestParam Long knowledgeBaseId,
            @Parameter(description = "文档ID") @RequestParam(required = false) String documentId,
            @Parameter(description = "分块大小") @RequestParam(required = false, defaultValue = "500") int chunkSize,
            @Parameter(description = "重叠大小") @RequestParam(required = false, defaultValue = "50") int overlapSize) {

        Map<String, Object> result = ragService.addTextWithKnowledgeBase(text, knowledgeBaseId, documentId, chunkSize, overlapSize);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/document/kb")
    @Operation(summary = "上传文档到知识库", description = "上传文档文件到指定知识库")
    public ResponseEntity<Map<String, Object>> uploadDocumentToKnowledgeBase(
            @Parameter(description = "文档文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "知识库ID") @RequestParam Long knowledgeBaseId,
            @Parameter(description = "分块大小") @RequestParam(required = false, defaultValue = "500") int chunkSize,
            @Parameter(description = "重叠大小") @RequestParam(required = false, defaultValue = "50") int overlapSize) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "文件为空"));
        }

        String originalFilename = file.getOriginalFilename();
        if (!fileStorageService.isValidFileType(originalFilename)) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false, 
                "error", "不支持的文件类型。支持的格式: txt, md, html, pdf, doc, docx, ppt, pptx, json, csv"
            ));
        }

        Map<String, Object> result = ragService.addMultipartFileWithKnowledgeBase(file, knowledgeBaseId, chunkSize, overlapSize);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            result.put("fileName", originalFilename);
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search/kb")
    @Operation(summary = "检索知识库", description = "从指定知识库中检索相关内容")
    public ResponseEntity<List<Map<String, Object>>> searchKnowledgeBase(
            @Parameter(description = "查询内容") @RequestParam String query,
            @Parameter(description = "知识库ID") @RequestParam Long knowledgeBaseId,
            @Parameter(description = "返回数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "启用重排序") @RequestParam(required = false, defaultValue = "false") boolean rerank) {

        List<Map<String, Object>> results = ragService.searchWithKnowledgeBase(query, knowledgeBaseId, topK, threshold, rerank);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/ask/kb")
    @Operation(summary = "知识库问答", description = "基于指定知识库回答问题")
    public ResponseEntity<Map<String, Object>> askKnowledgeBase(
            @Parameter(description = "问题") @RequestParam String question,
            @Parameter(description = "知识库ID") @RequestParam Long knowledgeBaseId,
            @Parameter(description = "检索数量") @RequestParam(required = false, defaultValue = "5") int topK,
            @Parameter(description = "相似度阈值") @RequestParam(required = false, defaultValue = "0.7") double threshold,
            @Parameter(description = "启用重排序") @RequestParam(required = false, defaultValue = "false") boolean rerank) {

        String answer = ragService.askWithKnowledgeBase(question, knowledgeBaseId, topK, threshold, rerank);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", answer);
        result.put("knowledgeBaseId", knowledgeBaseId);
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/kb")
    @Operation(summary = "知识库文档列表", description = "获取指定知识库中的文档列表")
    public ResponseEntity<List<Map<String, Object>>> getDocumentsFromKnowledgeBase(
            @Parameter(description = "知识库ID") @RequestParam Long knowledgeBaseId) {

        List<Map<String, Object>> documents = ragService.getDocumentsWithKnowledgeBase(knowledgeBaseId);
        return ResponseEntity.ok(documents);
    }
}
