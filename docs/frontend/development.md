# 前端模块详细设计文档

## 1. 概述

### 1.1 技术栈

- **框架**: Vue 3.5.30
- **构建工具**: Vite 6.2.3
- **UI 组件库**: Element Plus 2.9.10
- **状态管理**: Pinia 3.0.1
- **路由**: Vue Router 4.5.0
- **HTTP 客户端**: Axios 1.8.4
- **TypeScript**: 5.8.2

### 1.2 项目结构

```
frontend/
├── src/
│   ├── main.ts                    # 应用入口
│   ├── App.vue                    # 根组件
│   ├── components/                # 公共组件
│   │   ├── ChatAssistant.vue      # 聊天助手组件
│   │   ├── McpServerManagement.vue # MCP 服务器管理
│   │   ├── AgentManagement.vue    # Agent 管理
│   │   ├── ModelConfigForm.vue    # 模型配置表单
│   │   └── KnowledgeBaseList.vue  # 知识库列表
│   ├── views/                     # 页面视图
│   │   ├── ChatView.vue           # 聊天页面
│   │   ├── AgentListView.vue      # Agent 列表页面
│   │   ├── McpServerView.vue      # MCP 服务器页面
│   │   └── SettingsView.vue       # 设置页面
│   ├── services/                  # API 服务
│   │   ├── api.ts                 # API 客户端
│   │   ├── agentService.ts        # Agent 服务
│   │   ├── mcpService.ts          # MCP 服务
│   │   └── ragService.ts          # RAG 服务
│   ├── stores/                    # 状态管理
│   │   ├── chatStore.ts           # 聊天状态
│   │   ├── agentStore.ts          # Agent 状态
│   │   └── mcpStore.ts            # MCP 状态
│   ├── router/                    # 路由配置
│   │   └── index.ts               # 路由定义
│   ├── utils/                     # 工具函数
│   │   ├── request.ts             # 请求封装
│   │   └── helpers.ts             # 辅助函数
│   ├── types/                     # 类型定义
│   │   └── index.ts               # 类型声明
│   └── assets/                    # 静态资源
│       ├── styles/                # 样式文件
│       └── images/                # 图片资源
├── nginx.conf                     # Nginx 配置
├── Dockerfile                     # Docker 构建
├── package.json                   # 依赖配置
└── vite.config.ts                 # Vite 配置
```

## 2. 核心组件设计

### 2.1 ChatAssistant 聊天助手组件

**职责**: 实现聊天界面的核心功能

**组件结构**:
```vue
<template>
  <div class="chat-assistant">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <h3>{{ agentName }}</h3>
    </div>
    
    <!-- 消息列表 -->
    <div class="message-list" ref="messageListRef">
      <div 
        v-for="message in messages" 
        :key="message.id"
        :class="['message', message.role]"
      >
        <div class="message-content">
          {{ message.content }}
        </div>
        <div class="message-time">
          {{ formatTime(message.timestamp) }}
        </div>
      </div>
    </div>
    
    <!-- 输入区域 -->
    <div class="input-area">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="3"
        placeholder="输入消息..."
        @keyup.enter="sendMessage"
      />
      <el-button type="primary" @click="sendMessage">
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useChatStore } from '@/stores/chatStore'

const chatStore = useChatStore()
const messages = computed(() => chatStore.messages)
const inputMessage = ref('')

const sendMessage = async () => {
  if (!inputMessage.value.trim()) return
  
  // 发送消息
  await chatStore.sendMessage(inputMessage.value)
  
  // 清空输入
  inputMessage.value = ''
  
  // 滚动到底部
  await nextTick()
  scrollToBottom()
}
</script>
```

**核心功能**:
- 消息展示
- 消息发送
- 自动滚动
- 加载状态
- 错误处理

### 2.2 McpServerManagement MCP 服务器管理

**职责**: MCP 服务器的配置和管理

**组件结构**:
```vue
<template>
  <el-card class="mcp-server-management">
    <template #header>
      <div class="card-header">
        <span class="card-title">MCP 服务器管理</span>
        <div class="header-buttons">
          <el-button type="success" @click="reconnectAllServers">
            重新连接所有
          </el-button>
          <el-button type="primary" @click="showAddDialog = true">
            添加服务器
          </el-button>
        </div>
      </div>
    </template>
    
    <!-- 服务器列表 -->
    <el-table :data="servers" style="width: 100%">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="serverType" label="类型" />
      <el-table-column prop="enabled" label="状态">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">
            {{ row.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            @click="reconnectServer(row.id)"
          >
            重连
          </el-button>
          <el-button
            type="info"
            size="small"
            @click="viewTools(row.id)"
          >
            查看工具
          </el-button>
          <el-button
            type="danger"
            size="small"
            @click="deleteServer(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMcpStore } from '@/stores/mcpStore'

const mcpStore = useMcpStore()
const servers = computed(() => mcpStore.servers)

onMounted(() => {
  mcpStore.loadServers()
})

const reconnectServer = async (id: number) => {
  await mcpStore.reconnectServer(id)
}

const viewTools = async (id: number) => {
  const tools = await mcpStore.getServerTools(id)
  // 显示工具列表
}
</script>
```

### 2.3 AgentManagement Agent 管理

**职责**: Agent 的创建、编辑和管理

**核心功能**:
- Agent 列表展示
- 创建新 Agent
- 编辑 Agent 配置
- 关联工具和知识库
- 启用/禁用 Agent

## 3. 状态管理

### 3.1 ChatStore 聊天状态

```typescript
import { defineStore } from 'pinia'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  toolCalls?: ToolCall[]
}

interface ChatState {
  messages: Message[]
  currentAgentId: number | null
  isLoading: boolean
  error: string | null
}

export const useChatStore = defineStore('chat', {
  state: (): ChatState => ({
    messages: [],
    currentAgentId: null,
    isLoading: false,
    error: null
  }),
  
  actions: {
    async sendMessage(content: string) {
      // 添加用户消息
      const userMessage: Message = {
        id: generateId(),
        role: 'user',
        content,
        timestamp: Date.now()
      }
      this.messages.push(userMessage)
      
      // 调用 API
      this.isLoading = true
      try {
        const response = await agentApi.chat(
          this.currentAgentId!,
          content
        )
        
        // 添加 AI 响应
        const aiMessage: Message = {
          id: generateId(),
          role: 'assistant',
          content: response.message,
          timestamp: Date.now(),
          toolCalls: response.toolCalls
        }
        this.messages.push(aiMessage)
      } catch (error) {
        this.error = error.message
      } finally {
        this.isLoading = false
      }
    },
    
    clearMessages() {
      this.messages = []
    }
  }
})
```

### 3.2 AgentStore Agent 状态

```typescript
export const useAgentStore = defineStore('agent', {
  state: () => ({
    agents: [],
    currentAgent: null,
    loading: false
  }),
  
  actions: {
    async loadAgents() {
      this.loading = true
      try {
        const response = await agentApi.listAgents()
        this.agents = response.data
      } finally {
        this.loading = false
      }
    },
    
    async createAgent(config: AgentConfig) {
      return await agentApi.createAgent(config)
    },
    
    async updateAgent(id: number, config: AgentConfig) {
      return await agentApi.updateAgent(id, config)
    },
    
    async deleteAgent(id: number) {
      return await agentApi.deleteAgent(id)
    }
  }
})
```

### 3.3 McpStore MCP 状态

```typescript
export const useMcpStore = defineStore('mcp', {
  state: () => ({
    servers: [],
    tools: [],
    loading: false
  }),
  
  actions: {
    async loadServers() {
      this.loading = true
      try {
        const response = await mcpApi.listServers()
        this.servers = response.data
      } finally {
        this.loading = false
      }
    },
    
    async reconnectServer(id: number) {
      return await mcpApi.reconnectServer(id)
    },
    
    async getServerTools(id: number) {
      return await mcpApi.getServerTools(id)
    }
  }
})
```

## 4. API 服务层

### 4.1 API 客户端封装

```typescript
// services/api.ts
import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 添加认证信息
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    // 错误处理
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // 未授权，跳转登录
          break
        case 403:
          // 无权限
          break
        case 404:
          // 资源不存在
          break
        case 500:
          // 服务器错误
          break
      }
    }
    return Promise.reject(error)
  }
)

export default api
```

### 4.2 Agent 服务

```typescript
// services/agentService.ts
import api from './api'

export const agentApi = {
  // 获取 Agent 列表
  listAgents() {
    return api.get('/agent/list')
  },
  
  // 创建 Agent
  createAgent(config: AgentConfig) {
    return api.post('/agent/create', config)
  },
  
  // 更新 Agent
  updateAgent(id: number, config: AgentConfig) {
    return api.put(`/agent/${id}`, config)
  },
  
  // 删除 Agent
  deleteAgent(id: number) {
    return api.delete(`/agent/${id}`)
  },
  
  // 聊天
  chat(agentId: number, message: string) {
    return api.post(`/agent/${agentId}/chat`, {
      message,
      conversationId: getCurrentConversationId()
    })
  }
}
```

### 4.3 MCP 服务

```typescript
// services/mcpService.ts
import api from './api'

export const mcpApi = {
  // 获取 MCP 服务器列表
  listServers() {
    return api.get('/mcp/servers')
  },
  
  // 创建 MCP 服务器
  createServer(config: McpServerConfig) {
    return api.post('/mcp/servers', config)
  },
  
  // 更新 MCP 服务器
  updateServer(id: number, config: McpServerConfig) {
    return api.put(`/mcp/servers/${id}`, config)
  },
  
  // 删除 MCP 服务器
  deleteServer(id: number) {
    return api.delete(`/mcp/servers/${id}`)
  },
  
  // 重新连接
  reconnectServer(id: number) {
    return api.post(`/mcp/servers/${id}/reconnect`)
  },
  
  // 获取服务器工具
  getServerTools(id: number) {
    return api.get(`/mcp/servers/${id}/tools`)
  }
}
```

## 5. 路由配置

```typescript
// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue')
  },
  {
    path: '/agents',
    name: 'Agents',
    component: () => import('@/views/AgentListView.vue')
  },
  {
    path: '/mcp-servers',
    name: 'McpServers',
    component: () => import('@/views/McpServerView.vue')
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

## 6. 样式设计

### 6.1 全局样式

```scss
// assets/styles/main.scss
$primary-color: #409EFF;
$success-color: #67C23A;
$danger-color: #F56C6C;
$warning-color: #E6A23C;

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.chat-assistant {
  display: flex;
  flex-direction: column;
  height: 100vh;
  
  .chat-header {
    padding: 20px;
    border-bottom: 1px solid #e6e6e6;
  }
  
  .message-list {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    min-height: 0;
    
    .message {
      margin-bottom: 20px;
      display: flex;
      flex-direction: column;
      
      &.user {
        align-items: flex-end;
      }
      
      &.assistant {
        align-items: flex-start;
      }
      
      .message-content {
        max-width: 70%;
        padding: 12px 16px;
        border-radius: 8px;
        line-height: 1.5;
      }
    }
  }
  
  .input-area {
    padding: 20px;
    border-top: 1px solid #e6e6e6;
    display: flex;
    gap: 12px;
  }
}
```

## 7. 构建配置

### 7.1 Vite 配置

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue': ['vue', 'vue-router', 'pinia']
        }
      }
    }
  }
})
```

### 7.2 Nginx 配置

```nginx
server {
    listen 80;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;

    # 缓存静态资源
    location ~* \.(js|css|png|jpg|jpeg|gif|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Vue Router 支持
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 代理 API 请求
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_connect_timeout 60s;
        proxy_read_timeout 120s;
    }
}
```

## 8. 开发指南

### 8.1 创建新组件

1. 在 `components` 目录创建 `.vue` 文件
2. 使用 `<script setup lang="ts">` 语法
3. 定义 Props 和 Emits
4. 导入并使用

### 8.2 添加新页面

1. 在 `views` 目录创建页面组件
2. 在 `router/index.ts` 添加路由
3. 在导航菜单中添加链接

### 8.3 添加新 API

1. 在 `services` 目录添加服务方法
2. 在对应的 Store 中添加 actions
3. 在组件中使用

## 9. 性能优化

### 9.1 代码分割

- 路由懒加载
- 组件异步加载
- 第三方库拆分

### 9.2 缓存优化

- 静态资源缓存
- API 响应缓存
- 组件缓存（keep-alive）

### 9.3 渲染优化

- 虚拟列表（大数据量）
- 防抖节流
- 避免不必要的渲染

## 10. 测试指南

### 10.1 单元测试

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChatAssistant from '@/components/ChatAssistant.vue'

describe('ChatAssistant', () => {
  it('renders properly', () => {
    const wrapper = mount(ChatAssistant)
    expect(wrapper.find('.chat-assistant').exists()).toBe(true)
  })
})
```

### 10.2 E2E 测试

使用 Playwright 或 Cypress 进行端到端测试。

---

**文档版本**: v1.0  
**最后更新**: 2026-03-23  
**维护者**: AI Agent Team
