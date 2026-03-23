package com.wk.agent.config;

import com.wk.agent.mcp.McpToolRegistry;
import com.wk.agent.tool.BoChaSearchTool;
import com.wk.agent.tool.NewsTool;
import com.wk.agent.tool.PDFGenerationTool;
import com.wk.agent.tool.TerminalTool;
import com.wk.agent.tool.TranslationTool;
import com.wk.agent.tool.WeatherTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Autowired(required = false)
    private WeatherTool weatherTool;

    @Autowired(required = false)
    private NewsTool newsTool;

    @Autowired(required = false)
    private TranslationTool translationTool;

    @Autowired(required = false)
    private TerminalTool terminalTool;

    @Autowired(required = false)
    private BoChaSearchTool boChaSearchTool;

    @Autowired(required = false)
    private PDFGenerationTool pdfGenerationTool;

    @Autowired
    @Lazy
    private McpToolRegistry mcpToolRegistry;

    private ToolCallback[] staticToolsCache;

    public ToolCallback[] getAllTools() {
        List<ToolCallback> allToolCallbacks = new ArrayList<>();
        
        if (staticToolsCache == null) {
            List<Object> toolObjects = new ArrayList<>();
            if (weatherTool != null) toolObjects.add(weatherTool);
            if (newsTool != null) toolObjects.add(newsTool);
            if (translationTool != null) toolObjects.add(translationTool);
            if (terminalTool != null) toolObjects.add(terminalTool);
            if (boChaSearchTool != null) toolObjects.add(boChaSearchTool);
            if (pdfGenerationTool != null) toolObjects.add(pdfGenerationTool);
            
            if (!toolObjects.isEmpty()) {
                staticToolsCache = MethodToolCallbackProvider.builder()
                        .toolObjects(toolObjects.toArray())
                        .build()
                        .getToolCallbacks();
            } else {
                staticToolsCache = new ToolCallback[0];
            }
        }
        
        allToolCallbacks.addAll(List.of(staticToolsCache));
        
        if (mcpToolRegistry != null && mcpToolRegistry.hasTools()) {
            allToolCallbacks.addAll(mcpToolRegistry.getToolCallbacks());
        }
        
        return allToolCallbacks.toArray(new ToolCallback[0]);
    }

    @Bean
    public ToolCallback[] allTools() {
        return getAllTools();
    }
}
