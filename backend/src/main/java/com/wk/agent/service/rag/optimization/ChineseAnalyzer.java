package com.wk.agent.service.rag.optimization;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.springframework.stereotype.Component;

@Component
public class ChineseAnalyzer {
    
    public static Analyzer createCJKAnalyzer() {
        return new CJKAnalyzer();
    }
}
