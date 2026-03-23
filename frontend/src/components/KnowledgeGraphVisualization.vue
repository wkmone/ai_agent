<template>
  <div class="knowledge-graph">
    <div class="toolbar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索节点..."
        style="width: 300px"
        @keyup.enter="handleSearch"
      >
        <template #append>
          <el-button @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
      <div class="toolbar-actions">
        <el-button @click="handleZoomIn" title="放大">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button @click="handleZoomOut" title="缩小">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button @click="handleCenter" title="居中">
          <el-icon><Aim /></el-icon>
        </el-button>
        <el-button @click="loadGraphData" title="刷新">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button @click="showAddDialog = true" title="添加节点">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="stats-bar" v-if="stats">
      <el-tag type="primary">节点: {{ stats.totalNodes || nodes.length }}</el-tag>
      <el-tag type="success">关系: {{ stats.totalEdges || edges.length }}</el-tag>
      <el-tag :type="stats.healthScore > 0.7 ? 'success' : 'warning'">
        健康度: {{ ((stats.healthScore || 0) * 100).toFixed(0) }}%
      </el-tag>
    </div>

    <div class="graph-container">
      <div v-if="loading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <canvas
        v-else
        ref="canvasRef"
        :width="canvasWidth"
        :height="canvasHeight"
        @mousedown="handleCanvasMouseDown"
        @mousemove="handleCanvasMouseMove"
        @mouseup="handleCanvasMouseUp"
        @mouseleave="handleCanvasMouseUp"
      />
    </div>

    <div class="node-details" v-if="selectedNode">
      <div class="node-header">
        <h4>{{ selectedNode.label }}</h4>
        <el-button type="danger" size="small" @click="handleDeleteNode">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <el-card shadow="never" class="detail-card">
        <div class="detail-item">
          <span class="label">类别</span>
          <el-tag :style="{ backgroundColor: getCategoryColor(selectedNode.category), color: '#fff' }">
            {{ selectedNode.category || '未分类' }}
          </el-tag>
        </div>
      </el-card>
      <el-card shadow="never" class="detail-card" v-if="selectedNode.definition">
        <div class="detail-item">
          <span class="label">定义</span>
          <p>{{ selectedNode.definition }}</p>
        </div>
      </el-card>
      <el-card shadow="never" class="detail-card">
        <div class="detail-row">
          <div class="detail-item">
            <span class="label">重要性</span>
            <span>{{ ((selectedNode.importance || 0) * 100).toFixed(0) }}%</span>
          </div>
          <div class="detail-item">
            <span class="label">访问次数</span>
            <span>{{ selectedNode.accessCount || 0 }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="showAddDialog" title="添加知识节点" width="400px">
      <el-form label-width="80px">
        <el-form-item label="节点名称">
          <el-input v-model="newNodeName" />
        </el-form-item>
        <el-form-item label="类别">
          <el-input v-model="newNodeCategory" placeholder="如：技术、概念、方法..." />
        </el-form-item>
        <el-form-item label="定义">
          <el-input type="textarea" v-model="newNodeDefinition" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddNode">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { knowledgeGraphApi } from '../services/api'
import { ElMessage } from 'element-plus'

interface GraphNode {
  id: string
  label: string
  category?: string
  definition?: string
  importance?: number
  accessCount?: number
}

interface GraphEdge {
  source: string
  target: string
  relationType: string
  weight?: number
}

const canvasRef = ref<HTMLCanvasElement | null>(null)
const canvasWidth = ref(800)
const canvasHeight = ref(600)

const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const loading = ref(false)
const stats = ref<any>(null)
const searchKeyword = ref('')
const selectedNode = ref<GraphNode | null>(null)
const nodePositions = ref<Map<string, { x: number; y: number }>>(new Map())

const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })

const showAddDialog = ref(false)
const newNodeName = ref('')
const newNodeCategory = ref('')
const newNodeDefinition = ref('')

const categoryColors: Record<string, string> = {
  '技术': '#4CAF50',
  '概念': '#2196F3',
  '方法': '#FF9800',
  '工具': '#9C27B0',
  '人物': '#E91E63',
  '组织': '#00BCD4',
  '事件': '#FF5722',
  'extracted': '#607D8B',
  'general': '#795548',
}

function getCategoryColor(category?: string): string {
  return categoryColors[category || 'general'] || '#795548'
}

onMounted(async () => {
  await loadGraphData()
  nextTick(() => {
    drawGraph()
  })
})

watch([nodes, edges, scale, offset, selectedNode], () => {
  nextTick(() => {
    drawGraph()
  })
}, { deep: true })

async function loadGraphData() {
  loading.value = true
  try {
    const [graphResponse, statsResponse] = await Promise.all([
      knowledgeGraphApi.getFullGraph({ nodeLimit: 100, edgeLimit: 200 }),
      knowledgeGraphApi.getStats(),
    ])
    nodes.value = graphResponse.nodes || []
    edges.value = graphResponse.edges || []
    stats.value = statsResponse

    const positions = new Map<string, { x: number; y: number }>()
    const centerX = 400
    const centerY = 300
    
    nodes.value.forEach((node, index) => {
      const angle = (2 * Math.PI * index) / nodes.value.length
      const radius = 150 + Math.random() * 100
      positions.set(node.id, {
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle),
      })
    })
    nodePositions.value = positions
  } catch (err: any) {
    console.error('加载知识图谱失败:', err)
  } finally {
    loading.value = false
  }
}

function drawGraph() {
  if (!canvasRef.value || nodes.value.length === 0) return

  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.save()
  ctx.translate(offset.value.x, offset.value.y)
  ctx.scale(scale.value, scale.value)

  edges.value.forEach((edge) => {
    const sourcePos = nodePositions.value.get(edge.source)
    const targetPos = nodePositions.value.get(edge.target)
    if (!sourcePos || !targetPos) return

    ctx.beginPath()
    ctx.moveTo(sourcePos.x, sourcePos.y)
    ctx.lineTo(targetPos.x, targetPos.y)
    ctx.strokeStyle = edge.weight && edge.weight > 0.7 ? '#1976d2' : '#90caf9'
    ctx.lineWidth = edge.weight ? edge.weight * 2 : 1
    ctx.stroke()

    const midX = (sourcePos.x + targetPos.x) / 2
    const midY = (sourcePos.y + targetPos.y) / 2
    ctx.fillStyle = '#666'
    ctx.font = '10px Arial'
    ctx.fillText(edge.relationType, midX, midY)
  })

  nodes.value.forEach((node) => {
    const pos = nodePositions.value.get(node.id)
    if (!pos) return

    const color = getCategoryColor(node.category)
    const radius = 10 + (node.importance || 0.5) * 15

    ctx.beginPath()
    ctx.arc(pos.x, pos.y, radius, 0, 2 * Math.PI)
    ctx.fillStyle = selectedNode.value?.id === node.id ? '#ff9800' : color
    ctx.fill()
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.stroke()

    ctx.fillStyle = '#333'
    ctx.font = 'bold 12px Arial'
    ctx.textAlign = 'center'
    ctx.fillText(node.label || node.id, pos.x, pos.y + radius + 15)
  })

  ctx.restore()
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) return
  try {
    const results = await knowledgeGraphApi.searchNodes(searchKeyword.value)
    if (results.length > 0) {
      const firstResult = results[0]
      selectedNode.value = {
        id: firstResult.name || firstResult.id,
        label: firstResult.name || firstResult.id,
        category: firstResult.category,
        definition: firstResult.definition,
        importance: firstResult.importance,
        accessCount: firstResult.accessCount,
      }
    }
  } catch (err) {
    console.error('搜索失败:', err)
  }
}

async function handleAddNode() {
  if (!newNodeName.value.trim()) return
  try {
    await knowledgeGraphApi.createNode({
      name: newNodeName.value,
      category: newNodeCategory.value || 'general',
      definition: newNodeDefinition.value,
    })
    showAddDialog.value = false
    newNodeName.value = ''
    newNodeCategory.value = ''
    newNodeDefinition.value = ''
    await loadGraphData()
    ElMessage.success('添加成功')
  } catch (err) {
    console.error('创建节点失败:', err)
    ElMessage.error('创建失败')
  }
}

async function handleDeleteNode() {
  if (!selectedNode.value) return
  try {
    await knowledgeGraphApi.deleteNode(selectedNode.value.id)
    selectedNode.value = null
    await loadGraphData()
    ElMessage.success('删除成功')
  } catch (err) {
    console.error('删除节点失败:', err)
    ElMessage.error('删除失败')
  }
}

function handleZoomIn() {
  scale.value = Math.min(scale.value * 1.2, 3)
}

function handleZoomOut() {
  scale.value = Math.max(scale.value / 1.2, 0.3)
}

function handleCenter() {
  scale.value = 1
  offset.value = { x: 0, y: 0 }
}

function handleCanvasMouseDown(e: MouseEvent) {
  isDragging.value = true
  dragStart.value = { x: e.clientX - offset.value.x, y: e.clientY - offset.value.y }
}

function handleCanvasMouseMove(e: MouseEvent) {
  if (!isDragging.value) return
  offset.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y,
  }
}

function handleCanvasMouseUp() {
  isDragging.value = false
}
</script>

<style scoped>
.knowledge-graph {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.stats-bar {
  display: flex;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.graph-container {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
}

canvas {
  cursor: grab;
}

.node-details {
  position: absolute;
  right: 16px;
  top: 100px;
  width: 280px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 16px;
  max-height: calc(100% - 120px);
  overflow: auto;
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.node-header h4 {
  margin: 0;
}

.detail-card {
  margin-bottom: 12px;
}

.detail-item {
  margin-bottom: 8px;
}

.detail-item .label {
  font-size: 12px;
  color: #909399;
  display: block;
  margin-bottom: 4px;
}

.detail-row {
  display: flex;
  gap: 20px;
}
</style>
