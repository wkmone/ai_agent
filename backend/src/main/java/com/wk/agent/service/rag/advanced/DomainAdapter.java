package com.wk.agent.service.rag.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DomainAdapter {
    private static final Logger log = LoggerFactory.getLogger(DomainAdapter.class);
    
    @Value("${rag.advanced.domain-adaptation.enabled:false}")
    private boolean domainAdaptationEnabled;
    
    @Value("${rag.advanced.domain-adaptation.default-domain:general}")
    private String defaultDomain;
    
    private final Map<String, DomainConfig> domainConfigs = new ConcurrentHashMap<>();
    private final Map<String, String> queryDomainCache = new ConcurrentHashMap<>();
    
    public static class DomainConfig {
        private String domainName;
        private List<String> stopWords;
        private List<String> domainTerms;
        private double similarityBoost;
        private int preferredChunkSize;
        
        public DomainConfig(String domainName) {
            this.domainName = domainName;
            this.stopWords = new ArrayList<>();
            this.domainTerms = new ArrayList<>();
            this.similarityBoost = 1.0;
            this.preferredChunkSize = 500;
        }
        
        public String getDomainName() { return domainName; }
        public List<String> getStopWords() { return stopWords; }
        public void setStopWords(List<String> stopWords) { this.stopWords = stopWords; }
        public List<String> getDomainTerms() { return domainTerms; }
        public void setDomainTerms(List<String> domainTerms) { this.domainTerms = domainTerms; }
        public double getSimilarityBoost() { return similarityBoost; }
        public void setSimilarityBoost(double similarityBoost) { this.similarityBoost = similarityBoost; }
        public int getPreferredChunkSize() { return preferredChunkSize; }
        public void setPreferredChunkSize(int preferredChunkSize) { this.preferredChunkSize = preferredChunkSize; }
    }
    
    public DomainAdapter() {
        initDefaultDomains();
    }
    
    private void initDefaultDomains() {
        DomainConfig generalDomain = new DomainConfig("general");
        domainConfigs.put("general", generalDomain);
        
        DomainConfig techDomain = new DomainConfig("technology");
        techDomain.setDomainTerms(Arrays.asList(
            "API", "REST", "JSON", "HTTP", "HTTPS", "database", "algorithm", 
            "framework", "library", "SDK", "IDE", "server", "client", "cache"
        ));
        techDomain.setSimilarityBoost(1.2);
        techDomain.setPreferredChunkSize(400);
        domainConfigs.put("technology", techDomain);
        
        DomainConfig legalDomain = new DomainConfig("legal");
        legalDomain.setDomainTerms(Arrays.asList(
            "contract", "agreement", "law", "regulation", "compliance", 
            "liability", "indemnification", "warranty", "termination"
        ));
        legalDomain.setSimilarityBoost(1.3);
        legalDomain.setPreferredChunkSize(600);
        domainConfigs.put("legal", legalDomain);
        
        DomainConfig medicalDomain = new DomainConfig("medical");
        medicalDomain.setDomainTerms(Arrays.asList(
            "diagnosis", "treatment", "symptom", "patient", "doctor",
            "medication", "therapy", "procedure", "clinical"
        ));
        medicalDomain.setSimilarityBoost(1.3);
        medicalDomain.setPreferredChunkSize(500);
        domainConfigs.put("medical", medicalDomain);
    }
    
    public String detectDomain(String query) {
        if (!domainAdaptationEnabled) {
            return defaultDomain;
        }
        
        String cachedDomain = queryDomainCache.get(query);
        if (cachedDomain != null) {
            return cachedDomain;
        }
        
        String detectedDomain = defaultDomain;
        int maxMatches = 0;
        
        for (Map.Entry<String, DomainConfig> entry : domainConfigs.entrySet()) {
            if (entry.getKey().equals("general")) continue;
            
            int matches = countDomainTermMatches(query, entry.getValue());
            if (matches > maxMatches) {
                maxMatches = matches;
                detectedDomain = entry.getKey();
            }
        }
        
        queryDomainCache.put(query, detectedDomain);
        log.debug("Detected domain for query: {} -> {}", query, detectedDomain);
        
        return detectedDomain;
    }
    
    private int countDomainTermMatches(String query, DomainConfig config) {
        String lowerQuery = query.toLowerCase();
        int matches = 0;
        
        for (String term : config.getDomainTerms()) {
            if (lowerQuery.contains(term.toLowerCase())) {
                matches++;
            }
        }
        
        return matches;
    }
    
    public DomainConfig getDomainConfig(String domain) {
        return domainConfigs.getOrDefault(domain, domainConfigs.get(defaultDomain));
    }
    
    public void registerDomainConfig(DomainConfig config) {
        domainConfigs.put(config.getDomainName(), config);
        log.info("Registered domain config: {}", config.getDomainName());
    }
    
    public String adaptQuery(String query, String domain) {
        if (!domainAdaptationEnabled) {
            return query;
        }
        
        DomainConfig config = getDomainConfig(domain);
        String adaptedQuery = query;
        
        for (String stopWord : config.getStopWords()) {
            adaptedQuery = adaptedQuery.replaceAll("\\b" + stopWord + "\\b", "");
        }
        
        return adaptedQuery.trim().replaceAll("\\s+", " ");
    }
    
    public double getSimilarityBoost(String domain) {
        DomainConfig config = getDomainConfig(domain);
        return config.getSimilarityBoost();
    }
    
    public int getPreferredChunkSize(String domain) {
        DomainConfig config = getDomainConfig(domain);
        return config.getPreferredChunkSize();
    }
    
    public boolean isEnabled() {
        return domainAdaptationEnabled;
    }
    
    public void clearCache() {
        queryDomainCache.clear();
        log.info("Cleared domain detection cache");
    }
}
