<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-content">
        <div class="logo-section">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="20" cy="20" r="18" stroke="url(#gradient1)" stroke-width="2"/>
              <path d="M12 20L18 26L28 14" stroke="url(#gradient1)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="gradient1" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#6366f1"/>
                  <stop offset="1" stop-color="#8b5cf6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span class="logo-text">AI Agent</span>
        </div>
        <el-menu mode="horizontal" :default-active="activeTab" @select="handleTabChange" class="main-menu">
          <el-menu-item index="0">Agent管理</el-menu-item>
          <el-menu-item index="1">模型配置</el-menu-item>
          <el-menu-item index="2">任务管理</el-menu-item>
          <el-menu-item index="3">记忆管理</el-menu-item>
          <el-menu-item index="4">RAG知识库</el-menu-item>
          <el-menu-item index="5">MCP服务器</el-menu-item>
          <el-menu-item index="6">知识图谱</el-menu-item>
        </el-menu>
      </div>
    </el-header>
    
    <el-main class="app-main">
      <div class="content-wrapper">
        <ChatAssistant v-if="activeTab === '0'" />
        
        <el-card v-if="activeTab === '1'" class="management-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">模型配置</span>
            </div>
          </template>
          <p class="description">配置大模型参数，支持多套配置并设置默认使用</p>

          <el-form ref="modelFormRef" :model="modelForm" label-width="100px" style="max-width: 600px">
            <el-form-item label="配置名称" prop="name" :rules="[{ required: true, message: '请输入配置名称', trigger: 'blur' }]">
              <el-input v-model="modelForm.name" placeholder="例如：我的GPT-4配置" class="styled-input" />
            </el-form-item>
            <el-form-item label="模型提供商" prop="provider" :rules="[{ required: true, message: '请选择模型提供商', trigger: 'change' }]">
              <el-select v-model="modelForm.provider" placeholder="请选择模型提供商" style="width: 100%" class="styled-input">
                <el-option label="OpenAI" value="openai" />
                <el-option label="Claude" value="claude" />
                <el-option label="通义千问" value="qwen" />
                <el-option label="Gemini" value="gemini" />
                <el-option label="自定义" value="custom" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型名称" prop="modelName" :rules="[{ required: true, message: '请输入模型名称', trigger: 'blur' }]">
              <el-input v-model="modelForm.modelName" placeholder="例如：gpt-4、qwen-plus" class="styled-input" />
            </el-form-item>
            <el-form-item label="API地址" prop="baseUrl">
              <el-input v-model="modelForm.baseUrl" placeholder="例如：https://api.openai.com/v1" class="styled-input" />
            </el-form-item>
            <el-form-item label="API密钥" prop="apiKey">
              <el-input v-model="modelForm.apiKey" type="password" placeholder="输入API密钥" class="styled-input" show-password />
            </el-form-item>
            <el-form-item label="温度参数">
              <el-slider v-model="modelForm.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="最大令牌数">
              <el-input-number v-model="modelForm.maxTokens" :min="100" :max="128000" :step="100" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="gradient-btn" @click="handleSaveModelConfig" :loading="savingModelConfig">
                {{ editingModelConfigId ? '更新配置' : '保存配置' }}
              </el-button>
              <el-button v-if="editingModelConfigId" @click="cancelEditModelConfig">取消编辑</el-button>
            </el-form-item>
          </el-form>

          <div class="list-section">
            <h3>已保存的模型配置</h3>
            <el-table :data="modelConfigs" border class="styled-table" v-loading="loadingModelConfigs">
              <el-table-column prop="name" label="配置名称" />
              <el-table-column prop="provider" label="提供商">
                <template #default="{ row }">
                  {{ getProviderName(row.provider) }}
                </template>
              </el-table-column>
              <el-table-column prop="modelName" label="模型名称" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag v-if="row.isDefault === 1" type="success" class="custom-tag success">默认模型</el-tag>
                  <el-tag v-else type="info" class="custom-tag">备用模型</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button type="primary" link class="link-btn" @click="editModelConfig(row)">编辑</el-button>
                  <el-button v-if="row.isDefault !== 1" type="success" link class="link-btn" @click="setDefaultModelConfig(row.id)">设为默认</el-button>
                  <el-button type="danger" link class="link-btn danger" @click="deleteModelConfig(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
        
        <el-card v-if="activeTab === '2'" class="management-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">任务管理</span>
            </div>
          </template>
          <p class="description">管理Agent执行的任务，查看任务状态和结果</p>

          <el-button type="primary" style="margin-bottom: 20px" class="gradient-btn">创建新任务</el-button>

          <el-table :data="mockTasks" border class="styled-table">
            <el-table-column prop="id" label="任务ID" width="120" />
            <el-table-column prop="name" label="任务名称" />
            <el-table-column prop="agentType" label="Agent类型" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" class="custom-tag">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button type="primary" link class="link-btn">查看</el-button>
                <el-button type="danger" link class="link-btn danger">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="activeTab === '3'" class="management-card">
          <MemoryManagement />
        </el-card>

        <el-card v-if="activeTab === '4'" class="management-card">
          <RagManagement />
        </el-card>

        <el-card v-if="activeTab === '5'" class="management-card">
          <McpServerManagement />
        </el-card>

        <el-card v-if="activeTab === '6'" class="management-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">知识图谱</span>
            </div>
          </template>
          <p class="description">可视化管理和探索知识图谱，查看概念和关系</p>
          <KnowledgeGraphVisualization />
        </el-card>
      </div>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { modelConfigApi } from './services/api'
import MemoryManagement from './components/MemoryManagement.vue'
import RagManagement from './components/RagManagement.vue'
import McpServerManagement from './components/McpServerManagement.vue'
import ChatAssistant from './components/ChatAssistant.vue'
import KnowledgeGraphVisualization from './components/KnowledgeGraphVisualization.vue'

interface ModelConfig {
  id?: string
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

const activeTab = ref('0')
const modelConfigs = ref<ModelConfig[]>([])
const modelFormRef = ref()
const loadingModelConfigs = ref(false)
const savingModelConfig = ref(false)
const editingModelConfigId = ref<string | null>(null)

const modelForm = reactive<ModelConfig>({
  name: '',
  provider: '',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  temperature: 0.7,
  maxTokens: 2000
})

const providerNames: Record<string, string> = {
  'openai': 'OpenAI',
  'claude': 'Claude',
  'qwen': '通义千问',
  'gemini': 'Gemini',
  'custom': '自定义'
}

function getProviderName(provider?: string): string {
  if (!provider) return '未知'
  return providerNames[provider] || provider
}

async function loadModelConfigs() {
  loadingModelConfigs.value = true
  try {
    modelConfigs.value = await modelConfigApi.getAllConfigs()
  } catch (error) {
    console.error('Failed to load model configs:', error)
    ElMessage.error('加载模型配置失败')
  } finally {
    loadingModelConfigs.value = false
  }
}

function resetModelForm() {
  modelForm.name = ''
  modelForm.provider = ''
  modelForm.modelName = ''
  modelForm.baseUrl = ''
  modelForm.apiKey = ''
  modelForm.temperature = 0.7
  modelForm.maxTokens = 2000
  editingModelConfigId.value = null
}

function editModelConfig(config: ModelConfig) {
  editingModelConfigId.value = config.id || null
  modelForm.name = config.name
  modelForm.provider = config.provider
  modelForm.modelName = config.modelName
  modelForm.baseUrl = config.baseUrl || ''
  modelForm.apiKey = config.apiKey || ''
  modelForm.temperature = config.temperature || 0.7
  modelForm.maxTokens = config.maxTokens || 2000
}

function cancelEditModelConfig() {
  resetModelForm()
}

async function handleSaveModelConfig() {
  if (!modelFormRef.value) return

  try {
    await modelFormRef.value.validate()
  } catch {
    return
  }

  savingModelConfig.value = true
  try {
    if (editingModelConfigId.value) {
      await modelConfigApi.updateConfig(editingModelConfigId.value, { ...modelForm })
      ElMessage.success('更新成功')
    } else {
      await modelConfigApi.createConfig({ ...modelForm })
      ElMessage.success('创建成功')
    }
    resetModelForm()
    await loadModelConfigs()
  } catch (error) {
    console.error('Failed to save model config:', error)
    ElMessage.error('保存失败')
  } finally {
    savingModelConfig.value = false
  }
}

async function setDefaultModelConfig(id: string) {
  try {
    await modelConfigApi.setDefaultConfig(id)
    ElMessage.success('设置默认配置成功')
    await loadModelConfigs()
  } catch (error) {
    console.error('Failed to set default config:', error)
    ElMessage.error('设置默认配置失败')
  }
}

async function deleteModelConfig(id: string) {
  try {
    await ElMessageBox.confirm('确定要删除这个模型配置吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await modelConfigApi.deleteConfig(id)
    ElMessage.success('删除成功')
    await loadModelConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to delete model config:', error)
      ElMessage.error('删除失败')
    }
  }
}

const mockTasks = ref([
  { id: 'TASK-001', name: '回答用户问题', agentType: 'SimpleAgent', status: '已完成', createTime: '2026-03-12 10:00' },
  { id: 'TASK-002', name: '解决复杂问题', agentType: 'ReActAgent', status: '执行中', createTime: '2026-03-12 10:30' },
  { id: 'TASK-003', name: '生成工作计划', agentType: 'PlanAndSolveAgent', status: '等待中', createTime: '2026-03-12 11:00' },
])

function handleTabChange(index: string) {
  activeTab.value = index
  if (index === '1') {
    loadModelConfigs()
  }
}

function getStatusType(status: string): string {
  switch (status) {
    case '已完成': return 'success'
    case '执行中': return 'primary'
    case '等待中': return 'warning'
    default: return 'info'
  }
}
</script>

<style scoped>
.app-container {
  height: 100vh;
  position: relative;
  z-index: 1;
}

.app-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-color);
  padding: 0;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow-soft);
}

.header-content {
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 40px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.main-menu {
  flex: 1;
  background: transparent;
  border: none;
}

.main-menu :deep(.el-menu-item) {
  color: var(--text-primary);
  transition: all 0.3s ease;
  border-bottom: 2px solid transparent;
  margin: 0 4px;
}

.main-menu :deep(.el-menu-item:hover) {
  color: var(--text-primary);
  background: transparent;
}

.main-menu :deep(.el-menu-item.is-active) {
  color: var(--accent-primary);
  border-bottom-color: var(--accent-primary);
  background: transparent;
}

.app-main {
  padding: 0;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-wrapper {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 24px;
  box-sizing: border-box;
}

.management-card {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.management-card :deep(.el-card__header) {
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  padding: 16px 24px;
}

.management-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.management-card .description {
  color: var(--text-muted);
  margin-bottom: 24px;
  font-size: 14px;
}

.form-section {
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  background: var(--bg-tertiary);
}

.form-section h3 {
  margin-bottom: 20px;
  color: var(--text-primary);
  font-size: 16px;
}

.list-section {
  margin-top: 24px;
}

.list-section h3 {
  margin-bottom: 16px;
  color: var(--text-primary);
  font-size: 16px;
}

.styled-input :deep(.el-input__wrapper),
.styled-input :deep(.el-textarea__inner) {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  box-shadow: none;
  transition: all 0.3s ease;
}

.styled-input :deep(.el-input__wrapper:hover),
.styled-input :deep(.el-textarea__inner:hover) {
  border-color: var(--border-light);
}

.styled-input :deep(.el-input__wrapper.is-focus),
.styled-input :deep(.el-textarea__inner:focus) {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}

.gradient-btn {
  background: var(--gradient-primary);
  border: none;
  color: #fff;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.gradient-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, transparent 50%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.gradient-btn:hover::before {
  opacity: 1;
}

.gradient-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.4);
}

.secondary-btn {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.secondary-btn:hover {
  background: var(--bg-hover);
  border-color: var(--border-light);
  color: var(--text-primary);
}

.custom-tag {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.custom-tag.success {
  background: rgba(16, 185, 129, 0.15);
  border-color: var(--accent-success);
  color: var(--accent-success);
}

.styled-table {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.styled-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.styled-table :deep(.el-table__header-wrapper th) {
  background: var(--bg-card);
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
}

.styled-table :deep(.el-table__body-wrapper tr) {
  background: transparent;
}

.styled-table :deep(.el-table__body-wrapper td) {
  border-bottom: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.styled-table :deep(.el-table__body-wrapper tr:hover td) {
  background: var(--bg-hover);
}

.link-btn {
  color: var(--accent-primary);
}

.link-btn.danger {
  color: var(--accent-danger);
}
</style>
