package com.wk.agent.service.rag.optimization;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BM25Retriever implements SparseRetriever {
    private static final Logger log = LoggerFactory.getLogger(BM25Retriever.class);
    
    @Value("${rag.retrieval.bm25.index-path:./data/bm25-index}")
    private String indexPath;
    
    @Value("${rag.retrieval.bm25.analyzer:standard}")
    private String analyzerType;
    
    private Directory directory;
    private IndexWriter indexWriter;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;
    private Analyzer analyzer;
    private boolean initialized = false;
    
    private final Map<String, Map<String, Object>> documentMetadataCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        try {
            log.info("初始化 BM25 检索器，索引路径: {}, 分词器: {}", indexPath, analyzerType);
            
            this.directory = FSDirectory.open(Paths.get(indexPath));
            this.analyzer = createAnalyzer();
            
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            
            this.indexWriter = new IndexWriter(directory, config);
            this.indexWriter.commit();
            
            reopenReader();
            initialized = true;
            log.info("BM25 检索器初始化成功");
        } catch (Exception e) {
            log.error("BM25 检索器初始化失败: {}", e.getMessage(), e);
            initialized = false;
        }
    }
    
    private void reopenReader() throws IOException {
        if (indexReader != null) {
            indexReader.close();
        }
        this.indexReader = DirectoryReader.open(directory);
        this.indexSearcher = new IndexSearcher(indexReader);
        this.indexSearcher.setSimilarity(new BM25Similarity());
    }
    
    @Override
    public List<SearchResult> search(String query, String ragNamespace, int topK) {
        if (!initialized) {
            log.warn("BM25 检索器未初始化，返回空结果");
            return new ArrayList<>();
        }
        
        List<SearchResult> results = new ArrayList<>();
        
        try {
            String escapedQuery = QueryParser.escape(query);
            String namespaceQuery = ragNamespace != null && !ragNamespace.isEmpty()
                ? String.format("ragNamespace:\"%s\" AND (%s)", ragNamespace, escapedQuery)
                : escapedQuery;
            
            QueryParser parser = new QueryParser("content", analyzer);
            Query q = parser.parse(namespaceQuery);
            
            TopDocs topDocs = indexSearcher.search(q, topK);
            
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = indexSearcher.doc(scoreDoc.doc);
                String id = doc.get("id");
                
                Map<String, Object> metadata = documentMetadataCache.getOrDefault(id, new HashMap<>());
                metadata.put("ragNamespace", doc.get("ragNamespace"));
                
                SearchResult result = SearchResult.builder()
                    .id(id)
                    .content(doc.get("content"))
                    .score(scoreDoc.score)
                    .metadata(metadata)
                    .build();
                
                results.add(result);
            }
            
        } catch (Exception e) {
            log.error("BM25 检索失败: {}", e.getMessage());
        }
        
        return results;
    }
    
    @Override
    public void addDocument(String id, String content, String ragNamespace, Map<String, Object> metadata) {
        if (!initialized) {
            log.warn("BM25 检索器未初始化，跳过添加文档");
            return;
        }
        
        try {
            Document doc = new Document();
            doc.add(new StringField("id", id, Field.Store.YES));
            doc.add(new TextField("content", content, Field.Store.YES));
            doc.add(new StringField("ragNamespace", ragNamespace != null ? ragNamespace : "", Field.Store.YES));
            
            indexWriter.addDocument(doc);
            indexWriter.commit();
            reopenReader();
            
            if (metadata != null) {
                documentMetadataCache.put(id, new HashMap<>(metadata));
            }
            
            log.debug("BM25 索引添加文档: {}", id);
            
        } catch (Exception e) {
            log.error("BM25 添加文档失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void deleteDocument(String id) {
        if (!initialized) {
            log.warn("BM25 检索器未初始化，跳过删除文档");
            return;
        }
        
        try {
            QueryParser parser = new QueryParser("id", analyzer);
            Query query = parser.parse("id:\"" + id + "\"");
            indexWriter.deleteDocuments(query);
            indexWriter.commit();
            reopenReader();
            documentMetadataCache.remove(id);
            
            log.debug("BM25 索引删除文档: {}", id);
            
        } catch (Exception e) {
            log.error("BM25 删除文档失败: {}", e.getMessage());
        }
    }
    
    @Override
    public void rebuildIndex(String ragNamespace) {
        log.info("重建 BM25 索引，namespace: {}", ragNamespace);
    }
    
    private Analyzer createAnalyzer() {
        return switch (analyzerType.toLowerCase()) {
            case "chinese", "cjk" -> new CJKAnalyzer();
            default -> new StandardAnalyzer();
        };
    }
    
    @PreDestroy
    public void close() {
        try {
            if (indexWriter != null) {
                indexWriter.close();
            }
            if (indexReader != null) {
                indexReader.close();
            }
            if (directory != null) {
                directory.close();
            }
            log.info("BM25 检索器已关闭");
        } catch (Exception e) {
            log.error("关闭 BM25 检索器失败: {}", e.getMessage(), e);
        }
    }
}
