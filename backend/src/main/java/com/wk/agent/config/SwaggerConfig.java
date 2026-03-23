package com.wk.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .tags(tags());
    }

    private Info apiInfo() {
        return new Info()
                .title("AI Agent API")
                .description("""
                    AI Agent 多层记忆系统 API 文档
                    
                    ### 核心功能
                    - **记忆管理**: 工作记忆、情景记忆、语义记忆、感知记忆
                    - **知识图谱**: 概念节点、关系管理、知识推理
                    - **多模态编码**: 文本、图像、音频编码
                    
                    ### 记忆类型说明
                    | 记忆类型 | 描述 | 存储位置 |
                    |---------|------|---------|
                    | 工作记忆 | 当前对话上下文 | Redis |
                    | 情景记忆 | 事件和经历 | PostgreSQL |
                    | 语义记忆 | 概念和知识 | PostgreSQL + Neo4j |
                    | 感知记忆 | 感官输入 | PostgreSQL |
                    """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("AI Agent Team")
                        .email("agent@example.com"));
    }

    private List<Server> servers() {
        return Arrays.asList(
                new Server().url("http://localhost:8080").description("开发环境")
        );
    }

    private List<Tag> tags() {
        return Arrays.asList(
                new Tag().name("Chat").description("对话接口"),
                new Tag().name("Memory").description("记忆管理"),
                new Tag().name("Knowledge Graph").description("知识图谱"),
                new Tag().name("Model Config").description("模型配置"),
                new Tag().name("Health").description("健康检查")
        );
    }
}
