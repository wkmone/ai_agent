package com.wk.agent.service.rag.optimization;

import java.util.List;

public interface SynonymExpander {
    List<String> expand(String query);
}
