<template>
  <div class="memory-management">
    <el-card shadow="never" class="toolbar">
      <div class="toolbar-content">
        <div class="toolbar-left">
          <div class="input-group">
            <span class="input-label">会话ID</span>
            <el-input v-model="sessionId" placeholder="default" size="default" style="width: 180px" class="styled-input" />
          </div>
        </div>
        
        <div class="toolbar-center">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索记忆内容..." 
            size="default"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
            class="search-input styled-input"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        
        <div class="toolbar-right">
          <el-button @click="loadAllData" size="default" type="primary" class="gradient-btn">
            <el-icon><Refresh /></el-icon>
            刷新数据
          </el-button>
          <el-dropdown trigger="click">
            <el-button size="default" class="danger-btn">
              <el-icon><Delete /></el-icon>
              清空记忆
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="custom-dropdown">
                <el-dropdown-item @click="handleClearLayer('working')">
                  <el-icon><DocumentDelete /></el-icon>
                  清空工作记忆
                </el-dropdown-item>
                <el-dropdown-item @click="handleClearLayer('episodic')">
                  <el-icon><DocumentDelete /></el-icon>
                  清空情景记忆
                </el-dropdown-item>
                <el-dropdown-item @click="handleClearLayer('semantic')">
                  <el-icon><DocumentDelete /></el-icon>
                  清空语义记忆
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleClearAll">
                  <el-icon><DeleteFilled /></el-icon>
                  清空所有记忆
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" type="border-card" class="custom-tabs">
      <el-tab-pane label="概览" name="overview">
        <div v-if="searchResults.length > 0" class="search-results">
          <div class="section-header">
            <h3>搜索结果 ({{ searchResults.length }} 条)</h3>
            <el-button type="text" @click="searchResults = []; activeTab = 'overview'" class="clear-search-btn">
              <el-icon><Close /></el-icon>
              清除搜索
            </el-button>
          </div>
          <div class="memory-list">
            <div v-for="(memory, idx) in searchResults" :key="memory.id || idx" class="memory-item">
              <div class="memory-content">
                <div class="memory-text">{{ memory.content || '-' }}</div>
                <div class="memory-tags">
                  <el-tag v-if="memory.memoryType" size="small" class="custom-tag">
                    {{ getMemoryTypeName(memory.memoryType) }}
                  </el-tag>
                  <el-tag :style="{ backgroundColor: getImportanceColor(memory.importance || 0.5), color: '#fff' }" size="small" class="importance-tag">
                    {{ ((memory.importance || 0.5) * 100).toFixed(0) }}%
                  </el-tag>
                </div>
              </div>
              <div class="memory-meta">
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  创建: {{ formatTime(memory.createdAt) }}
                </span>
                <span v-if="memory.keywords" class="meta-item">
                  <el-icon><PriceTag /></el-icon>
                  关键词: {{ memory.keywords }}
                </span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="stats-cards">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon working">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="工作记忆" :value="stats?.workingCount || 0" />
            </div>
          </el-card>
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon episodic">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="情景记忆" :value="stats?.episodicCount || 0" />
            </div>
          </el-card>
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon semantic">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="语义记忆" :value="stats?.semanticCount || 0" />
            </div>
          </el-card>
          <el-card shadow="hover" class="stat-card">
            <div class="stat-icon total">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-content">
              <el-statistic title="总记忆数" :value="stats?.totalCount || 0" />
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="工作记忆" name="working">
        <div class="memory-list" v-loading="loading">
          <el-empty v-if="workingMemories.length === 0" description="暂无工作记忆" class="custom-empty" />
          <div v-else>
            <div v-for="(memory, idx) in workingMemories" :key="memory.id || idx" class="memory-item">
              <div class="memory-content">
                <div class="memory-text">{{ memory.content || '-' }}</div>
                <el-tag :style="{ backgroundColor: getImportanceColor(memory.importance || 0.5), color: '#fff' }" size="small" class="importance-tag">
                  {{ ((memory.importance || 0.5) * 100).toFixed(0) }}%
                </el-tag>
              </div>
              <div class="memory-meta">
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  创建: {{ formatTime(memory.createdAt) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="情景记忆" name="episodic">
        <div class="memory-list" v-loading="loading">
          <el-empty v-if="episodicMemories.length === 0" description="暂无情景记忆" class="custom-empty" />
          <div v-else>
            <div v-for="(memory, idx) in episodicMemories" :key="memory.id || idx" class="memory-item">
              <div class="memory-content">
                <div class="memory-text">{{ memory.content || '-' }}</div>
                <el-tag :style="{ backgroundColor: getImportanceColor(memory.importance || 0.5), color: '#fff' }" size="small" class="importance-tag">
                  {{ ((memory.importance || 0.5) * 100).toFixed(0) }}%
                </el-tag>
              </div>
              <div class="memory-meta">
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  创建: {{ formatTime(memory.createdAt) }}
                </span>
                <span v-if="memory.keywords" class="meta-item">
                  <el-icon><PriceTag /></el-icon>
                  关键词: {{ memory.keywords }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="语义记忆" name="semantic">
        <div class="memory-list" v-loading="loading">
          <el-empty v-if="semanticMemories.length === 0" description="暂无语义记忆" class="custom-empty" />
          <div v-else>
            <div v-for="(memory, idx) in semanticMemories" :key="memory.id || idx" class="memory-item">
              <div class="memory-content">
                <div class="memory-text">{{ memory.content || '-' }}</div>
                <el-tag :style="{ backgroundColor: getImportanceColor(memory.importance || 0.5), color: '#fff' }" size="small" class="importance-tag">
                  {{ ((memory.importance || 0.5) * 100).toFixed(0) }}%
                </el-tag>
              </div>
              <div class="memory-meta">
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  创建: {{ formatTime(memory.createdAt) }}
                </span>
                <span v-if="memory.keywords" class="meta-item">
                  <el-icon><PriceTag /></el-icon>
                  关键词: {{ memory.keywords }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="重要记忆" name="important">
        <div class="memory-list" v-loading="loading">
          <el-empty v-if="importantMemories.length === 0" description="暂无重要记忆" class="custom-empty" />
          <div v-else>
            <div v-for="(memory, idx) in importantMemories" :key="memory.id || idx" class="memory-item">
              <div class="memory-content">
                <div class="memory-text">{{ memory.content || '-' }}</div>
                <div class="memory-tags">
                  <el-tag type="warning" size="small" class="custom-tag warning">
                    {{ memory.memoryType || 'unknown' }}
                  </el-tag>
                  <el-tag :style="{ backgroundColor: getImportanceColor(memory.importance || 0.5), color: '#fff' }" size="small" class="importance-tag">
                    {{ ((memory.importance || 0.5) * 100).toFixed(0) }}%
                  </el-tag>
                </div>
              </div>
              <div class="memory-meta">
                <span class="meta-item">
                  <el-icon><Clock /></el-icon>
                  创建: {{ formatTime(memory.createdAt) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, ArrowDown, Delete, DocumentDelete, DeleteFilled, Close, Clock, PriceTag, Document, Timer, Connection, DataLine } from '@element-plus/icons-vue'
import { memoryApi } from '../services/api'

interface Memory {
  id: number
  content: string
  importance: number
  createdAt: string
  memoryType?: string
  accessCount?: number
  keywords?: string
  layerLevel?: number
  [key: string]: any
}

interface Stats {
  workingCount: number
  episodicCount: number
  semanticCount: number
  totalCount: number
  importantCount: number
  distribution: Record<string, number>
}

const activeTab = ref('overview')
const loading = ref(true)
const sessionId = ref('default')
const searchKeyword = ref('')

const stats = ref<Stats | null>(null)
const workingMemories = ref<Memory[]>([])
const episodicMemories = ref<Memory[]>([])
const semanticMemories = ref<Memory[]>([])
const importantMemories = ref<Memory[]>([])
const searchResults = ref<Memory[]>([])

onMounted(async () => {
  await loadAllData()
})

async function loadAllData() {
  loading.value = true
  searchResults.value = []
  try {
    await Promise.all([
      loadStats(),
      loadWorkingMemories(),
      loadEpisodicMemories(),
      loadSemanticMemories(),
      loadImportantMemories()
    ])
  } catch (err) {
    console.error('加载记忆数据失败:', err)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const data = await memoryApi.getMemoryStats(sessionId.value)
    stats.value = data
  } catch (err) {
    console.error('加载统计信息失败:', err)
  }
}

async function loadWorkingMemories() {
  try {
    const data = await memoryApi.getWorkingMemories(sessionId.value)
    workingMemories.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('加载工作记忆失败:', err)
    workingMemories.value = []
  }
}

async function loadEpisodicMemories() {
  try {
    const data = await memoryApi.getEpisodicMemories(sessionId.value)
    episodicMemories.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('加载情景记忆失败:', err)
    episodicMemories.value = []
  }
}

async function loadSemanticMemories() {
  try {
    const data = await memoryApi.getSemanticMemories(sessionId.value)
    semanticMemories.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('加载语义记忆失败:', err)
    semanticMemories.value = []
  }
}

async function loadImportantMemories() {
  try {
    const data = await memoryApi.getImportantMemories(sessionId.value, 50)
    importantMemories.value = Array.isArray(data) ? data : []
  } catch (err) {
    console.error('加载重要记忆失败:', err)
    importantMemories.value = []
  }
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    activeTab.value = 'overview'
    return
  }
  loading.value = true
  try {
    const data = await memoryApi.searchMemories(sessionId.value, searchKeyword.value)
    searchResults.value = Array.isArray(data) ? data : []
    activeTab.value = 'overview'
  } catch (err) {
    console.error('搜索失败:', err)
  } finally {
    loading.value = false
  }
}

function getMemoryTypeName(type: string): string {
  const typeMap: Record<string, string> = {
    'working': '工作记忆',
    'episodic': '情景记忆',
    'semantic': '语义记忆',
    'dialog': '对话记忆'
  }
  return typeMap[type] || type
}

async function handleClearLayer(layer: string) {
  if (!confirm(`确定要清空${layer}记忆吗？`)) return
  try {
    if (layer === 'working') {
      await memoryApi.clearWorkingMemory(sessionId.value)
    } else if (layer === 'episodic') {
      await memoryApi.clearEpisodicMemory(sessionId.value)
    } else if (layer === 'semantic') {
      await memoryApi.clearSemanticMemory(sessionId.value)
    }
    ElMessage.success('清空成功')
    await loadAllData()
  } catch (err: any) {
    ElMessage.error(err.message)
  }
}

async function handleClearAll() {
  if (!confirm('确定要清空所有记忆吗？此操作不可恢复！')) return
  try {
    await memoryApi.clearAllMemory(sessionId.value)
    ElMessage.success('清空成功')
    await loadAllData()
  } catch (err: any) {
    ElMessage.error(err.message)
  }
}

function getImportanceColor(importance: number): string {
  if (importance >= 0.7) return '#10b981'
  if (importance >= 0.4) return '#f59e0b'
  return '#ef4444'
}

function formatTime(timeStr: string): string {
  if (!timeStr) return '-'
  try {
    const date = new Date(timeStr)
    return date.toLocaleString('zh-CN')
  } catch {
    return timeStr
  }
}
</script>

<style scoped>
.memory-management {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
}

.toolbar-content {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.input-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.input-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
}

.toolbar-center {
  flex: 1;
  min-width: 300px;
}

.search-input {
  width: 100%;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
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

.search-results {
  padding: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.clear-search-btn {
  color: var(--text-muted);
  transition: all 0.3s ease;
}

.clear-search-btn:hover {
  color: var(--accent-primary);
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
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
  box-shadow: var(--shadow-card);
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

.stat-icon.working {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}

.stat-icon.episodic {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.stat-icon.semantic {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

.stat-icon.total {
  background: linear-gradient(135deg, #ec4899 0%, #db2777 100%);
}

.stat-content {
  flex: 1;
  text-align: center;
}

.stat-content :deep(.el-statistic__head) {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 4px;
  font-weight: 600;
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

.memory-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 4px;
}

.memory-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-bottom: 12px;
  transition: all 0.3s ease;
}

.memory-item:hover {
  background: var(--bg-hover);
  border-color: var(--accent-primary);
  transform: translateX(4px);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
}

.memory-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.memory-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.5;
}

.memory-tags {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.custom-tag {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.custom-tag.warning {
  background: rgba(245, 158, 11, 0.15);
  border-color: var(--accent-warning);
  color: var(--accent-warning);
}

.importance-tag {
  border: none;
  font-weight: 600;
}

.memory-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.meta-item .el-icon {
  font-size: 14px;
}

.custom-empty {
  padding: 60px 0;
}

.custom-empty :deep(.el-empty__description) {
  color: var(--text-muted);
}

.styled-input :deep(.el-input__wrapper) {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  box-shadow: none;
  transition: all 0.3s ease;
}

.styled-input :deep(.el-input__wrapper:hover) {
  border-color: var(--border-light);
}

.styled-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}

.custom-dropdown :deep(.el-dropdown-menu__item) {
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.custom-dropdown :deep(.el-dropdown-menu__item:hover) {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.custom-dropdown :deep(.el-dropdown-menu__item.is-divided) {
  border-top-color: var(--border-color);
}
</style>
