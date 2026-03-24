# RAG 系统优化设计文档

## 1. 概述

### 1.1 目标

全面提升 RAG（Retrieval-Augmented Generation）系统的检索质量和回答准确性，优化用户体验。

### 1.2 当前问题

- 关键词匹配过于简单，未考虑词频、语义等因素
- 固定大小分块可能破坏语义完整性
- 检索结果去重不够智能
- 缺少查询理解和优化
- 缺少系统化的评估体系

### 1.3 优化原则

- **渐进式改进**：优先实现低成本、高收益的优化
- **可配置化**：所有优化策略支持动态配置和开关
- **可评估**：每个优化都有明确的评估指标
- **向后兼容**：保持现有 API 接口不变

***

## 2. 整体架构

### 2.1 系统架构图

```
用户查询
    │
    ▼
┌─────────────────────────────────────┐
│       查询理解与优化层               │
│  - 查询重写                          │
│  - 意图识别                          │
│  - 查询扩展 (MQE/HyDE)              │
│  - 同义词扩展                        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│       混合检索层                     │
│  - 向量检索 (Dense Retrieval)       │
│  - BM25 检索 (Sparse Retrieval)     │
│  - 混合分数融合 (RRF/加权)          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│       后处理层                       │
│  - 语义去重                          │
│  - MMR 多样性重排序                 │
│  - Cross-Encoder 重排序             │
│  - 上下文窗口扩展                    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│       生成层                         │
│  - 动态 Context 构建                 │
│  - 结构化 Prompt                     │
│  - 多轮对话记忆                      │
└─────────────────────────────────────┘
    │
    ▼
用户回答
```

### 2.2 核心模块

| 模块               | 职责   | 优先级 |
| ---------------- | ---- | --- |
| QueryOptimizer   | 查询优化 | P0  |
| HybridRetriever  | 混合检索 | P0  |
| PostProcessor    | 后处理  | P0  |
| ChunkingStrategy | 智能分块 | P1  |
| RAGEvaluator     | 评估体系 | P1  |

***

## 3. 详细设计

### 3.1 查询理解与优化层

#### 3.1.1 查询重写 (Query Rewriting)

**目标**：将用户的自然语言查询转换为更适合检索的形式

**实现方案**：

```java
public interface QueryRewriter {
    /**
     * 重写查询
     * @param query 原始查询
     * @param context 对话上下文（可选）
     * @return 重写后的查询
     */
    String rewrite(String query, List<String> context);
}

// 基于 LLM 的实现
public class LLMQueryRewriter implements QueryRewriter {
    @Override
    public String rewrite(String query, List<String> context) {
        String prompt = buildRewritePrompt(query, context);
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
    
    private String buildRewritePrompt(String query, List<String> context) {
        return """
        你是一个查询优化专家。请将用户的查询改写为更适合检索的形式。
        
        要求：
        1. 保留核心语义
        2. 补充省略的主语和宾语
        3. 去除冗余词汇
        4. 使用更规范的表述
        
        对话历史：%s
        用户查询：%s
        
        改写后的查询：
        """.formatted(
            context != null ? String.join("\n", context) : "无",
            query
        );
    }
}
```

**配置项**：

```yaml
rag:
  query:
    rewrite:
      enabled: true
      use-llm: true  # 使用 LLM 重写
      max-history: 3  # 最多使用多少轮对话历史
```

#### 3.1.2 意图识别 (Intent Classification)

**目标**：识别查询意图，选择最优检索策略

**意图类型**：

- **FACTUAL**：事实型查询（如"XX 的定义是什么"）
- **EXPLANATORY**：解释型查询（如"XX 是如何工作的"）
- **COMPARATIVE**：比较型查询（如"XX 和 YY 的区别"）
- **PROCEDURAL**：流程型查询（如"如何做 XX"）
- **OPEN\_DOMAIN**：开放域查询

**实现方案**：

```java
public enum QueryIntent {
    FACTUAL, EXPLANATORY, COMPARATIVE, PROCEDURAL, OPEN_DOMAIN
}

public interface IntentClassifier {
    QueryIntent classify(String query);
}

public class LLMIntentClassifier implements IntentClassifier {
    @Override
    public QueryIntent classify(String query) {
        String prompt = """
        请判断以下查询的意图类型：
        %s
        
        可选类型：FACTUAL, EXPLANATORY, COMPARATIVE, PROCEDURAL, OPEN_DOMAIN
        只返回类型名称：
        """.formatted(query);
        
        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        
        return QueryIntent.valueOf(result.trim());
    }
}
```

**策略映射**：

```java
public class IntentBasedStrategy {
    private final Map<QueryIntent, RetrievalStrategy> strategies = Map.of(
        QueryIntent.FACTUAL, new FactRetrievalStrategy(),      // 精确匹配，小 topK
        QueryIntent.EXPLANATORY, new ExplanationStrategy(),     // 广泛匹配，大 topK
        QueryIntent.COMPARATIVE, new ComparisonStrategy(),      // 多查询，对比检索
        QueryIntent.PROCEDURAL, new ProcedureStrategy(),        // 步骤检索
        QueryIntent.OPEN_DOMAIN, new DefaultStrategy()          // 默认策略
    );
}
```

#### 3.1.3 同义词扩展

**目标**：扩展查询的同义词，提升召回率

**实现方案**：

```java
public interface SynonymExpander {
    List<String> expand(String query);
}

public class DictionarySynonymExpander implements SynonymExpander {
    private final Map<String, List<String>> synonymDict;
    
    @Override
    public List<String> expand(String query) {
        List<String> expanded = new ArrayList<>();
        expanded.add(query);
        
        // 分词
        List<String> tokens = tokenize(query);
        for (String token : tokens) {
            List<String> synonyms = synonymDict.getOrDefault(token.toLowerCase(), List.of());
            expanded.addAll(synonyms);
        }
        
        return expanded.stream().distinct().collect(Collectors.toList());
    }
}
```

**数据源**：

- HowNet（知网）同义词词典
- 哈工大同义词词典
- 自定义业务同义词

***

### 3.2 混合检索层

#### 3.2.1 BM25 检索

**目标**：实现基于词频统计的稀疏检索

**依赖**：

```xml
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.8.0</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analyzers-common</artifactId>
    <version>9.8.0</version>
</dependency>
```

**实现方案**：

```java
public class BM25Retriever implements SparseRetriever {
    private final IndexReader indexReader;
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    
    public BM25Retriever(String indexPath) throws IOException {
        Directory directory = FSDirectory.open(Paths.get(indexPath));
        this.indexReader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(indexReader);
        this.analyzer = new StandardAnalyzer(); // 或中文分词器
    }
    
    @Override
    public List<SearchResult> search(String query, int topK) {
        QueryParser parser = new QueryParser("content", analyzer);
        Query q = parser.parse(query);
        
        TopDocs topDocs = searcher.search(q, topK);
        
        return Arrays.stream(topDocs.scoreDocs)
            .map(scoreDoc -> {
                try {
                    Document doc = searcher.doc(scoreDoc.doc);
                    return new SearchResult(
                        doc.get("id"),
                        doc.get("content"),
                        scoreDoc.score
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }
    
    public void addDocument(String id, String content, Map<String, Object> metadata) {
        // 添加到 Lucene 索引
    }
}
```

**中文分词器集成**：

```java
public class ChineseAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new IKTokenizer(); // IK Analyzer
        TokenStream filter = new LowerCaseFilter(tokenizer);
        filter = new StopFilter(filter, ChineseStopWords);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
```

#### 3.2.2 混合分数融合

**目标**：融合向量检索和 BM25 检索的结果

**方案 1：倒数排名融合 (RRF - Reciprocal Rank Fusion)**

```java
public class RRFFusion implements ScoreFusion {
    private final int k = 60; // RRF 常数
    
    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults, 
                                   List<SearchResult> bm25Results,
                                   int topK) {
        Map<String, SearchResult> merged = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        
        // 向量检索分数
        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult result = vectorResults.get(i);
            merged.put(result.getId(), result);
            scores.put(result.getId(), 1.0 / (k + i + 1));
        }
        
        // BM25 分数
        for (int i = 0; i < bm25Results.size(); i++) {
            SearchResult result = bm25Results.get(i);
            merged.put(result.getId(), result);
            scores.merge(result.getId(), 1.0 / (k + i + 1), Double::sum);
        }
        
        // 排序
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> merged.get(e.getKey()))
            .collect(Collectors.toList());
    }
}
```

**方案 2：加权融合**

```java
public class WeightedFusion implements ScoreFusion {
    private final double vectorWeight;
    private final double bm25Weight;
    
    public WeightedFusion(double vectorWeight, double bm25Weight) {
        this.vectorWeight = vectorWeight;
        this.bm25Weight = bm25Weight;
    }
    
    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults, 
                                   List<SearchResult> bm25Results,
                                   int topK) {
        // 归一化分数
        Map<String, Double> normalizedVectorScores = normalizeScores(vectorResults);
        Map<String, Double> normalizedBM25Scores = normalizeScores(bm25Results);
        
        // 合并
        Map<String, SearchResult> merged = new HashMap<>();
        Map<String, Double> finalScores = new HashMap<>();
        
        for (SearchResult result : vectorResults) {
            merged.put(result.getId(), result);
            finalScores.put(result.getId(), 
                normalizedVectorScores.get(result.getId()) * vectorWeight);
        }
        
        for (SearchResult result : bm25Results) {
            merged.put(result.getId(), result);
            double bm25Score = normalizedBM25Scores.getOrDefault(result.getId(), 0.0);
            finalScores.merge(result.getId(), bm25Score * bm25Weight, Double::sum);
        }
        
        // 排序
        return finalScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> merged.get(e.getKey()))
            .collect(Collectors.toList());
    }
    
    private Map<String, Double> normalizeScores(List<SearchResult> results) {
        double maxScore = results.stream()
            .mapToDouble(SearchResult::getScore)
            .max()
            .orElse(1.0);
        
        return results.stream()
            .collect(Collectors.toMap(
                SearchResult::getId,
                r -> r.getScore() / maxScore
            ));
    }
}
```

**配置项**：

```yaml
rag:
  retrieval:
    hybrid:
      enabled: true
      fusion-method: rrf  # rrf 或 weighted
      vector-weight: 0.6
      bm25-weight: 0.4
```

***

### 3.3 后处理层

#### 3.3.1 语义去重

**目标**：移除语义重复的检索结果

**实现方案**：

```java
public class SemanticDeduplicator {
    private final double similarityThreshold;
    private final EmbeddingModel embeddingModel;
    
    public SemanticDeduplicator(EmbeddingModel embeddingModel, double threshold) {
        this.embeddingModel = embeddingModel;
        this.similarityThreshold = threshold;
    }
    
    public List<SearchResult> deduplicate(List<SearchResult> results) {
        List<SearchResult> deduplicated = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        
        for (SearchResult result : results) {
            float[] embedding = embeddingModel.embed(result.getContent());
            
            boolean isDuplicate = false;
            for (float[] existingEmbedding : embeddings) {
                double similarity = cosineSimilarity(embedding, existingEmbedding);
                if (similarity > similarityThreshold) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (!isDuplicate) {
                deduplicated.add(result);
                embeddings.add(embedding);
            }
        }
        
        return deduplicated;
    }
    
    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

#### 3.3.2 MMR 多样性重排序

**目标**：平衡相关性和多样性

**实现方案**：

```java
public class MMRReranker {
    private final double lambda; // 0-1，越大越重视相关性
    private final EmbeddingModel embeddingModel;
    
    public List<SearchResult> rerank(List<SearchResult> results, String query, int topK) {
        List<SearchResult> selected = new ArrayList<>();
        List<SearchResult> remaining = new ArrayList<>(results);
        
        // 计算所有结果的嵌入
        Map<String, float[]> embeddings = new HashMap<>();
        float[] queryEmbedding = embeddingModel.embed(query);
        
        for (SearchResult result : results) {
            embeddings.put(result.getId(), embeddingModel.embed(result.getContent()));
        }
        
        while (selected.size() < topK && !remaining.isEmpty()) {
            SearchResult best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            
            for (SearchResult candidate : remaining) {
                // 与查询的相似度
                double querySimilarity = cosineSimilarity(
                    queryEmbedding, 
                    embeddings.get(candidate.getId())
                );
                
                // 与已选结果的最大相似度
                double maxSimilarityToSelected = selected.stream()
                    .mapToDouble(s -> cosineSimilarity(
                        embeddings.get(candidate.getId()),
                        embeddings.get(s.getId())
                    ))
                    .max()
                    .orElse(0.0);
                
                // MMR 分数
                double mmrScore = lambda * querySimilarity 
                                - (1 - lambda) * maxSimilarityToSelected;
                
                if (mmrScore > bestScore) {
                    bestScore = mmrScore;
                    best = candidate;
                }
            }
            
            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            }
        }
        
        return selected;
    }
}
```

**配置项**：

```yaml
rag:
  post-processing:
    deduplication:
      enabled: true
      similarity-threshold: 0.9
    mmr:
      enabled: true
      lambda: 0.7  # 0.7 相关性，0.3 多样性
```

#### 3.3.3 上下文窗口扩展

**目标**：提供完整的上下文信息

**实现方案**：

```java
public class ContextExpander {
    private final RagChunkMapper chunkMapper;
    private final int windowSize; // 前后各扩展多少个 chunk
    
    public List<SearchResult> expand(List<SearchResult> results) {
        List<SearchResult> expanded = new ArrayList<>();
        
        for (SearchResult result : results) {
            String documentId = result.getMetadata().get("documentId").toString();
            int chunkIndex = (int) result.getMetadata().get("chunkIndex");
            
            // 获取相邻 chunk
            List<RagChunk> neighbors = chunkMapper.findByDocumentIdAndIndexRange(
                documentId, 
                chunkIndex - windowSize, 
                chunkIndex + windowSize
            );
            
            // 合并内容
            String fullContent = mergeChunks(neighbors, chunkIndex);
            
            SearchResult expandedResult = new SearchResult(
                result.getId(),
                fullContent,
                result.getScore(),
                result.getMetadata()
            );
            expandedResult.setExpanded(true);
            expandedResult.setOriginalChunkIndex(chunkIndex);
            
            expanded.add(expandedResult);
        }
        
        return expanded;
    }
    
    private String mergeChunks(List<RagChunk> chunks, int targetIndex) {
        // 按 chunkIndex 排序
        chunks.sort(Comparator.comparingInt(RagChunk::getChunkIndex));
        
        StringBuilder sb = new StringBuilder();
        for (RagChunk chunk : chunks) {
            if (chunk.getChunkIndex() == targetIndex) {
                sb.append("[核心内容] ");
            }
            sb.append(chunk.getContent());
            sb.append("\n\n");
        }
        
        return sb.toString();
    }
}
```

***

### 3.4 智能分块策略

#### 3.4.1 语义分块

**目标**：基于文档结构进行智能分块

**实现方案**：

```java
public interface ChunkingStrategy {
    List<ChunkResult> chunk(String content, Map<String, Object> options);
}

public class SemanticChunkingStrategy implements ChunkingStrategy {
    private final int maxChunkSize;
    private final int minChunkSize;
    
    @Override
    public List<ChunkResult> chunk(String content, Map<String, Object> options) {
        List<ChunkResult> chunks = new ArrayList<>();
        
        // 1. 按段落分割
        List<String> paragraphs = splitByParagraph(content);
        
        // 2. 合并段落形成 chunk
        List<String> currentChunk = new ArrayList<>();
        int currentSize = 0;
        
        for (String paragraph : paragraphs) {
            int paragraphSize = countTokens(paragraph);
            
            if (currentSize + paragraphSize > maxChunkSize && currentSize >= minChunkSize) {
                // 创建新 chunk
                chunks.add(createChunk(currentChunk));
                currentChunk = new ArrayList<>();
                currentSize = 0;
            }
            
            currentChunk.add(paragraph);
            currentSize += paragraphSize;
        }
        
        // 添加最后一个 chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(createChunk(currentChunk));
        }
        
        return chunks;
    }
    
    private List<String> splitByParagraph(String content) {
        // 识别段落边界：空行、标题、列表等
        return Arrays.stream(content.split("\\n\\s*\\n"))
            .filter(s -> !s.trim().isEmpty())
            .collect(Collectors.toList());
    }
}
```

#### 3.4.2 父子索引

**目标**：同时索引父文档和子 chunk，提升检索灵活性

**数据结构**：

```java
public class DocumentHierarchy {
    private String parentDocumentId;
    private String parentContent;
    private List<ChildChunk> children;
}

public class ChildChunk {
    private String chunkId;
    private String content;
    private int startIndex;
    private int endIndex;
    private String headingPath;
}
```

**检索策略**：

- 检索子 chunk，返回父文档
- 或者检索子 chunk，返回子 chunk + 相邻子 chunk

***

### 3.5 评估体系

#### 3.5.1 评估指标

**检索质量指标**：

- **Precision\@K**：前 K 个结果的相关性比例
- **Recall\@K**：检索出的相关文档占所有相关文档的比例
- **NDCG\@K**：归一化折损累积增益
- **MRR**：平均倒数排名

**生成质量指标**：

- **Answer Relevance**：回答与查询的相关性
- **Faithfulness**：回答是否基于检索内容
- **Context Precision**：上下文的精确度

#### 3.5.2 RAGAS 集成

**依赖**：

```python
# Python 评估服务
pip install ragas
pip install datasets
```

**评估服务**：

```python
from ragas import evaluate
from ragas.metrics import (
    answer_relevancy,
    faithfulness,
    context_precision,
    context_recall
)

class RAGEvaluator:
    def __init__(self):
        self.metrics = [
            answer_relevancy,
            faithfulness,
            context_precision,
            context_recall
        ]
    
    def evaluate(self, dataset):
        """
        dataset 包含：question, answer, contexts, ground_truth
        """
        result = evaluate(
            dataset=dataset,
            metrics=self.metrics
        )
        return result
```

#### 3.5.3 A/B 测试框架

**实现方案**：

```java
public class ABTestFramework {
    private final Map<String, RetrievalConfig> configs;
    private final Random random = new Random();
    
    public RetrievalConfig getConfigForUser(String userId) {
        // 根据用户 ID 分配实验组
        int bucket = Math.abs(userId.hashCode()) % 100;
        
        if (bucket < 50) {
            return configs.get("control"); // 对照组
        } else {
            return configs.get("treatment"); // 实验组
        }
    }
    
    public void logEvent(String userId, String eventType, Map<String, Object> data) {
        // 记录用户行为日志
    }
}
```

***

## 4. 实施计划

### Phase 1: 基础优化 (P0) - 2 周

#### Week 1

- [ ] BM25 检索实现
- [ ] 中文分词器集成
- [ ] RRF 分数融合
- [ ] 单元测试

#### Week 2

- [ ] 语义去重
- [ ] MMR 重排序
- [ ] 配置化支持
- [ ] 集成测试

**交付物**：

- 混合检索功能上线
- 检索质量提升 20%+

### Phase 2: 查询优化 (P1) - 2 周

#### Week 3

- [x] 查询重写
- [ ] 意图识别
- [ ] 同义词扩展

#### Week 4

- [ ] 上下文窗口扩展
- [ ] 智能分块策略
- [ ] 性能优化

**交付物**：

- 查询理解能力提升
- 用户满意度提升 15%+

### : 评估体系 (P1) - 1 周

#### Week 5

- [ ] RAGAS 评估集成
- [ ] A/B 测试框架
- [ ] 监控 Dashboard

**交付物**：

- 完整的评估体系
- 数据驱动的优化闭环

### Phase 4: 高级优化 (P2) - 3 周

#### Week 6-8

- [ ] 父子索引
- [ ] 语义分块
- [ ] 多向量检索探索
- [ ] 领域适配微调

**交付物**：

- 高级检索功能
- 领域特定优化

***

## 5. 配置设计

### 5.1 完整配置示例

```yaml
rag:
  # 查询优化配置
  query:
    rewrite:
      enabled: true
      use-llm: true
      max-history: 3
    intent-classification:
      enabled: true
    synonym-expansion:
      enabled: true
      dictionary-path: /path/to/synonym.dict
  
  # 检索配置
  retrieval:
    hybrid:
      enabled: true
      fusion-method: rrf  # rrf | weighted
      vector-weight: 0.6
      bm25-weight: 0.4
    vector-store:
      type: qdrant  # qdrant | milvus | chroma
      url: http://localhost:6333
      collection-name: rag_vectors
    bm25:
      index-path: /path/to/bm25/index
      analyzer: chinese  # standard | chinese
  
  # 后处理配置
  post-processing:
    deduplication:
      enabled: true
      similarity-threshold: 0.9
    mmr:
      enabled: true
      lambda: 0.7
    context-expansion:
      enabled: true
      window-size: 2
  
  # 分块配置
  chunking:
    strategy: semantic  # fixed | semantic
    max-chunk-size: 500
    min-chunk-size: 100
    overlap: 50
  
  # 评估配置
  evaluation:
    enabled: true
    metrics:
      - answer_relevancy
      - faithfulness
      - context_precision
    ab-test:
      enabled: true
      traffic-split: 50
  
  # 缓存配置
  cache:
    enabled: true
    type: redis
    ttl-minutes: 30
```

***

## 6. API 设计

### 6.1 检索 API 扩展

```java
public interface RagService {
    // 现有 API 保持不变
    
    // 新增高级检索 API
    AdvancedSearchResult advancedSearch(AdvancedSearchRequest request);
    
    // 评估 API
    EvaluationResult evaluate(EvaluationRequest request);
}

public class AdvancedSearchRequest {
    private String query;
    private String namespace;
    private int topK;
    private double threshold;
    
    // 查询优化选项
    private boolean enableRewrite;
    private boolean enableIntentClassification;
    private boolean enableSynonymExpansion;
    
    // 检索选项
    private String fusionMethod; // rrf | weighted
    private Double vectorWeight;
    private Double bm25Weight;
    
    // 后处理选项
    private boolean enableDeduplication;
    private boolean enableMMR;
    private boolean enableContextExpansion;
    
    // 实验配置
    private String experimentGroup;
}

public class AdvancedSearchResult {
    private List<SearchResult> results;
    private SearchMetadata metadata;
    private List<String> appliedOptimizations;
    private String experimentGroup;
}

public class SearchMetadata {
    private int totalResults;
    private long searchTimeMs;
    private Map<String, Object> debugInfo;
}
```

***

## 7. 测试计划

### 7.1 单元测试

- 每个模块的单元测试覆盖率 > 80%
- 重点测试边界条件和异常情况

### 7.2 集成测试

- 端到端检索流程测试
- 性能测试（QPS、延迟）
- 压力测试

### 7.3 离线评估

- 构建测试集（100+ 查询）
- 对比优化前后指标
- A/B 测试

### 7.4 在线评估

- 用户反馈收集
- 点击率分析
- 转化率分析

***

## 8. 风险与缓解

| 风险        | 影响 | 概率 | 缓解措施                |
| --------- | -- | -- | ------------------- |
| BM25 性能问题 | 高  | 中  | 使用增量索引、缓存热点数据       |
| 查询重写质量不稳定 | 中  | 中  | 设置 fallback 机制、人工审核 |
| 评估数据不足    | 中  | 高  | 先构建种子测试集、逐步积累       |
| 配置复杂度太高   | 低  | 高  | 提供默认配置、配置模板         |

***

## 9. 成功标准

### 技术指标

- 检索 Precision\@10 提升 30%+
- 检索 Recall\@10 提升 25%+
- 平均响应时间 < 500ms
- 系统可用性 > 99.9%

### 业务指标

- 用户满意度提升 20%+
- 检索点击率提升 15%+
- 负面反馈率降低 30%+

***

## 10. 附录

### 10.1 参考资料

- [RAGAS 论文](https://arxiv.org/abs/2309.15217)
- [DPR (Dense Passage Retrieval)](https://arxiv.org/abs/2004.04906)
- [BM25 算法详解](https://en.wikipedia.org/wiki/Okapi_BM25)
- [MMR (Maximal Marginal Relevance)](https://www.cs.cmu.edu/~jgc/publication/The_Use_MMR_Diversity_Based_LTMIR_1998.pdf)

### 10.2 术语表

- **RAG**: Retrieval-Augmented Generation，检索增强生成
- **BM25**: Best Matching 25，经典的检索算法
- **RRF**: Reciprocal Rank Fusion，倒数排名融合
- **MMR**: Maximal Marginal Relevance，最大边界相关性
- **HyDE**: Hypothetical Document Embeddings，假设文档嵌入
- **MQE**: Multiple Queries Expansion，多查询扩展

***

**文档版本**: v1.0\
**创建时间**: 2026-03-24\
**最后更新**: 2026-03-24\
**作者**: AI Assistant
