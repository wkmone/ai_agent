package com.wk.agent.service.rag.optimization;

public interface IntentClassifier {
    QueryIntent classify(String query);
}
