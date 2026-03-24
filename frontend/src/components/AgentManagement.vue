<template>
  <div class="agent-management">
    <el-row :gutter="20" style="height: 100%">
      <el-col :span="6" class="sidebar">
        <div class="sidebar-header">
          <h3>我的智能体</h3>
          <el-button type="primary" @click="showCreateDialog" class="gradient-btn">
            <el-icon><Plus /></el-icon>
            新建智能体
          </el-button>
        </div>

        <div class="agent-list">
          <div
            v-for="agent in agentConfigs"
            :key="agent.id"
            :class="['agent-item', { active: selectedAgentConfig?.id === agent.id }]"
            @click="selectAgentConfig(agent)"
          >
            <div class="agent-info">
              <div class="agent-name">{{ agent.name }}</div>
              <div class="agent-type">{{ getBaseAgentTypeName(agent.baseAgentType) }}</div>
            </div>
            <el-dropdown @command="(command: string) => handleAgentCommand(command, agent)">
              <el-button link type="primary">
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
      </el-col>

      <el-col :span="18" class="content">
        <div v-if="shouldShowEmpty" class="empty-state">
          <el-empty description="请先创建一个智能体或选择左侧的智能体">
            <el-button type="primary" @click="showCreateDialog" class="gradient-btn">
              新建智能体
            </el-button>
          </el-empty>
        </div>

        <div v-else class="agent-detail">
          <div class="detail-header">
            <div class="header-left">
              <h2>{{ selectedAgentConfig?.name }}</h2>
              <el-tag>{{ getBaseAgentTypeName(selectedAgentConfig?.baseAgentType) }}</el-tag>
            </div>
            <el-button type="primary" @click="showEditDialog">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
          </div>

          <el-divider />

          <div class="detail-content">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="描述" :span="2">
                {{ selectedAgentConfig?.description || '暂无描述' }}
              </el-descriptions-item>
              <el-descriptions-item label="模型配置">
                {{ getModelConfigName(selectedAgentConfig?.modelConfigId) || '未选择' }}
              </el-descriptions-item>
              <el-descriptions-item label="Temperature">
                {{ selectedAgentConfig?.temperature }}
              </el-descriptions-item>
              <el-descriptions-item label="知识库">
                {{ getKnowledgeBaseName(selectedAgentConfig?.knowledgeBaseId) || '未选择' }}
              </el-descriptions-item>
              <el-descriptions-item label="工具">
                {{ selectedAgentConfig?.tools || '未配置' }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间" :span="2">
                {{ formatDate(selectedAgentConfig?.createdAt) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑智能体' : '创建智能体'"
      width="600px"
      class="custom-dialog"
    >
      <el-form :model="formData" label-width="100px">
        <el-form-item label="智能体名称" required>
          <el-input v-model="formData.name" placeholder="请输入智能体名称" class="styled-input" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
            class="styled-input"
          />
        </el-form-item>

        <el-form-item label="基础智能体" required>
          <el-select v-model="formData.baseAgentType" placeholder="请选择基础智能体" style="width: 100%" class="styled-select">
            <el-option label="SimpleAgent" value="SimpleAgent" />
            <el-option label="ReActAgent" value="ReActAgent" />
            <el-option label="ReflectionAgent" value="ReflectionAgent" />
            <el-option label="PlanAndSolveAgent" value="PlanAndSolveAgent" />
            <el-option label="FunctionCallAgent" value="FunctionCallAgent" />
          </el-select>
        </el-form-item>

        <el-form-item label="模型配置" required>
          <el-select v-model="formData.modelConfigId" placeholder="请选择模型配置" style="width: 100%" class="styled-select">
            <el-option
              v-for="mc in modelConfigs"
              :key="mc.id"
              :label="`${mc.name} (${mc.modelName})`"
              :value="mc.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Temperature">
          <el-slider
            v-model="formData.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-input
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="知识库">
          <el-select v-model="formData.knowledgeBaseId" placeholder="请选择知识库" style="width: 100%" class="styled-select" clearable>
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="工具">
          <el-input v-model="formData.tools" placeholder="请输入工具配置" class="styled-input" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false" class="secondary-btn">取消</el-button>
        <el-button type="primary" @click="handleSave" class="gradient-btn">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, MoreFilled } from '@element-plus/icons-vue'
import { agentConfigApi, knowledgeBaseApi, modelConfigApi } from '../services/api'

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
  modelName: string
  baseUrl: string
  apiKey: string
  temperature?: number
  enabled: boolean
  isDefault: boolean
}

const agentConfigs = ref<AgentConfig[]>([])
const knowledgeBases = ref<KnowledgeBase[]>([])
const modelConfigs = ref<ModelConfig[]>([])
const selectedAgentConfig = ref<AgentConfig | null>(null)
const dialogVisible = ref(false)
const isEditing = ref(false)
const formData = ref<Partial<AgentConfig>>({
  name: '',
  description: '',
  baseAgentType: '',
  modelConfigId: undefined,
  temperature: 0.7,
  knowledgeBaseId: undefined,
  tools: ''
})

const shouldShowEmpty = computed(() => {
  return agentConfigs.value.length === 0 || selectedAgentConfig.value === null
})

const baseAgentTypeNames: Record<string, string> = {
  'SimpleAgent': '简单聊天助手',
  'ReActAgent': '推理行动助手',
  'ReflectionAgent': '反思助手',
  'PlanAndSolveAgent': '规划解决助手',
  'FunctionCallAgent': '函数调用助手'
}

function getBaseAgentTypeName(type?: string): string {
  if (!type) return '未知类型'
  return baseAgentTypeNames[type] || type
}

function getKnowledgeBaseName(id?: string): string | undefined {
  if (!id) return undefined
  const kb = knowledgeBases.value.find(k => k.id === id)
  return kb?.name
}

function getModelConfigName(id?: string): string | undefined {
  if (!id) return undefined
  const mc = modelConfigs.value.find(m => m.id === id)
  return mc ? `${mc.name} (${mc.modelName})` : undefined
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

async function loadAgentConfigs() {
  try {
    const data = await agentConfigApi.getAllAgentConfigs()
    agentConfigs.value = Array.isArray(data) ? data : []
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
    const data = await modelConfigApi.getEnabledConfigs()
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

function selectAgentConfig(agent: AgentConfig) {
  selectedAgentConfig.value = agent
}

function showCreateDialog() {
  isEditing.value = false
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

function showEditDialog() {
  if (!selectedAgentConfig.value) return
  isEditing.value = true
  const { modelName, ...rest } = selectedAgentConfig.value
  formData.value = rest
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.value.name || !formData.value.baseAgentType || !formData.value.modelConfigId) {
    ElMessage.warning('请填写必填字段')
    return
  }

  try {
    const dataToSave = { ...formData.value }
    delete dataToSave.modelName
    
    console.log('formData.value:', formData.value)
    console.log('dataToSave:', dataToSave)
    console.log('dataToSave.modelConfigId:', dataToSave.modelConfigId)
    console.log('dataToSave.modelName:', dataToSave.modelName)
    
    if (isEditing.value && formData.value.id) {
      await agentConfigApi.updateAgentConfig(formData.value.id, dataToSave)
      ElMessage.success('更新成功')
    } else {
      await agentConfigApi.createAgentConfig(dataToSave)
      ElMessage.success('创建成功')
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
    selectedAgentConfig.value = agent
    showEditDialog()
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

onMounted(async () => {
  await loadAgentConfigs()
  await loadKnowledgeBases()
  await loadModelConfigs()
})
</script>

<style scoped>
.agent-management {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar {
  height: 100%;
  background: var(--bg-tertiary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
}

.sidebar-header h3 {
  margin: 0 0 16px 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.agent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  margin-bottom: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.agent-item:hover {
  border-color: var(--accent-primary);
  background: rgba(99, 102, 241, 0.05);
}

.agent-item.active {
  border-color: var(--accent-primary);
  background: rgba(99, 102, 241, 0.1);
}

.agent-info {
  flex: 1;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-type {
  font-size: 12px;
  color: var(--text-muted);
}

.content {
  height: 100%;
  overflow-y: auto;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-detail {
  padding: 24px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 600;
}

.detail-content {
  margin-top: 24px;
}

.gradient-btn {
  background: var(--gradient-primary);
  border: none;
  color: #fff;
  transition: all 0.3s ease;
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

.styled-select :deep(.el-input__wrapper) {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  box-shadow: none;
}

.styled-select :deep(.el-input__wrapper:hover) {
  border-color: var(--border-light);
}

.styled-select :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}

.custom-dialog :deep(.el-dialog__header) {
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  padding: 20px 24px;
}

.custom-dialog :deep(.el-dialog__title) {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.custom-dialog :deep(.el-dialog__body) {
  background: var(--bg-card);
  padding: 24px;
}

.custom-dialog :deep(.el-dialog__footer) {
  background: var(--bg-tertiary);
  border-top: 1px solid var(--border-color);
  padding: 16px 24px;
}
</style>
