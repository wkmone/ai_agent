package com.wk.agent.controller;

import com.wk.agent.advisor.MyLoggerAdvisor;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/demo")
public class SkillController {
    private final ChatClient chatClient;

    @Value("classpath:/.claude/skills/")
    private String skillsResource;

    @Autowired
    private ResourceLoader resourceLoader;

    public SkillController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("始终运用现有技能协助用户满足其要求.")
                .defaultAdvisors(
                        ToolCallAdvisor.builder().build(),
                        MyLoggerAdvisor.builder()
                                .showAvailableTools(true)
                                .showSystemMessage(true)
                                .build())
                .build();
    }


    @GetMapping("/skill")
    public String chat() {
        return chatClient
                .prompt("""
                            今天西安的天气
                             """)
                .call()
                .content();
    }
}