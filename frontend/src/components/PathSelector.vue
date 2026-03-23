<template>
  <div class="path-selector">
    <el-alert v-if="selectedPath" type="success" :closable="false" style="margin-bottom: 16px">
      <strong>已选择路径：</strong>{{ selectedPath }}
    </el-alert>

    <div class="quick-access" style="margin-bottom: 12px">
      <div class="label" style="font-size: 12px; color: #909399; margin-bottom: 8px">快速访问</div>
      <div class="quick-buttons" style="display: flex; flex-wrap: wrap; gap: 8px">
        <el-button size="small" v-for="path in quickPaths" :key="path.name" @click="setQuickPath(path.path)">
          <el-icon><Folder /></el-icon>
          {{ path.name }}
        </el-button>
      </div>
    </div>

    <div class="input-section">
      <div class="label">请输入项目路径</div>
      <div class="input-row">
        <el-input
          v-model="manualInput"
          placeholder="/Users/wk/Desktop/code/trae/ai_agent"
          @keyup.enter="handleConfirm"
        />
        <el-button type="primary" @click="handleConfirm" :disabled="!manualInput.trim()">
          <el-icon><Check /></el-icon>
          确认
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-top: 16px">
      示例路径：/Users/wk/Desktop/code/trae/ai_agent
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { Folder } from '@element-plus/icons-vue'

const props = defineProps<{
  selectedPath: string
}>()

const emit = defineEmits<{
  (e: 'select', path: string): void
}>()

const manualInput = ref(props.selectedPath || '')
const quickPaths = ref([
  { name: '主目录', path: '/Users/wk' },
  { name: '桌面', path: '/Users/wk/Desktop' },
  { name: '代码', path: '/Users/wk/Desktop/code' },
  { name: '当前项目', path: '/Users/wk/Desktop/code/trae/ai_agent' }
])

onMounted(() => {
  if (!manualInput.value.trim()) {
    manualInput.value = '/Users/wk/Desktop/code/trae/ai_agent'
  }
})

watch(() => props.selectedPath, (newVal) => {
  if (newVal && newVal !== manualInput.value) {
    manualInput.value = newVal
  }
})

function handleConfirm() {
  if (manualInput.value.trim()) {
    emit('select', manualInput.value.trim())
  }
}

function setQuickPath(path: string) {
  manualInput.value = path
  emit('select', path)
}
</script>

<style scoped>
.path-selector {
  width: 100%;
}

.input-section {
  margin-bottom: 16px;
}

.label {
  font-weight: bold;
  margin-bottom: 8px;
  font-size: 14px;
}

.input-row {
  display: flex;
  gap: 8px;
}
</style>
