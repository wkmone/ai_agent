package com.wk.agent.config;

import com.wk.agent.advisor.MyLoggerAdvisor;
import com.wk.agent.core.AgentFactory;
import com.wk.agent.mcp.McpToolRegistry;
import jakarta.annotation.Resource;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Resource
    private ToolCallback[] allTools;

    @Autowired
    private ChatMemory chatMemory;


    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        MyLoggerAdvisor.builder()
                                .showAvailableTools(true)
                                .showSystemMessage(true)
                                .build())
                // .defaultToolCallbacks(SkillsTool.builder()
                //         .addSkillsDirectory(".claude/skills")
                //         .build())
                // .defaultTools(FileSystemTools.builder().build())
                // .defaultTools(ShellTools.builder().build())
//                .defaultToolCallbacks(allTools)
                .build();
    }

    @Bean
    public AgentFactory agentFactory(org.springframework.context.ApplicationContext applicationContext, com.wk.agent.service.ModelConfigService modelConfigService, McpToolRegistry mcpToolRegistry) {
        return new AgentFactory(applicationContext, modelConfigService, mcpToolRegistry);
    }
}