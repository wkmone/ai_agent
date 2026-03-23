package com.wk.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 翻译服务工具
 */
@Component
public class TranslationTool {
    
    @Tool(description = "翻译文本到指定语言")
    public String translate(
            @ToolParam(description = "要翻译的文本") String text,
            @ToolParam(description = "目标语言，如：en、zh等") String targetLanguage) {
        // 模拟翻译结果
        // 实际应用中可以调用翻译API获取真实结果
        if (targetLanguage.equals("en")) {
            return String.format("原文: %s\n译文: [English translation of '%s']", text, text);
        } else if (targetLanguage.equals("zh")) {
            return String.format("原文: %s\n译文: [%s的中文翻译]", text, text);
        } else {
            return String.format("原文: %s\n译文: [Translation to %s]", text, targetLanguage);
        }
    }
}
