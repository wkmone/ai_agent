# 后端模块详细设计文档

## 1. 概述

### 1.1 技术栈

- **框架**: Spring Boot 4.0.3
- **AI 集成**: Spring AI 2.0.0-m2
- **ORM**: MyBatis Plus 3.5.5
- **数据库**: PostgreSQL 15
- **缓存**: Redis 7
- **消息队列**: RabbitMQ 3.12

### 1.2 项目结构

```
backend/
├── src/main/java/com/wk/agent/
│   ├── BackendApplication.java          # 启动类
│   ├── config/                          # 配置类
│   │   ├── ChatConfig.java              # Chat 配置
│   │   ├── VectorStoreConfig.java       # 向量存储配置
│   │   ├── RabbitMQConfig.java          # 消息队列配置
│   │   └── CorsConfig.java              # CORS 配置
│   ├── controller/                      # 控制器
│   │   ├── AgentController.java         # Agent 控制器
│   │   ├── McpServerController.java     # MCP 服务器控制器
│   │   ├── ModelConfigController.java   # 模型配置控制器
│   │   └── RagKnowledgeBaseController.java # RAG 控制器
│   ├── service/                         # 服务层
│   │   ├── AgentService.java            # Agent 服务接口
│   │   ├── ModelConfigService.java      # 模型配置服务
│   │   ├── McpServerService.java        # MCP 服务
│   │   └── RagKnowledgeBaseService.java # RAG 服务
│   ├── core/                            # 核心模块
│   │   ├── AbstractAgent.java           # Agent 抽象基类
│   │   ├── AgentFactory.java            # Agent 工厂
│   │   └── AgentAsyncExecutor.java      # Agent 异步执行器
│   ├── impl/                            # 实现类
│   │   └── SimpleAgent.java             # 简单 Agent 实现
│   ├── factory/                         # 工厂类
│   │   └── DynamicChatClientFactory.java # 动态 ChatClient 工厂
│   ├── mcp/                             # MCP 模块
│   │   ├── McpClientManager.java        # MCP 客户端管理
│   │   ├── McpToolRegistry.java         # MCP 工具注册
│   │   ├── StdioMcpClient.java          # STDIO 客户端
│   │   └── StreamableHttpMcpClient.java # HTTP 客户端
│   ├── entity/                          # 实体类
│   │   ├── ModelConfig.java             # 模型配置实体
│   │   ├── AgentConfig.java             # Agent 配置实体
│   │   └── McpServer.java               # MCP 服务器实体
│   └── repository/                      # 数据访问层
│       ├── ModelConfigMapper.java       # 模型配置 Mapper
│       ├── AgentConfigMapper.java       # Agent 配置 Mapper
│       └── McpServerMapper.java         # MCP 服务器 Mapper
└── src/main/resources/
    ├── application.yml                  # 应用配置
    └── mapper/                          # MyBatis XML
```

## 2. 核心模块设计

### 2.1 Agent 核心模块

#### 2.1.1 AbstractAgent 抽象类

**职责**: 定义 Agent 的基本结构和行为

**核心方法**:
```java
public abstract class AbstractAgent {
    // Agent 执行
    public abstract AgentResponse execute(AgentRequest request);
    
    // 消息处理
    protected abstract String processMessage(String message);
    
    // 构建知识库上下文
    protected String buildKnowledgeBaseContext();
    
    // 获取可用工具
    protected List<ToolCallback> getAvailableTools();
    
    // 工具描述
    protected String getToolDescriptions();
}
```

**字段说明**:
- `agentConfig`: Agent 配置信息
- `chatMemory`: 聊天记忆
- `ragService`: RAG 服务
- `filteredToolCallbacks`: 过滤后的工具回调

#### 2.1.2 SimpleAgent 实现类

**职责**: 实现简单的对话 Agent

**核心流程**:
```java
public class SimpleAgent extends AbstractAgent {
    
    @Override
    public AgentResponse execute(AgentRequest request) {
        // 1. 加载历史记忆
        loadChatMemory(request.getConversationId());
        
        // 2. 构建知识上下文
        String knowledgeContext = buildKnowledgeBaseContext();
        
        // 3. 获取工具描述
        String tools = getToolDescriptions();
        
        // 4. 调用 AI 模型
        String response = chatClientCall(request.getMessage(), knowledgeContext, tools);
        
        // 5. 保存记忆
        saveChatMemory(request.getConversationId(), response);
        
        return new AgentResponse(response);
    }
}
```

#### 2.1.3 AgentFactory

**职责**: 创建和管理 Agent 实例

**核心方法**:
```java
@Component
public class AgentFactory {
    
    @Autowired
    private DynamicChatClientFactory chatClientFactory;
    
    @Autowired
    private ChatMemory chatMemory;
    
    @Autowired
    private RagService ragService;
    
    public AbstractAgent createAgent(AgentConfig config) {
        // 1. 根据模型配置创建 ChatClient
        ChatClient chatClient = chatClientFactory.createChatClient(
            config.getModelConfigId()
        );
        
        // 2. 获取工具列表
        List<ToolCallback> tools = getToolsForAgent(config);
        
        // 3. 创建 Agent 实例
        return new SimpleAgent(
            config,
            chatClient,
            chatMemory,
            ragService,
            tools
        );
    }
}
```

### 2.2 DynamicChatClientFactory

**职责**: 根据 ModelConfig 动态创建对应的 ChatClient

**核心逻辑**:
```java
@Component
public class DynamicChatClientFactory {
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public ChatClient createChatClient(Long modelConfigId) {
        // 1. 查询模型配置
        ModelConfig config = modelConfigService.getById(modelConfigId);
        
        // 2. 根据配置选择 ChatClient Builder
        ChatClient.Builder builder;
        if (config.getBaseUrl().contains("aliyuncs.com")) {
            // 阿里云
            builder = getAliyunBuilder(config);
        } else if (config.getBaseUrl().contains("deepseek.com")) {
            // DeepSeek
            builder = getDeepSeekBuilder(config);
        } else {
            // 默认 OpenAI
            builder = getOpenAiBuilder(config);
        }
        
        // 3. 构建 ChatClient
        return builder.build();
    }
}
```

### 2.3 MCP 工具集成模块

#### 2.3.1 McpClientManager

**职责**: 管理所有 MCP 客户端的生命周期

**核心方法**:
```java
@Component
public class McpClientManager {
    
    private final Map<Long, McpClient> clients = new ConcurrentHashMap<>();
    
    // 获取或初始化客户端
    public McpClient getOrInitializeClient(McpServer server) {
        return clients.computeIfAbsent(
            server.getId(),
            id -> initializeClient(server)
        );
    }
    
    // 重新连接客户端
    public void reconnectClient(Long serverId) {
        destroyClient(serverId);
        McpServer server = mcpServerService.getServer(serverId);
        if (server != null && server.getEnabled()) {
            initializeClient(server);
        }
    }
    
    // 销毁客户端
    public void destroyClient(Long serverId) {
        McpClient client = clients.remove(serverId);
        if (client != null) {
            client.close();
        }
    }
}
```

#### 2.3.2 McpToolRegistry

**职责**: 注册和管理所有 MCP 工具

**核心数据结构**:
```java
@Component
public class McpToolRegistry {
    
    // 工具映射：serverId:toolName -> ToolCallback
    private final Map<String, McpToolCallback> tools = new ConcurrentHashMap<>();
    
    // 注册工具
    public void registerTool(Long serverId, Tool tool, McpClient client) {
        String toolKey = serverId + ":" + tool.getName();
        McpToolCallback callback = new McpToolCallback(
            serverId, 
            tool, 
            client
        );
        tools.put(toolKey, callback);
    }
    
    // 根据服务器 ID 获取工具
    public List<ToolCallback> getToolCallbacksForServers(List<Long> serverIds) {
        return tools.values().stream()
            .filter(callback -> serverIds.contains(callback.getServerId()))
            .collect(Collectors.toList());
    }
}
```

#### 2.3.3 StdioMcpClient

**职责**: 通过 STDIO 协议与 MCP 服务器通信

**核心流程**:
```java
public class StdioMcpClient implements McpClient {
    
    private Process process;
    private BufferedReader reader;
    private PrintWriter writer;
    
    public void connect() throws IOException {
        // 1. 启动子进程
        process = new ProcessBuilder(command, args)
            .redirectErrorStream(true)
            .start();
        
        // 2. 获取输入输出流
        reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        writer = new PrintWriter(process.getOutputStream());
        
        // 3. 初始化 MCP 会话
        initialize();
        
        // 4. 获取工具列表
        listTools();
    }
    
    public JsonNode callTool(String toolName, Map<String, Object> arguments) {
        // 1. 构建 JSON-RPC 请求
        JsonObject request = buildRequest(toolName, arguments);
        
        // 2. 发送请求
        sendRequest(request);
        
        // 3. 等待响应
        return waitForResponse();
    }
}
```

### 2.4 RAG 知识库模块

#### 2.4.1 RagService

**职责**: 提供 RAG 相关服务

**核心方法**:
```java
@Service
public class RagService {
    
    @Autowired
    private VectorStore vectorStore;
    
    @Autowired
    private EmbeddingModel embeddingModel;
    
    // 构建知识上下文
    public String buildKnowledgeContext(String query, List<Long> knowledgeBaseIds) {
        // 1. 生成查询向量
        float[] queryVector = embeddingModel.embed(query);
        
        // 2. 相似度搜索
        List<Document> documents = vectorStore.similaritySearch(
            queryVector,
            knowledgeBaseIds,
            5  // Top K
        );
        
        // 3. 构建上下文
        return documents.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));
    }
    
    // 添加文档到知识库
    public void addDocument(Long knowledgeBaseId, Document document) {
        // 1. 生成文档向量
        float[] vector = embeddingModel.embed(document.getContent());
        
        // 2. 存储到向量数据库
        vectorStore.add(vector, document, knowledgeBaseId);
    }
}
```

## 3. 数据库设计

### 3.1 实体关系图

```
┌─────────────────┐       ┌─────────────────┐
│  ModelConfig    │       │  AgentConfig    │
├─────────────────┤       ├─────────────────┤
│ id              │◄──────│ model_config_id │
│ model_name      │       │ id              │
│ base_url        │       │ name            │
│ api_key         │       │ system_prompt   │
│ temperature     │       └─────────────────┘
│ max_tokens      │
└─────────────────┘
         │
         │
┌────────▼────────┐       ┌─────────────────┐
│  AgentTool      │       │  McpServer      │
├─────────────────┤       ├─────────────────┤
│ agent_config_id │       │ id              │
│ tool_name       │       │ name            │
│ enabled         │       │ server_type     │
└─────────────────┘       │ config          │
                          └─────────────────┘
```

### 3.2 核心实体类

#### ModelConfig
```java
@Data
@TableName("model_config")
public class ModelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String modelName;
    private String baseUrl;
    private String apiKey;
    private BigDecimal temperature;
    private Integer maxTokens;
    private LocalDateTime createdAt;
}
```

#### AgentConfig
```java
@Data
@TableName("agent_config")
public class AgentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String name;
    private Long modelConfigId;
    private String systemPrompt;
    private Boolean enabled;
}
```

## 4. 配置说明

### 4.1 application.yml

```yaml
spring:
  ai:
    chat:
      memory:
        repository:
          jdbc:
            initialize-schema: always
          redis:
            host: localhost
            port: 6379
            time-to-live: 24h
    openai:
      base-url: https://dashscope.aliyuncs.com
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: qwen-flash
    embedding:
      base-url: http://localhost:11434
      model: nomic-embed-text
    vectorstore:
      milvus:
        client:
          host: localhost
          port: 19530
        collection-name: memory_vectors
```

### 4.2 关键配置项

**Chat Memory 配置**:
- `spring.ai.chat.memory.repository.redis.host`: Redis 主机
- `spring.ai.chat.memory.repository.redis.time-to-live`: 记忆存活时间

**向量数据库配置**:
- `spring.ai.vectorstore.milvus.client.host`: Milvus 主机
- `spring.ai.vectorstore.milvus.collection-name`: 集合名称

## 5. 开发指南

### 5.1 创建新的 Agent

1. 继承 `AbstractAgent` 类
2. 实现 `execute()` 方法
3. 在 `AgentFactory` 中注册

### 5.2 添加新的 MCP 工具

1. 在数据库中添加 MCP 服务器配置
2. 系统自动发现和注册工具
3. 在 Agent 中配置使用

### 5.3 添加新的 AI 模型

1. 在 `model_config` 表中添加配置
2. 在 `DynamicChatClientFactory` 中添加 Builder
3. 测试验证

## 6. 测试指南

### 6.1 单元测试

```java
@SpringBootTest
class SimpleAgentTest {
    
    @Autowired
    private AgentFactory agentFactory;
    
    @Test
    void testExecute() {
        AgentConfig config = new AgentConfig();
        config.setModelConfigId(1L);
        
        AbstractAgent agent = agentFactory.createAgent(config);
        
        AgentRequest request = new AgentRequest("你好");
        AgentResponse response = agent.execute(request);
        
        assertNotNull(response.getMessage());
    }
}
```

### 6.2 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testChat() throws Exception {
        mockMvc.perform(post("/api/v1/agent/1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"你好\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }
}
```

## 7. 性能优化

### 7.1 数据库优化

- 使用连接池（HikariCP）
- 配置合适的连接数
- 添加必要的索引

### 7.2 缓存优化

- Redis 缓存热点数据
- 配置合理的 TTL
- 使用本地缓存（Caffeine）

### 7.3 异步处理

- 使用 `@Async` 异步执行
- 消息队列解耦
- 批量操作

## 8. 常见问题

### Q: 如何调试 Agent 执行流程？

A: 开启 DEBUG 日志，查看 `AbstractAgent` 的执行日志。

### Q: MCP 工具调用失败怎么办？

A: 检查 MCP 服务器状态，查看 `McpClientManager` 日志。

### Q: 向量搜索效果不好？

A: 调整向量模型、相似度阈值和 Top K 参数。

---

**文档版本**: v1.0  
**最后更新**: 2026-03-23  
**维护者**: AI Agent Team
