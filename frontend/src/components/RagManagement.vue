<template>
  <div class="rag-management">
    <div class="knowledge-base-sidebar">
      <div class="sidebar-header">
        <h3>知识库</h3>
        <el-button type="primary" size="small" @click="showCreateDialog = true" class="create-btn">
          <el-icon><Plus /></el-icon>
          新建
        </el-button>
      </div>
      
      <div class="knowledge-base-list">
        <div
          v-for="kb in knowledgeBases"
          :key="kb.id"
          class="knowledge-base-item"
          :class="{ active: selectedKnowledgeBaseId === kb.id }"
          @click="selectKnowledgeBase(kb.id)"
        >
          <div class="kb-icon">
            <el-icon><Folder /></el-icon>
          </div>
          <div class="kb-info">
            <div class="kb-name">{{ kb.name }}</div>
            <div class="kb-desc" v-if="kb.description">{{ kb.description }}</div>
          </div>
          <el-dropdown @command="(cmd: string) => handleKnowledgeBaseAction(cmd, kb)" trigger="click">
            <el-button type="text" size="small" class="kb-menu-btn" @click.stop>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="delete" :disabled="selectedKnowledgeBaseId === kb.id">
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-empty v-if="knowledgeBases.length === 0" description="暂无知识库" :image-size="80" />
      </div>
    </div>

    <div class="knowledge-base-content">
      <div v-if="shouldShowEmpty" class="empty-state">
        <el-empty :description="emptyDescription" :image-size="120">
          <el-button v-if="knowledgeBases.length === 0" type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            创建知识库
          </el-button>
        </el-empty>
      </div>
      
      <div v-else class="content-wrapper">
        <div class="stats-cards" v-if="stats">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon chunks">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="总分块数" :value="stats.chunkCount || 0" />
            </div>
          </el-card>
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon documents">
              <el-icon><Files /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="文档数" :value="stats.documentCount || 0" />
            </div>
          </el-card>
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon tokens">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="总Token数" :value="stats.totalTokens || 0" />
            </div>
          </el-card>
        </div>

        <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="custom-tabs rag-tabs">
          <el-tab-pane label="添加知识" name="add">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="card-header">
                  <span class="card-title">添加文本到知识库</span>
                </div>
              </template>
              <el-form label-width="100px">
                <el-form-item label="文本内容">
                  <el-input type="textarea" v-model="textContent" :rows="6" placeholder="输入要添加到知识库的文本内容..." class="styled-input" />
                </el-form-item>
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="分块大小">
                      <el-input-number v-model="chunkSize" :min="100" :max="2000" class="styled-number" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="重叠大小">
                      <el-input-number v-model="overlapSize" :min="0" :max="200" class="styled-number" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item>
                  <el-button type="primary" @click="handleAddText" :loading="loading" class="gradient-btn">添加到知识库</el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card shadow="never" class="section-card" style="margin-top: 16px">
              <template #header>
                <div class="card-header">
                  <span class="card-title">上传文档到知识库</span>
                </div>
              </template>
              <p class="help-text">支持的文件格式: TXT, MD, HTML, PDF, DOC, DOCX, PPT, PPTX, JSON, CSV</p>
              <el-form label-width="100px">
                <el-form-item>
                  <input type="file" ref="fileInputRef" style="display: none" @change="handleFileUpload" accept=".txt,.md,.html,.pdf,.doc,.docx,.ppt,.pptx,.json,.csv" />
                  <el-button type="primary" @click="() => fileInputRef?.click()" :loading="uploadLoading" class="gradient-btn">选择文件上传（异步）</el-button>
                </el-form-item>
                <el-progress v-if="uploadLoading && currentTaskId" :percentage="Math.round(processingProgress * 100)" :stroke-width="8" :status="progressStatus" class="styled-progress">
                  <template #default="{ percentage }">
                    <span class="percentage-text">{{ progressMessage }}</span>
                  </template>
                </el-progress>
                <div v-if="currentTaskId" class="task-info">
                  <p><strong>任务ID:</strong> {{ currentTaskId }}</p>
                  <p><strong>状态:</strong> {{ getStatusText(processingStatus) }}</p>
                </div>
              </el-form>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="知识检索" name="search">
            <el-card shadow="never" class="section-card">
              <el-form label-width="100px">
                <el-form-item label="检索查询">
                  <el-input v-model="searchQuery" placeholder="输入检索关键词或问题..." class="styled-input" />
                </el-form-item>
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="返回数量">
                      <el-input-number v-model="searchTopK" :min="1" :max="20" class="styled-number" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="相似度阈值">
                      <el-slider v-model="searchThreshold" :min="0" :max="1" :step="0.1" show-input class="styled-slider" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item class="checkbox-group">
                  <el-checkbox v-model="enableRerank" class="custom-checkbox">启用重排序</el-checkbox>
                </el-form-item>
                <el-form-item class="button-group">
                  <el-button type="primary" @click="handleSearch" :loading="loading" class="gradient-btn">检索</el-button>
                </el-form-item>
              </el-form>

              <div v-if="searchResults.length > 0" class="results-section">
                <h4>检索结果 ({{ searchResults.length }})</h4>
                <div v-for="(result, index) in searchResults" :key="index" class="result-item">
                  <div class="result-tags">
                    <el-tag size="small" class="custom-tag">#{{ index + 1 }}</el-tag>
                    <el-tag v-if="result.rerankScore !== undefined" size="small" type="primary" class="custom-tag">重排序: {{ result.rerankScore.toFixed(2) }}</el-tag>
                    <el-tag v-if="result.score !== undefined" size="small" type="success" class="custom-tag success">相似度: {{ result.score.toFixed(2) }}</el-tag>
                  </div>
                  <p class="result-text">{{ result.content?.substring(0, 300) }}...</p>
                </div>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="知识问答" name="ask">
            <el-card shadow="never" class="section-card">
              <el-form label-width="80px">
                <el-form-item label="问题">
                  <el-input v-model="question" placeholder="输入您的问题..." class="styled-input" />
                </el-form-item>
                <el-form-item class="checkbox-group">
                  <el-checkbox v-model="enableRerank" class="custom-checkbox">启用重排序</el-checkbox>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleAsk" :loading="answerLoading" class="gradient-btn">提问</el-button>
                </el-form-item>
              </el-form>
              <div v-if="answer" class="answer-section">
                <div class="answer-header">
                  <el-icon><ChatDotRound /></el-icon>
                  <strong>回答:</strong>
                </div>
                <p class="answer-text">{{ answer }}</p>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="文档管理" name="manage">
            <el-card shadow="never" class="section-card">
              <template #header>
                <div class="card-header">
                  <span class="card-title">文档列表</span>
                  <el-button type="primary" @click="loadDocuments" :loading="loading" class="gradient-btn">刷新列表</el-button>
                </div>
              </template>
              <el-table :data="documents" v-if="documents.length > 0" class="custom-table">
                <el-table-column prop="documentId" label="文档ID" width="220" />
                <el-table-column prop="sourcePath" label="文件路径" min-width="150" />
                <el-table-column prop="chunkCount" label="分块数" width="100" align="center" />
                <el-table-column prop="totalTokens" label="Token数" width="100" align="center" />
                <el-table-column prop="createdAt" label="创建时间" width="180">
                  <template #default="{ row }">
                    {{ formatDate(row.createdAt) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center">
                  <template #default="{ row }">
                    <el-button type="danger" size="small" @click="handleDeleteById(row.documentId)" class="danger-btn">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无文档，请先上传文档" class="custom-empty" />
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <el-dialog v-model="showCreateDialog" title="创建知识库" width="500px" class="custom-dialog">
      <el-form label-width="100px">
        <el-form-item label="知识库名称">
          <el-input v-model="newKbName" placeholder="输入知识库名称" class="styled-input" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input type="textarea" v-model="newKbDesc" :rows="3" placeholder="输入知识库描述（可选）" class="styled-input" />
        </el-form-item>
        <el-form-item label="命名空间">
          <el-input v-model="newKbNamespace" placeholder="留空自动生成" class="styled-input" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false" class="secondary-btn">取消</el-button>
        <el-button type="primary" @click="handleCreateKnowledgeBase" :loading="createLoading" class="gradient-btn">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ragApi, knowledgeBaseApi } from '../services/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Files, DataLine, Folder, Plus, MoreFilled, ChatDotRound } from '@element-plus/icons-vue'

const API_BASE_URL = 'http://localhost:8080/api/v1'

const knowledgeBases = ref<any[]>([])
const selectedKnowledgeBaseId = ref<number | null>(null)
const stats = ref<any>(null)
const activeTab = ref('add')
const loading = ref(false)

const textContent = ref('')
const chunkSize = ref(500)
const overlapSize = ref(50)

const searchQuery = ref('')
const searchTopK = ref(5)
const searchThreshold = ref(0.7)
const searchResults = ref<any[]>([])
const enableRerank = ref(false)

const question = ref('')
const answer = ref('')
const answerLoading = ref(false)

const documents = ref<any[]>([])
const uploadLoading = ref(false)
const uploadProgress = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)

const currentTaskId = ref<string | null>(null)
const processingProgress = ref(0)
const progressStatus = ref('')
const progressMessage = ref('')
let progressPollingInterval: number | null = null

const showCreateDialog = ref(false)
const newKbName = ref('')
const newKbDesc = ref('')
const newKbNamespace = ref('')
const createLoading = ref(false)

const shouldShowEmpty = computed(() => {
  return knowledgeBases.value.length === 0 || !selectedKnowledgeBaseId.value
})

const emptyDescription = computed(() => {
  if (knowledgeBases.value.length === 0) {
    return '请先创建一个知识库'
  }
  return '请选择一个知识库'
})

onMounted(() => {
  loadKnowledgeBases()
})

async function loadKnowledgeBases() {
  try {
    const result = await knowledgeBaseApi.getAllKnowledgeBases()
    knowledgeBases.value = Array.isArray(result) ? result.filter((kb: any) => kb && kb.id) : []
    if (knowledgeBases.value.length === 0) {
      selectedKnowledgeBaseId.value = null
      stats.value = null
      documents.value = []
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    knowledgeBases.value = []
    selectedKnowledgeBaseId.value = null
    stats.value = null
    documents.value = []
  }
}

async function selectKnowledgeBase(id: number) {
  selectedKnowledgeBaseId.value = id
  await loadStats()
  await loadDocuments()
}

async function loadStats() {
  if (!selectedKnowledgeBaseId.value) return
  try {
    stats.value = await knowledgeBaseApi.getKnowledgeBaseStats(selectedKnowledgeBaseId.value)
  } catch (error) {
    console.error('加载统计信息失败:', error)
  }
}

async function loadDocuments() {
  if (!selectedKnowledgeBaseId.value) return
  try {
    documents.value = await ragApi.getDocumentsFromKnowledgeBase(selectedKnowledgeBaseId.value)
  } catch (error) {
    console.error('加载文档列表失败:', error)
  }
}

async function handleCreateKnowledgeBase() {
  if (!newKbName.value.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  createLoading.value = true
  try {
    const result = await knowledgeBaseApi.createKnowledgeBase(
      newKbName.value,
      newKbDesc.value || undefined,
      newKbNamespace.value || undefined
    )
    if (result.success) {
      ElMessage.success('创建成功')
      showCreateDialog.value = false
      newKbName.value = ''
      newKbDesc.value = ''
      newKbNamespace.value = ''
      await loadKnowledgeBases()
      selectKnowledgeBase(result.knowledgeBase.id)
    } else {
      ElMessage.error(`创建失败: ${result.error}`)
    }
  } catch (error: any) {
    ElMessage.error(`创建失败: ${error.message}`)
  } finally {
    createLoading.value = false
  }
}

async function handleKnowledgeBaseAction(command: string, kb: any) {
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(`确定要删除知识库「${kb.name}」吗？此操作不可恢复！`, '删除确认', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      })
      const result = await knowledgeBaseApi.deleteKnowledgeBase(kb.id)
      if (result.success) {
        ElMessage.success('删除成功')
        if (selectedKnowledgeBaseId.value === kb.id) {
          selectedKnowledgeBaseId.value = null
        }
        await loadKnowledgeBases()
      } else {
        ElMessage.error(`删除失败: ${result.error}`)
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        ElMessage.error(`删除失败: ${error.message}`)
      }
    }
  }
}

async function handleAddText() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!textContent.value.trim()) {
    ElMessage.warning('请输入文本内容')
    return
  }
  loading.value = true
  try {
    const result = await ragApi.addTextToKnowledgeBase(
      textContent.value,
      selectedKnowledgeBaseId.value,
      undefined,
      chunkSize.value,
      overlapSize.value
    )
    if (result.success) {
      ElMessage.success(`添加成功！文档ID: ${result.documentId}`)
      textContent.value = ''
      loadStats()
      loadDocuments()
    } else {
      ElMessage.error(`添加失败: ${result.error}`)
    }
  } catch (error: any) {
    ElMessage.error(`添加失败: ${error.message}`)
  } finally {
    loading.value = false
  }
}

async function handleFileUpload(event: Event) {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  uploadLoading.value = true
  currentTaskId.value = null
  processingProgress.value = 0
  progressStatus.value = ''
  progressMessage.value = ''

  try {
    const result = await ragApi.uploadDocumentToKnowledgeBaseAsync(
      file,
      selectedKnowledgeBaseId.value,
      chunkSize.value,
      overlapSize.value
    )

    if (result.success) {
      currentTaskId.value = result.taskId
      ElMessage.success(`任务已提交！任务ID: ${result.taskId}`)
      startProgressPolling(result.taskId)
    } else {
      ElMessage.error(`提交失败: ${result.error}`)
      uploadLoading.value = false
    }
  } catch (error: any) {
    ElMessage.error(`上传失败: ${error.message}`)
    uploadLoading.value = false
  }

  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function startProgressPolling(taskId: string) {
  if (progressPollingInterval) {
    clearInterval(progressPollingInterval)
  }

  progressPollingInterval = window.setInterval(async () => {
    try {
      const progress = await ragApi.getProcessingProgress(taskId)
      if (progress) {
        processingProgress.value = progress.progress || 0
        progressStatus.value = progress.status || ''
        progressMessage.value = progress.message || ''

        if (progress.status === 'COMPLETED') {
          ElMessage.success(`处理完成！文档ID: ${progress.documentId}`)
          stopProgressPolling()
          uploadLoading.value = false
          loadStats()
          loadDocuments()
        } else if (progress.status === 'FAILED') {
          ElMessage.error(`处理失败: ${progress.errorMessage}`)
          stopProgressPolling()
          uploadLoading.value = false
        }
      }
    } catch (error) {
      console.error('轮询进度失败:', error)
    }
  }, 1000)
}

function stopProgressPolling() {
  if (progressPollingInterval) {
    clearInterval(progressPollingInterval)
    progressPollingInterval = null
  }
}

function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'PENDING': '等待处理',
    'PARSING': '解析中',
    'CHUNKING': '分块中',
    'VECTORIZING': '向量化中',
    'SAVING': '保存中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return statusMap[status] || status
}

async function handleSearch() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索查询')
    return
  }
  loading.value = true
  try {
    searchResults.value = await ragApi.searchKnowledgeBase({
      query: searchQuery.value,
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      topK: searchTopK.value,
      threshold: searchThreshold.value,
      rerank: enableRerank.value,
    })
  } catch (error: any) {
    ElMessage.error(`检索失败: ${error.message}`)
  } finally {
    loading.value = false
  }
}

async function handleAsk() {
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  answerLoading.value = true
  answer.value = ''
  try {
    const result = await ragApi.askKnowledgeBase({
      question: question.value,
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      topK: searchTopK.value,
      threshold: searchThreshold.value,
      rerank: enableRerank.value,
    })
    answer.value = typeof result === 'string' ? result : (result as any).answer || JSON.stringify(result)
  } catch (error: any) {
    ElMessage.error(`问答失败: ${error.message}`)
  } finally {
    answerLoading.value = false
  }
}

async function handleDeleteById(documentId: string) {
  try {
    const result = await ragApi.deleteDocument(documentId)
    if (result.success) {
      ElMessage.success('删除成功')
      loadDocuments()
      loadStats()
    } else {
      ElMessage.error(`删除失败: ${result.error}`)
    }
  } catch (error: any) {
    ElMessage.error(`删除失败: ${error.message}`)
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

function handleTabChange(tabName: string) {
  if (tabName === 'manage') {
    loadDocuments()
  }
}
</script>

<style scoped>
.rag-management {
  height: 100%;
  display: flex;
  gap: 16px;
  overflow: hidden;
}

.knowledge-base-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.create-btn {
  padding: 6px 12px;
  font-size: 13px;
}

.knowledge-base-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.knowledge-base-item {
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.2s ease;
  margin-bottom: 8px;
}

.knowledge-base-item:hover {
  background: var(--bg-hover);
}

.knowledge-base-item.active {
  background: var(--accent-primary);
}

.knowledge-base-item.active .kb-name,
.knowledge-base-item.active .kb-desc,
.knowledge-base-item.active .kb-icon {
  color: #fff;
}

.kb-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-desc {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-menu-btn {
  padding: 4px;
  color: var(--text-muted);
}

.knowledge-base-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  flex-shrink: 0;
}

.stat-card {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s ease;
  overflow: hidden;
  position: relative;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-primary);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
  border-color: var(--accent-primary);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
  flex-shrink: 0;
}

.stat-icon.chunks {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}

.stat-icon.documents {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.stat-icon.tokens {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.stat-content {
  flex: 1;
}

.stat-content :deep(.el-statistic__head) {
  color: var(--text-muted);
  font-size: 14px;
  margin-bottom: 4px;
}

.stat-content :deep(.el-statistic__number) {
  color: var(--text-primary);
  font-size: 32px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.rag-tabs {
  flex: 1;
  overflow: auto;
}

.rag-tabs :deep(.el-tabs__content) {
  overflow: auto;
}

.rag-tabs :deep(.el-tab-pane) {
  height: auto;
}

.section-card {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
}

.section-card :deep(.el-card__header) {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  padding: 16px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.help-text {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.custom-tag {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.custom-tag.clickable {
  cursor: pointer;
}

.custom-tag.clickable:hover {
  background: var(--accent-primary);
  border-color: var(--accent-primary);
  color: #fff;
}

.custom-tag.success {
  background: rgba(16, 185, 129, 0.15);
  border-color: var(--accent-success);
  color: var(--accent-success);
}

.styled-input :deep(.el-input__wrapper),
.styled-input :deep(.el-textarea__inner) {
  background: var(--bg-card);
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

.styled-number :deep(.el-input__wrapper) {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  box-shadow: none;
}

.styled-number :deep(.el-input__wrapper:hover) {
  border-color: var(--border-light);
}

.styled-number :deep(.el-input__wrapper.is-focus) {
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
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.secondary-btn:hover {
  background: var(--bg-hover);
  border-color: var(--border-light);
  color: var(--text-primary);
}

.danger-btn {
  background: var(--gradient-danger);
  border: none;
  color: #fff;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.danger-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, transparent 50%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.danger-btn:hover::before {
  opacity: 1;
}

.danger-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.4);
}

.styled-progress {
  margin-top: 16px;
}

.styled-progress :deep(.el-progress-bar__outer) {
  background: var(--bg-card);
}

.styled-progress :deep(.el-progress-bar__inner) {
  background: var(--gradient-primary);
}

.custom-tabs {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  overflow: hidden;
}

.custom-tabs :deep(.el-tabs__header) {
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  margin: 0;
}

.custom-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.custom-tabs :deep(.el-tabs__item) {
  color: var(--text-secondary);
  transition: all 0.3s ease;
  border-bottom: 2px solid transparent;
}

.custom-tabs :deep(.el-tabs__item:hover) {
  color: var(--text-primary);
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent-primary);
  border-bottom-color: var(--accent-primary);
}

.custom-tabs :deep(.el-tabs__active-bar) {
  background: var(--accent-primary);
}

.custom-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

.checkbox-group {
  display: flex;
  gap: 24px;
}

.custom-checkbox {
  color: var(--text-secondary);
}

.button-group {
  display: flex;
  gap: 12px;
}

.results-section {
  margin-top: 20px;
}

.results-section h4 {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
}

.result-item {
  padding: 16px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-bottom: 12px;
  transition: all 0.3s ease;
}

.result-item:hover {
  background: var(--bg-hover);
  border-color: var(--accent-primary);
  transform: translateX(4px);
}

.result-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.result-text {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.answer-section {
  margin-top: 16px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid var(--accent-success);
  border-radius: 12px;
  padding: 20px;
}

.answer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--accent-success);
  font-size: 16px;
  margin-bottom: 12px;
}

.answer-text {
  white-space: pre-wrap;
  color: var(--text-secondary);
  line-height: 1.7;
}

.custom-empty {
  padding: 60px 0;
}

.custom-empty :deep(.el-empty__description) {
  color: var(--text-muted);
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

.styled-slider {
  padding: 8px 0;
}

.styled-slider :deep(.el-slider__runway) {
  background: var(--bg-card);
}

.styled-slider :deep(.el-slider__bar) {
  background: var(--gradient-primary);
}

.styled-slider :deep(.el-slider__button) {
  border-color: var(--accent-primary);
}

.custom-table {
  margin-top: 16px;
}

.custom-table :deep(.el-table__header th) {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 600;
}

.custom-table :deep(.el-table__body tr) {
  background: var(--bg-card);
  transition: all 0.2s ease;
}

.custom-table :deep(.el-table__body tr:hover) {
  background: var(--bg-hover);
}

.custom-table :deep(.el-table__body td) {
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
}

.task-info {
  margin-top: 16px;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.task-info p {
  margin: 8px 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.task-info strong {
  color: var(--text-primary);
  font-weight: 600;
}

.percentage-text {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}
</style>
