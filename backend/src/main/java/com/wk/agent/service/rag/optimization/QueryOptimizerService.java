package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QueryOptimizerService {
    private static final Logger log = LoggerFactory.getLogger(QueryOptimizerService.class);
    
    @Autowired(required = false)
    private QueryRewriter queryRewriter;
    
    @Autowired(required = false)
    private IntentClassifier intentClassifier;
    
    @Autowired(required = false)
    private SynonymExpander synonymExpander;
    
    @Autowired(required = false)
    private HydeGenerator hydeGenerator;
    
    @Autowired(required = false)
    private QueryExpander queryExpander;
    
    public OptimizedQuery optimize(String query, List<String> context) {
        OptimizedQuery result = new OptimizedQuery();
        result.setOriginalQuery(query);
        
        String optimizedQuery = query;
        
        if (queryRewriter != null) {
            optimizedQuery = queryRewriter.rewrite(query, context);
            result.setRewrittenQuery(optimizedQuery);
        }
        
        List<String> allExpandedQueries = new ArrayList<>();
        allExpandedQueries.add(optimizedQuery);
        
        if (synonymExpander != null) {
            List<String> synonymQueries = synonymExpander.expand(optimizedQuery);
            for (String q : synonymQueries) {
                if (!allExpandedQueries.contains(q)) {
                    allExpandedQueries.add(q);
                }
            }
        }
        
        if (queryExpander != null && queryExpander.isEnabled()) {
            List<String> mqeQueries = queryExpander.expand(optimizedQuery);
            for (String q : mqeQueries) {
                if (!allExpandedQueries.contains(q)) {
                    allExpandedQueries.add(q);
                }
            }
        }
        
        if (hydeGenerator != null && hydeGenerator.isEnabled()) {
            String hydeDoc = hydeGenerator.generate(optimizedQuery);
            if (hydeDoc != null && !hydeDoc.isEmpty()) {
                result.setHydeDocument(hydeDoc);
                allExpandedQueries.add(hydeDoc);
            }
        }
        
        if (allExpandedQueries.size() > 1) {
            result.setExpandedQueries(allExpandedQueries);
        }
        
        if (intentClassifier != null) {
            QueryIntent intent = intentClassifier.classify(optimizedQuery);
            result.setIntent(intent);
        }
        
        log.info("Query optimization result - original: {}, rewritten: {}, expanded count: {}, has hyde: {}",
            query, optimizedQuery, allExpandedQueries.size(), result.getHydeDocument() != null);
        
        return result;
    }
    
    public static class OptimizedQuery {
        private String originalQuery;
        private String rewrittenQuery;
        private List<String> expandedQueries;
        private QueryIntent intent;
        private String hydeDocument;
        
        public String getOriginalQuery() {
            return originalQuery;
        }
        
        public void setOriginalQuery(String originalQuery) {
            this.originalQuery = originalQuery;
        }
        
        public String getRewrittenQuery() {
            return rewrittenQuery != null ? rewrittenQuery : originalQuery;
        }
        
        public void setRewrittenQuery(String rewrittenQuery) {
            this.rewrittenQuery = rewrittenQuery;
        }
        
        public List<String> getExpandedQueries() {
            return expandedQueries != null ? expandedQueries : List.of(getRewrittenQuery());
        }
        
        public void setExpandedQueries(List<String> expandedQueries) {
            this.expandedQueries = expandedQueries;
        }
        
        public QueryIntent getIntent() {
            return intent != null ? intent : QueryIntent.OPEN_DOMAIN;
        }
        
        public void setIntent(QueryIntent intent) {
            this.intent = intent;
        }
        
        public String getHydeDocument() {
            return hydeDocument;
        }
        
        public void setHydeDocument(String hydeDocument) {
            this.hydeDocument = hydeDocument;
        }
    }
}
