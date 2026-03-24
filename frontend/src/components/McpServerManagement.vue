<template>
  <div class="mcp-server-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">MCP 服务器管理</span>
          <div class="header-buttons">
            <el-button type="success" @click="reconnectAllServers" class="gradient-btn">
              重新连接所有
            </el-button>
            <el-button type="primary" @click="showAddDialog = true" class="gradient-btn">
              添加服务器
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="servers" class="custom-table" v-loading="loading">
        <el-table-column prop="name" label="名称" width="200" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="serverType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.serverType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.connected ? 'success' : 'danger'">
              {{ row.connected ? '已连接' : '未连接' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" align="center">
          <template #default="{ row }">
            <el-button
              v-if="!row.enabled"
              type="success"
              size="small"
              @click="enableServer(row.id)"
            >
              启用
            </el-button>
            <el-button
              v-else
              type="warning"
              size="small"
              @click="disableServer(row.id)"
            >
              禁用
            </el-button>
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

      <el-empty v-if="servers.length === 0 && !loading" description="暂无 MCP 服务器" class="custom-empty" />
    </el-card>

    <el-dialog v-model="showAddDialog" title="添加 MCP 服务器" width="600px">
      <el-form :model="newServer" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="newServer.name" placeholder="请输入服务器名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="newServer.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newServer.serverType" placeholder="请选择类型">
            <el-option label="STDIO" value="stdio" />
            <el-option label="Streamable HTTP" value="streamable" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置">
          <el-input
            v-model="newServer.config"
            type="textarea"
            :rows="5"
            placeholder='{"url": "http://localhost:8081", ...}'
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="newServer.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="addServer" :loading="submitting">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showToolsDialog" title="服务器工具列表" width="800px">
      <el-alert
        v-if="tools.length === 0"
        title="暂无工具"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
      >
        该 MCP 服务器当前没有可用工具，或服务器未连接。
      </el-alert>
      <el-table :data="tools" class="custom-table" v-loading="toolsLoading">
        <el-table-column prop="name" label="工具名称" width="200" />
        <el-table-column prop="description" label="描述" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mcpApi } from '@/services/api'

interface McpServer {
  id: number
  name: string
  description: string
  serverType: string
  config: string
  enabled: boolean
  connected: boolean
  createdAt: string
}

const servers = ref<McpServer[]>([])
const loading = ref(false)
const submitting = ref(false)
const showAddDialog = ref(false)
const showToolsDialog = ref(false)
const tools = ref<any[]>([])
const toolsLoading = ref(false)
const newServer = ref<Partial<McpServer>>({
  name: '',
  description: '',
  serverType: 'stdio',
  config: '',
  enabled: true
})

async function loadServers() {
  loading.value = true
  try {
    servers.value = await mcpApi.getMcpServers()
  } catch (error) {
    console.error('加载 MCP 服务器失败:', error)
    ElMessage.error('加载 MCP 服务器失败')
  } finally {
    loading.value = false
  }
}

async function addServer() {
  if (!newServer.value.name || !newServer.value.config) {
    ElMessage.warning('请填写完整信息')
    return
  }

  submitting.value = true
  try {
    await mcpApi.addMcpServer(newServer.value)
    ElMessage.success('添加成功')
    showAddDialog.value = false
    newServer.value = {
      name: '',
      description: '',
      serverType: 'stdio',
      config: '',
      enabled: true
    }
    loadServers()
  } catch (error: any) {
    ElMessage.error(`添加失败: ${error.message}`)
  } finally {
    submitting.value = false
  }
}

async function deleteServer(id: string) {
  try {
    await ElMessageBox.confirm('确定要删除此 MCP 服务器吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await mcpApi.deleteMcpServer(id)
    ElMessage.success('删除成功')
    loadServers()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败：${error.message}`)
    }
  }
}

async function enableServer(id: string) {
  try {
    await mcpApi.enableMcpServer(id)
    ElMessage.success('已启用')
    loadServers()
  } catch (error: any) {
    ElMessage.error(`启用失败：${error.message}`)
  }
}

async function disableServer(id: string) {
  try {
    await mcpApi.disableMcpServer(id)
    ElMessage.success('已禁用')
    loadServers()
  } catch (error: any) {
    ElMessage.error(`禁用失败：${error.message}`)
  }
}

async function viewTools(id: string) {
  tools.value = []
  toolsLoading.value = true
  showToolsDialog.value = true
  try {
    tools.value = await mcpApi.getMcpServerTools(id)
  } catch (error) {
    console.error('加载 MCP 工具失败:', error)
    ElMessage.error('加载 MCP 工具失败')
  } finally {
    toolsLoading.value = false
  }
}

async function reconnectServer(id: string) {
  try {
    await mcpApi.reconnectMcpServer(id)
    ElMessage.success('重连成功，正在刷新工具列表...')
    await new Promise(resolve => setTimeout(resolve, 1000))
    await loadServers()
  } catch (error: any) {
    ElMessage.error(`重连失败：${error.message}`)
  }
}

async function reconnectAllServers() {
  try {
    await ElMessageBox.confirm('确定要重新连接所有 MCP 服务器吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await mcpApi.reconnectAllMcpServers()
    ElMessage.success('重连成功，正在刷新工具列表...')
    await new Promise(resolve => setTimeout(resolve, 1000))
    await loadServers()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(`重连失败: ${error.message}`)
    }
  }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadServers()
})
</script>

<style scoped>
.mcp-server-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-buttons {
  display: flex;
  gap: 12px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
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
</style>