package com.wk.agent.service.rag.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DictionarySynonymExpander implements SynonymExpander {
    private static final Logger log = LoggerFactory.getLogger(DictionarySynonymExpander.class);
    
    @Value("${rag.query.synonym-expansion.enabled:false}")
    private boolean enabled;
    
    private final Map<String, List<String>> synonymDict;
    
    public DictionarySynonymExpander() {
        this.synonymDict = buildDefaultSynonymDict();
    }
    
    private Map<String, List<String>> buildDefaultSynonymDict() {
        Map<String, List<String>> dict = new HashMap<>();
        
        dict.put("如何", Arrays.asList("怎样", "怎么", "如何做"));
        dict.put("什么", Arrays.asList("哪些", "啥"));
        dict.put("是", Arrays.asList("为", "即是"));
        dict.put("的", Arrays.asList("之"));
        dict.put("和", Arrays.asList("与", "及", "跟"));
        dict.put("可以", Arrays.asList("能够", "能"));
        dict.put("需要", Arrays.asList("要", "需"));
        dict.put("使用", Arrays.asList("用", "利用", "采用"));
        dict.put("方法", Arrays.asList("方式", "办法", "手段"));
        dict.put("问题", Arrays.asList("疑问", "难题"));
        dict.put("答案", Arrays.asList("回答", "解答"));
        dict.put("说明", Arrays.asList("解释", "介绍", "描述"));
        dict.put("区别", Arrays.asList("差异", "不同", "差别"));
        dict.put("相同", Arrays.asList("一样", "类似", "相似"));
        dict.put("学习", Arrays.asList("学", "研究", "了解"));
        dict.put("了解", Arrays.asList("知道", "明白", "理解"));
        dict.put("理解", Arrays.asList("明白", "懂得", "领会"));
        
        return dict;
    }
    
    @Override
    public List<String> expand(String query) {
        if (!enabled || query == null || query.isEmpty()) {
            return Collections.singletonList(query);
        }
        
        List<String> expanded = new ArrayList<>();
        expanded.add(query);
        
        List<String> tokens = tokenize(query);
        for (String token : tokens) {
            List<String> synonyms = synonymDict.getOrDefault(token.toLowerCase(), Collections.emptyList());
            for (String synonym : synonyms) {
                String expandedQuery = query.replace(token, synonym);
                if (!expanded.contains(expandedQuery)) {
                    expanded.add(expandedQuery);
                }
            }
        }
        
        List<String> result = expanded.stream().distinct().collect(Collectors.toList());
        log.info("同义词扩展: 原始查询='{}', 扩展后查询数={}", query, result.size());
        
        return result;
    }
    
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String[] words = text.split("[\\s,，。！？、；：\"''（）【】《》]+");
        for (String word : words) {
            if (word.length() >= 1) {
                tokens.add(word);
            }
        }
        return tokens;
    }
}
