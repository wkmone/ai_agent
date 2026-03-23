根据文档内容，正确使用 SkillsTool（Spring AI Agent Skills 的核心工具）的完整流程如下：

SkillsTool 使用流程

1. 添加依赖

首先在项目中添加 spring-ai-agent-utils 依赖：
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>0.4.2</version>
</dependency>

注意：需要 Spring AI 2.0.0-M2 或更高版本。

2. 配置 SkillsTool

在 Spring Boot 应用中配置 SkillsTool 并注册到 ChatClient：
@SpringBootApplication
public class Application {
    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                .defaultToolCallbacks(
                    SkillsTool.builder()
                        .addSkillsDirectory(".claude/skills")  // 指定技能目录
                        .build()
                )
                .defaultTools(FileSystemTools.builder().build())  // 可选：文件系统工具
                .defaultTools(ShellTools.builder().build())       // 可选：Shell工具
                .build();
            
            // 使用代理
            String response = chatClient.prompt()
                .user("Your task here")
                .call()
                .content();
        };
    }
}


3. 创建技能目录结构

按照规范创建技能文件夹和文件：

.claude/skills/
└── my-skill/
    ├── SKILL.md          # 必需：技能指令和元数据
    ├── scripts/          # 可选：可执行脚本
    │   └── get_youtube_transcript.py
    └── references/       # 可选：参考资料
        └── research_methodology.md


4. 编写技能文件 (SKILL.md)

每个技能必须包含 YAML 前端元数据和具体指令：
---
name: code-reviewer
description: Reviews Java code for best practices, security issues, and Spring Framework conventions. Use when user asks to review, analyze, or audit code.
---

# Code Reviewer

## Instructions
When reviewing code:
1. Check for security vulnerabilities (SQL injection, XSS, etc.)
2. Verify Spring Boot best practices (proper use of @Service, @Repository, etc.)
3. Look for potential null pointer exceptions
4. Suggest improvements for readability and maintainability
5. Provide specific line-by-line feedback with code examples


5. 使用技能

当用户请求匹配技能描述时，代理会自动触发技能：
String response = chatClient.prompt()
    .user("Review this controller class for best practices: " + 
          "src/main/java/com/example/UserController.java")
    .call()
    .content();


工作流程详解

三步执行过程

1. 发现阶段：应用启动时，SkillsTool 扫描配置的技能目录，解析每个 SKILL.md 的 YAML 元数据，构建轻量级技能注册表。
2. 语义匹配：用户请求时，LLM 检查技能描述，如果语义匹配则调用 Skill 工具并传入技能名称。
3. 执行阶段：SkillsTool 加载完整的 SKILL.md 内容，LLM 按指令执行，必要时通过 FileSystemTools 读取参考文件或通过 ShellTools 执行脚本。

生产环境建议

• 类路径加载：对于打包应用，可以从类路径加载技能：
  .defaultToolCallbacks(SkillsTool.builder()
      .addSkillsResource(resourceLoader.getResource("classpath:.claude/skills"))
      .build())
  
• 安全考虑：脚本直接在本地机器执行，无沙箱保护。建议：

  • 审查第三方技能脚本

  • 在容器化环境（Docker、Kubernetes）中运行代理应用

  • 考虑实现自定义审批工作流

关键注意事项

1. 渐进式上下文管理：技能采用按需加载机制，仅当需要时才加载完整内容，保持上下文窗口高效。
2. 模型无关性：技能可在 OpenAI、Anthropic、Google Gemini 等多种 LLM 间移植。
3. 与 Anthropic Skills API 的区别：
   • Generic Agent Skills：在您的环境中运行，支持本地资源访问

   • Anthropic Skills：在 Anthropic 沙箱云容器中运行，提供预构建文档生成功能

   • 两者可同时使用，互补不同需求

通过以上流程，您可以创建模块化、可重用的技能，扩展 Spring AI 代理的能力，而无需修改核心代码或绑定特定 LLM 供应商。