package com.wk.agent.service.rag.optimization;

import java.util.List;

public interface QueryRewriter {
    String rewrite(String query, List<String> context);
}
