<template>
  <div class="chat-assistant">
    <el-container class="main-container">
      <el-aside width="280px" class="sidebar">
        <div class="agents-section">
          <el-button type="primary" class="create-agent-btn" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            智能体助手
          </el-button>

          <div class="agent-list">
            <div
              :class="['agent-item', { active: isQuickChatMode }]"
              @click="switchToQuickChat()"
            >
              <div class="agent-icon quick-chat-icon">
                <span>💬</span>
              </div>
              <div class="agent-info">
                <div class="agent-name">快速对话</div>
                <div class="agent-desc">简单聊天助手</div>
              </div>
            </div>
            
            <el-divider style="margin: 12px 0;" />

            <div
              v-for="agent in agentConfigs"
              :key="agent.id"
              :class="['agent-item', { active: selectedAgentConfig?.id === agent.id && !isQuickChatMode }]"
              @click="selectAgent(agent)"
            >
              <div class="agent-icon">
                <span>{{ getAgentIcon(agent.baseAgentType) }}</span>
              </div>
              <div class="agent-info">
                <div class="agent-name">{{ agent.name }}</div>
                <div class="agent-desc">{{ getBaseAgentTypeName(agent.baseAgentType) }}</div>
              </div>
              <div class="agent-actions">
                <el-dropdown @command="(cmd: string) => handleAgentCommand(cmd, agent)" trigger="click">
                  <el-button type="text" size="small" @click.stop>
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>

          <div v-if="agentConfigs.length === 0" class="empty-agents">
            <div class="empty-text">暂无智能体</div>
            <div class="empty-hint">点击上方按钮添加</div>
          </div>
        </div>
      </el-aside>

      <el-main class="main-content">
        <div class="chat-section">
          <div class="chat-header">
            <div class="current-agent">
              <span class="agent-icon">{{ isQuickChatMode ? '💬' : getAgentIcon(selectedAgentConfig?.baseAgentType) }}</span>
              <span class="agent-name">{{ isQuickChatMode ? '快速对话' : selectedAgentConfig?.name }}</span>
            </div>
          </div>

          <div class="message-list" ref="messageListRef">
            <div
              v-for="message in messages"
              :key="message.id"
              :class="['message-item', message.sender]"
            >
              <div class="message-avatar">
                <span v-if="message.sender === 'assistant'">{{ isQuickChatMode ? '💬' : getAgentIcon(selectedAgentConfig?.baseAgentType) }}</span>
                <span v-else>👤</span>
              </div>
              <div class="message-content-wrapper">
                <div class="message-content">
                  <div v-if="message.sender === 'assistant'" v-html="renderMarkdown(message.content)"></div>
                  <div v-else>{{ message.content }}</div>
                </div>
                <el-button
                  class="copy-btn"
                  circle
                  size="small"
                  @click="copyMessage(message)"
                >
                  <el-icon>
                    <Check v-if="copiedMessageId === message.id" />
                    <DocumentCopy v-else />
                  </el-icon>
                </el-button>
              </div>
            </div>
            <div v-if="isLoading" class="message-item assistant">
              <div class="message-avatar">{{ isQuickChatMode ? '💬' : getAgentIcon(selectedAgentConfig?.baseAgentType) }}</div>
              <div class="message-content-wrapper">
                <div class="message-content loading">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="input-area">
          <el-input
            v-model="inputValue"
            placeholder="请输入消息..."
            @keyup.enter="sendMessage"
            class="styled-input"
            type="textarea"
            :rows="2"
            resize="none"
          />
          <el-button type="primary" @click="sendMessage" :disabled="isLoading" class="send-btn">
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </el-main>
    </el-container>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑智能体助手' : '智能体助手'"
      width="800px"
      class="agent-dialog"
    >
      <el-row :gutter="20">
        <el-col :span="6">
          <el-menu :default-active="configTab" class="config-tabs">
            <el-menu-item index="basic" @click="configTab = 'basic'">基础设置</el-menu-item>
            <el-menu-item index="model" @click="configTab = 'model'">模型设置</el-menu-item>
            <el-menu-item index="knowledge" @click="configTab = 'knowledge'">知识库设置</el-menu-item>
            <el-menu-item index="tools" @click="configTab = 'tools'">工具调用</el-menu-item>
          </el-menu>
        </el-col>
        <el-col :span="18">
          <div class="config-content">
            <div v-if="configTab === 'basic'" class="tab-content">
              <el-form :model="formData" label-width="120px">
                <el-form-item label="智能体名称" required>
                  <el-input v-model="formData.name" placeholder="请输入智能体名称" />
                </el-form-item>
                <el-form-item label="描述">
                  <el-input
                    v-model="formData.description"
                    type="textarea"
                    :rows="4"
                    placeholder="请输入智能体描述"
                  />
                </el-form-item>
                <el-form-item label="基础智能体" required>
                  <el-select v-model="formData.baseAgentType" placeholder="请选择基础智能体" style="width: 100%">
                    <el-option label="SimpleAgent - 简单聊天助手" value="SimpleAgent" />
                    <el-option label="ReActAgent - 推理行动助手" value="ReActAgent" />
                    <el-option label="ReflectionAgent - 反思助手" value="ReflectionAgent" />
                    <el-option label="PlanAndSolveAgent - 规划解决助手" value="PlanAndSolveAgent" />
                    <el-option label="FunctionCallAgent - 函数调用助手" value="FunctionCallAgent" />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>

            <div v-if="configTab === 'model'" class="tab-content">
              <el-form :model="formData" label-width="120px">
                <el-form-item label="选择模型" required>
                  <el-select v-model="formData.modelConfigId" placeholder="请选择模型" style="width: 100%">
                    <el-option
                      v-for="config in modelConfigs"
                      :key="config.id"
                      :label="`${config.name} (${config.modelName})`"
                      :value="config.id"
                    />
                  </el-select>
                </el-form-item>
                <div v-if="modelConfigs.length === 0" class="empty-hint">
                  <el-alert type="warning" :closable="false" description="暂无模型配置，请先在模型配置页面添加" />
                </div>
                <el-divider content-position="left">模型参数</el-divider>
                <el-form-item label="Temperature">
                  <div class="slider-wrapper">
                    <el-slider
                      v-model="formData.temperature"
                      :min="0"
                      :max="2"
                      :step="0.1"
                      show-input
                      style="width: 100%"
                    />
                  </div>
                </el-form-item>
              </el-form>
            </div>

            <div v-if="configTab === 'knowledge'" class="tab-content">
              <el-form :model="formData" label-width="120px">
                <el-form-item label="关联知识库">
                  <el-select v-model="formData.knowledgeBaseId" placeholder="请选择知识库" style="width: 100%" clearable>
                    <el-option
                      v-for="kb in knowledgeBases"
                      :key="kb.id"
                      :label="kb.name"
                      :value="kb.id"
                    />
                  </el-select>
                </el-form-item>
                <div v-if="knowledgeBases.length === 0" class="empty-hint">
                  <el-empty description="暂无知识库，请先创建知识库" />
                </div>
              </el-form>
            </div>

            <div v-if="configTab === 'tools'" class="tab-content">
              <el-form :model="formData" label-width="120px">
                <el-form-item label="选择MCP服务器">
                  <el-select
                    v-model="selectedServers"
                    multiple
                    placeholder="请选择MCP服务器"
                    style="width: 100%"
                    @change="updateServersString"
                  >
                    <el-option
                      v-for="server in mcpServers"
                      :key="server.id"
                      :label="server.name"
                      :value="server.id"
                    >
                      <div class="server-option">
                        <span class="server-name">{{ server.name }}</span>
                        <span v-if="server.description" class="server-desc">{{ server.description }}</span>
                      </div>
                    </el-option>
                  </el-select>
                </el-form-item>
                <div v-if="mcpServers.length === 0" class="empty-hint">
                  <el-alert type="warning" :closable="false" description="暂无已启用的MCP服务器，请先在MCP服务器页面配置并启用" />
                </div>
                <div class="tools-hint">
                  <el-alert
                    title="提示"
                    type="info"
                    :closable="false"
                    description="选择MCP服务器后，智能体将可以使用该服务器提供的所有工具"
                  />
                </div>
              </el-form>
            </div>
          </div>
        </el-col>
      </el-row>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MoreFilled, Check, DocumentCopy, Promotion, Loading } from '@element-plus/icons-vue'
import { agentConfigApi, agentApi, knowledgeBaseApi, chatApi, modelConfigApi, mcpApi } from '../services/api'
import 'highlight.js/styles/github.css'

const renderer = new marked.Renderer()
renderer.code = function({ text, lang }: { text: string; lang?: string }) {
  let highlighted: string
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(text, { language: lang }).value
    } catch (err) {
      highlighted = hljs.highlightAuto(text).value
    }
  } else {
    highlighted = hljs.highlightAuto(text).value
  }
  return `<pre><code class="hljs">${highlighted}</code></pre>`
}

marked.setOptions({
  renderer: renderer,
  breaks: true,
  gfm: true
})

interface Message {
  id: string
  content: string
  sender: 'user' | 'assistant'
}

interface AgentConfig {
  id: string
  name: string
  description?: string
  baseAgentType: string
  modelName?: string
  modelConfigId?: string
  temperature: number
  knowledgeBaseId?: string
  tools?: string
  createdAt: string
  updatedAt: string
}

interface KnowledgeBase {
  id: string
  name: string
  description?: string
}

interface ModelConfig {
  id: string
  name: string
  provider: string
  modelName: string
  baseUrl?: string
  apiKey?: string
  temperature?: number
  maxTokens?: number
  isDefault?: number
  status?: number
}

interface McpServer {
  id: string
  name: string
  description?: string
  serverType?: string
  enabled?: boolean
}

const configTab = ref('basic')
const dialogVisible = ref(false)
const isEditing = ref(false)
const agentConfigs = ref<AgentConfig[]>([])
const knowledgeBases = ref<KnowledgeBase[]>([])
const modelConfigs = ref<ModelConfig[]>([])
const mcpServers = ref<McpServer[]>([])
const selectedAgentConfig = ref<AgentConfig | null>(null)
const messages = ref<Message[]>([])
const inputValue = ref('')
const isLoading = ref(false)
const copiedMessageId = ref<string | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const isQuickChatMode = ref(true)
const quickChatSessionId = ref('global_quick_chat_session')
const selectedServers = ref<string[]>([])

const formData = ref<Partial<AgentConfig>>({
  name: '',
  description: '',
  baseAgentType: '',
  modelConfigId: undefined,
  temperature: 0.7,
  knowledgeBaseId: undefined,
  tools: ''
})

const baseAgentTypeNames: Record<string, string> = {
  'SimpleAgent': '简单聊天助手',
  'ReActAgent': '推理行动助手',
  'ReflectionAgent': '反思助手',
  'PlanAndSolveAgent': '规划解决助手',
  'FunctionCallAgent': '函数调用助手'
}

const agentIcons: Record<string, string> = {
  'SimpleAgent': '💬',
  'ReActAgent': '🧠',
  'ReflectionAgent': '🤔',
  'PlanAndSolveAgent': '📋',
  'FunctionCallAgent': '🔧'
}

function getBaseAgentTypeName(type?: string): string {
  if (!type) return '未知类型'
  return baseAgentTypeNames[type] || type
}

function getAgentIcon(type?: string): string {
  if (!type) return '🤖'
  return agentIcons[type] || '🤖'
}

async function loadAgentConfigs() {
  try {
    const data = await agentConfigApi.getAllAgentConfigs()
    agentConfigs.value = Array.isArray(data) ? data : []
    
    if (!selectedAgentConfig.value && messages.value.length === 0) {
      switchToQuickChat()
    }
  } catch (error) {
    console.error('Failed to load agent configs:', error)
    agentConfigs.value = []
  }
}

async function loadKnowledgeBases() {
  try {
    const data = await knowledgeBaseApi.getAllKnowledgeBases()
    knowledgeBases.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('Failed to load knowledge bases:', error)
    knowledgeBases.value = []
  }
}

async function loadModelConfigs() {
  try {
    const data = await modelConfigApi.getAllConfigs()
    // 强制将 ID 转换为字符串，避免 JavaScript 精度丢失
    modelConfigs.value = Array.isArray(data) ? data.map(mc => ({
      ...mc,
      id: mc.id.toString()
    })) : []
    console.log('Model configs loaded with string IDs:', modelConfigs.value)
  } catch (error) {
    console.error('Failed to load model configs:', error)
    modelConfigs.value = []
  }
}

async function loadMcpServers() {
  try {
    const data = await mcpApi.getEnabledMcpServers()
    mcpServers.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('Failed to load MCP servers:', error)
    mcpServers.value = []
  }
}

function selectAgent(agent: AgentConfig) {
  selectedAgentConfig.value = agent
  isQuickChatMode.value = false
  messages.value = [
    { id: '1', content: `你好！我是 ${agent.name}，有什么可以帮助你的吗？`, sender: 'assistant' }
  ]
}

function switchToQuickChat() {
  selectedAgentConfig.value = null
  isQuickChatMode.value = true
  if (messages.value.length === 0) {
    messages.value = [
      { id: '1', content: '你好！我是快速对话助手，有什么可以帮助你的吗？', sender: 'assistant' }
    ]
  }
}

function showCreateDialog() {
  isEditing.value = false
  configTab.value = 'basic'
  selectedServers.value = []
  formData.value = {
    name: '',
    description: '',
    baseAgentType: '',
    modelConfigId: undefined,
    temperature: 0.7,
    knowledgeBaseId: undefined,
    tools: ''
  }
  dialogVisible.value = true
}

function showEditDialog(agent: AgentConfig) {
  isEditing.value = true
  configTab.value = 'basic'
  selectedServers.value = agent.tools ? agent.tools.split(',').map((t: string) => t.trim()).filter((t: string) => t.length > 0) : []
  const { modelName, ...rest } = agent
  formData.value = rest
  dialogVisible.value = true
}

function updateServersString() {
  formData.value.tools = selectedServers.value.join(',')
}

async function handleSave() {
  if (!formData.value.name || !formData.value.baseAgentType || !formData.value.modelConfigId) {
    ElMessage.warning('请填写必填字段')
    return
  }

  try {
    const dataToSave = { ...formData.value }
    delete dataToSave.modelName
    
    if (isEditing.value && formData.value.id) {
      await agentConfigApi.updateAgentConfig(formData.value.id, dataToSave)
      ElMessage.success('更新成功')
    } else {
      const newAgent = await agentConfigApi.createAgentConfig(dataToSave)
      ElMessage.success('创建成功')
      selectAgent(newAgent)
    }
    dialogVisible.value = false
    await loadAgentConfigs()
  } catch (error) {
    console.error('Failed to save agent config:', error)
    ElMessage.error('保存失败')
  }
}

async function handleAgentCommand(command: string, agent: AgentConfig) {
  if (command === 'edit') {
    showEditDialog(agent)
  } else if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定要删除这个智能体吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await agentConfigApi.deleteAgentConfig(agent.id)
      ElMessage.success('删除成功')
      if (selectedAgentConfig.value?.id === agent.id) {
        selectedAgentConfig.value = null
      }
      await loadAgentConfigs()
    } catch (error) {
      if (error !== 'cancel') {
        console.error('Failed to delete agent config:', error)
        ElMessage.error('删除失败')
      }
    }
  }
}

function renderMarkdown(content: string): string {
  return marked(content) as string
}

async function sendMessage() {
  if (!inputValue.value.trim() || isLoading.value) return
  
  const userMessage: Message = {
    id: Date.now().toString(),
    content: inputValue.value,
    sender: 'user'
  }
  messages.value.push(userMessage)
  const currentInput = inputValue.value
  inputValue.value = ''
  isLoading.value = true
  
  try {
    let responseContent: string
    
    if (isQuickChatMode.value) {
      const response = await chatApi.chatWithSession(quickChatSessionId.value, currentInput)
      responseContent = response.message || response.content || ''
    } else if (selectedAgentConfig.value) {
      const response = await agentApi.processMessage(String(selectedAgentConfig.value.id), currentInput)
      responseContent = response.message || response.content || JSON.stringify(response)
    } else {
      throw new Error('No agent selected')
    }
    
    const assistantMessage: Message = {
      id: (Date.now() + 1).toString(),
      content: responseContent,
      sender: 'assistant'
    }
    messages.value.push(assistantMessage)
  } catch (error) {
    console.error('Failed to send message:', error)
    const errorMessage: Message = {
      id: (Date.now() + 1).toString(),
      content: '抱歉，发送消息失败，请稍后再试。',
      sender: 'assistant'
    }
    messages.value.push(errorMessage)
  } finally {
    isLoading.value = false
    nextTick(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
    })
  }
}

async function copyMessage(message: Message) {
  try {
    await navigator.clipboard.writeText(message.content)
    copiedMessageId.value = message.id
    ElMessage.success('复制成功！')
    setTimeout(() => {
      copiedMessageId.value = null
    }, 2000)
  } catch (error) {
    console.error('Failed to copy:', error)
  }
}

onMounted(async () => {
  await loadAgentConfigs()
  await loadKnowledgeBases()
  await loadModelConfigs()
  await loadMcpServers()
})
</script>

<style scoped>
.chat-assistant {
  height: 100%;
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.main-container {
  height: 100%;
}

.sidebar {
  background: #f7f8fa;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.agents-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px;
  overflow-y: auto;
}

.create-agent-btn {
  width: 100%;
  margin-bottom: 15px;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
}

.agent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.agent-item:hover {
  background: #eef1f6;
}

.agent-item.active {
  background: #6366f1;
  color: white;
}

.agent-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.quick-chat-icon {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: white;
}

.agent-item.active .agent-icon {
  background: rgba(255, 255, 255, 0.2);
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-desc {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-item.active .agent-desc {
  color: rgba(255, 255, 255, 0.8);
}

.agent-actions {
  flex-shrink: 0;
}

.empty-agents {
  text-align: center;
  padding: 40px 20px;
  color: #9ca3af;
}

.empty-text {
  margin-bottom: 4px;
  font-size: 14px;
}

.empty-hint {
  font-size: 12px;
}

.main-content {
  padding: 0;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.welcome-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  padding: 40px;
  overflow-y: auto;
}

.welcome-title {
  font-size: 32px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.welcome-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 48px;
}

.quick-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 500px;
  width: 100%;
}

.quick-card {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 16px;
}

.quick-card:hover {
  border-color: #6366f1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.1);
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.chat-icon {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.quick-icon {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.card-info {
  flex: 1;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.chat-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.chat-header {
  padding: 18px 28px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(180deg, #ffffff 0%, #fafbfc 100%);
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.current-agent {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-agent .agent-icon {
  width: 38px;
  height: 38px;
  font-size: 18px;
  background: linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);
  border-radius: 10px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.06);
}

.current-agent .agent-name {
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, #1f2937 0%, #4b5563 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 0;
}

.message-item {
  display: flex;
  gap: 12px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%);
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.message-content-wrapper {
  flex: 1;
  max-width: 75%;
  position: relative;
}

.message-content {
  padding: 16px 20px;
  border-radius: 18px;
  line-height: 1.7;
  font-size: 15px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.3s ease;
}

.message-content:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.message-item.assistant .message-content {
  background: linear-gradient(180deg, #ffffff 0%, #f9fafb 100%);
  color: #1f2937;
  border-top-left-radius: 4px;
  border: 1px solid #f0f0f0;
}

.message-item.user .message-content {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: white;
  border-top-right-radius: 4px;
  border: none;
}

.message-content.loading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
}

.copy-btn {
  position: absolute;
  bottom: -8px;
  right: -8px;
  opacity: 0;
  transition: all 0.2s;
  background: white;
  border: 1px solid #e5e7eb;
}

.message-content-wrapper:hover .copy-btn {
  opacity: 1;
}

.copy-btn:hover {
  background: #6366f1;
  border-color: #6366f1;
  color: white;
}

.input-area {
  padding: 20px 24px;
  border-top: 1px solid #f0f0f0;
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
  display: flex;
  gap: 16px;
  flex-shrink: 0;
  align-items: flex-end;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.03);
}

.input-area :deep(.el-textarea__inner) {
  border-radius: 16px;
  resize: none;
  border: 2px solid #e8e8e8;
  transition: all 0.3s ease;
  font-size: 15px;
  line-height: 1.6;
  padding: 14px 18px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.input-area :deep(.el-textarea__inner:focus) {
  border-color: #6366f1;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1), 0 4px 12px rgba(99, 102, 241, 0.08);
}

.send-btn {
  height: 56px;
  padding: 0 28px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
  background: linear-gradient(135deg, #5856ec 0%, #7c3aed 100%);
}

.send-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

.send-btn:disabled {
  opacity: 0.6;
  transform: none;
  cursor: not-allowed;
  box-shadow: none;
}

.agent-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.config-tabs {
  border: none;
  border-right: 1px solid #e5e7eb;
  height: 100%;
}

.config-tabs :deep(.el-menu-item) {
  border-radius: 8px;
  margin: 4px 8px;
}

.config-content {
  padding: 24px;
}

.tab-content {
  min-height: 400px;
}

.slider-wrapper {
  width: 100%;
}

.tools-hint {
  margin-top: 16px;
}

.server-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.server-name {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
}

.server-desc {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.message-content :deep(pre) {
  background: #1f2937 !important;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  margin: 10px 0;
}

.message-content :deep(pre code) {
  font-family: 'SF Mono', 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #e5e7eb;
}

.message-content :deep(code:not(pre code)) {
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'SF Mono', 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  color: #6366f1;
}

.message-content :deep(p) {
  margin: 6px 0;
}
</style>
