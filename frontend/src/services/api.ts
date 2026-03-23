import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api/v1'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const chatApi = {
  simpleChat: (message: string) =>
    api.post<any>('/chat/simple', { message }).then(res => res.data),

  advancedChat: (message: string, sessionId?: string) =>
    api.post<any>('/chat/advanced', { message, sessionId }).then(res => res.data),

  chatWithSession: (sessionId: string, message: string) =>
    api.post<any>(`/chat/session/${sessionId}`, { message }).then(res => res.data),

  streamChat: (message: string) =>
    api.get<string>(`/chat/stream?message=${encodeURIComponent(message)}`, {
      responseType: 'stream'
    })
}

export const agentApi = {
  getAllAgents: () => api.get<any[]>('/agents').then(res => res.data),

  getAgentById: (id: string) => api.get<any>(`/agents/${id}`).then(res => res.data),

  processMessage: (id: string, message: string) =>
    api.post<any>(`/agents/${id}/message`, { message }).then(res => res.data),

  executeTask: (id: string, task: any) =>
    api.post<any>(`/agents/${id}/execute`, task).then(res => res.data),

  initializeCodebaseMaintainer: (projectName: string, codebasePath: string) =>
    api.post<any>('/agents/codebase-maintainer/initialize', { projectName, codebasePath }).then(res => res.data),

  getCodebaseMaintainerStats: () =>
    api.get<any>('/agents/codebase-maintainer/stats').then(res => res.data),

  codebaseMaintainerExplore: (target: string) =>
    api.post<any>('/agents/codebase-maintainer/explore', { target }).then(res => res.data),

  codebaseMaintainerAnalyze: (focus?: string) =>
    api.post<any>('/agents/codebase-maintainer/analyze', focus ? { focus } : {}).then(res => res.data),

  codebaseMaintainerPlan: () =>
    api.post<any>('/agents/codebase-maintainer/plan').then(res => res.data),
}

export const modelConfigApi = {
  getAllConfigs: () => api.get<any[]>('/model-config').then(res => res.data),
  getDefaultConfig: () => api.get<any>('/model-config/default').then(res => res.data),
  getEnabledConfigs: () => api.get<any[]>('/model-config/enabled').then(res => res.data),
  createConfig: (config: any) => api.post<any>('/model-config', config).then(res => res.data),
  updateConfig: (id: number, config: any) => api.put<boolean>(`/model-config/${id}`, config).then(res => res.data),
  deleteConfig: (id: number) => api.delete<boolean>(`/model-config/${id}`).then(res => res.data),
  setDefaultConfig: (id: number) => api.put<boolean>(`/model-config/${id}/default`).then(res => res.data),
}

export const ragApi = {
  addText: (data: { text: string; ragNamespace?: string; documentId?: string; chunkSize?: number; overlapSize?: number }) =>
    api.post<any>('/rag/text', data).then(res => res.data),

  addDocument: (data: { filePath: string; ragNamespace?: string; chunkSize?: number; overlapSize?: number }) =>
    api.post<any>('/rag/document', data).then(res => res.data),

  search: (params: { query: string; ragNamespace?: string; topK?: number; threshold?: number; rerank?: boolean }) => {
    const queryParams = new URLSearchParams()
    queryParams.append('query', params.query)
    if (params.ragNamespace) queryParams.append('ragNamespace', params.ragNamespace)
    if (params.topK) queryParams.append('topK', params.topK.toString())
    if (params.threshold) queryParams.append('threshold', params.threshold.toString())
    if (params.rerank) queryParams.append('rerank', params.rerank.toString())
    return api.get<any[]>(`/rag/search?${queryParams.toString()}`).then(res => res.data)
  },

  ask: (params: { question: string; ragNamespace?: string; topK?: number; threshold?: number; rerank?: boolean }) => {
    const queryParams = new URLSearchParams()
    queryParams.append('question', params.question)
    if (params.ragNamespace) queryParams.append('ragNamespace', params.ragNamespace)
    if (params.topK) queryParams.append('topK', params.topK.toString())
    if (params.threshold) queryParams.append('threshold', params.threshold.toString())
    if (params.rerank) queryParams.append('rerank', params.rerank.toString())
    return api.get<{ question: string; answer: string; namespace: string; success: boolean }>(`/rag/ask?${queryParams.toString()}`).then(res => res.data)
  },

  getStats: (ragNamespace?: string) => {
    const queryParams = ragNamespace ? `?ragNamespace=${ragNamespace}` : ''
    return api.get<any>(`/rag/stats${queryParams}`).then(res => res.data)
  },

  getDocuments: (ragNamespace?: string) => {
    const queryParams = ragNamespace ? `?ragNamespace=${ragNamespace}` : ''
    return api.get<any[]>(`/rag/documents${queryParams}`).then(res => res.data)
  },

  deleteDocument: (documentId: string) =>
    api.delete<any>(`/rag/document/${documentId}`).then(res => res.data),

  generateReport: (params?: { ragNamespace?: string; sessionId?: string }) => {
    const queryParams = new URLSearchParams()
    if (params?.ragNamespace) queryParams.append('ragNamespace', params.ragNamespace)
    if (params?.sessionId) queryParams.append('sessionId', params.sessionId)
    return api.get<any>(`/rag/report?${queryParams.toString()}`).then(res => res.data)
  },

  expandQuery: (params: { query: string; expansions?: number }) =>
    api.post<string[]>('/rag/expand-query', params).then(res => res.data),

  generateHypotheticalDocument: (query: string) =>
    api.post<string>('/rag/hyde', { query }).then(res => res.data),

  advancedSearch: (params: {
    query: string
    ragNamespace?: string
    topK?: number
    threshold?: number
    enableMqe?: boolean
    enableHyde?: boolean
    enableRerank?: boolean
  }) => api.post<any[]>('/rag/advanced-search', params).then(res => res.data),

  searchWithRerank: (params: {
    query: string
    ragNamespace?: string
    topK?: number
    threshold?: number
    rerankType?: string
  }) => api.post<any[]>('/rag/search-rerank', params).then(res => res.data),

  addTextToKnowledgeBase: (text: string, knowledgeBaseId: number, documentId?: string, chunkSize?: number, overlapSize?: number) => {
    const queryParams = new URLSearchParams()
    queryParams.append('knowledgeBaseId', knowledgeBaseId.toString())
    if (documentId) queryParams.append('documentId', documentId)
    if (chunkSize) queryParams.append('chunkSize', chunkSize.toString())
    if (overlapSize) queryParams.append('overlapSize', overlapSize.toString())
    return api.post<any>(`/rag/text/kb?${queryParams.toString()}`, text).then(res => res.data)
  },

  uploadDocumentToKnowledgeBase: (file: File, knowledgeBaseId: number, chunkSize?: number, overlapSize?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('knowledgeBaseId', knowledgeBaseId.toString())
    if (chunkSize) formData.append('chunkSize', chunkSize.toString())
    if (overlapSize) formData.append('overlapSize', overlapSize.toString())
    return api.post<any>('/rag/document/kb', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(res => res.data)
  },

  searchKnowledgeBase: (params: { query: string; knowledgeBaseId: number; topK?: number; threshold?: number; rerank?: boolean }) => {
    const queryParams = new URLSearchParams()
    queryParams.append('query', params.query)
    queryParams.append('knowledgeBaseId', params.knowledgeBaseId.toString())
    if (params.topK) queryParams.append('topK', params.topK.toString())
    if (params.threshold) queryParams.append('threshold', params.threshold.toString())
    if (params.rerank) queryParams.append('rerank', params.rerank.toString())
    return api.get<any[]>(`/rag/search/kb?${queryParams.toString()}`).then(res => res.data)
  },

  askKnowledgeBase: (params: { question: string; knowledgeBaseId: number; topK?: number; threshold?: number; rerank?: boolean }) => {
    const queryParams = new URLSearchParams()
    queryParams.append('question', params.question)
    queryParams.append('knowledgeBaseId', params.knowledgeBaseId.toString())
    if (params.topK) queryParams.append('topK', params.topK.toString())
    if (params.threshold) queryParams.append('threshold', params.threshold.toString())
    if (params.rerank) queryParams.append('rerank', params.rerank.toString())
    return api.get<{ question: string; answer: string; knowledgeBaseId: number; success: boolean }>(`/rag/ask/kb?${queryParams.toString()}`).then(res => res.data)
  },

  getDocumentsFromKnowledgeBase: (knowledgeBaseId: number) => {
    return api.get<any[]>(`/rag/documents/kb?knowledgeBaseId=${knowledgeBaseId}`).then(res => res.data)
  },
}

export const knowledgeBaseApi = {
  getAllKnowledgeBases: () => api.get<any[]>('/knowledge-bases').then(res => res.data),

  getKnowledgeBaseById: (id: number) => api.get<any>(`/knowledge-bases/${id}`).then(res => res.data),

  getKnowledgeBaseStats: (id: number) => api.get<any>(`/knowledge-bases/${id}/stats`).then(res => res.data),

  createKnowledgeBase: (name: string, description?: string, namespace?: string) => {
    const queryParams = new URLSearchParams()
    queryParams.append('name', name)
    if (description) queryParams.append('description', description)
    if (namespace) queryParams.append('namespace', namespace)
    return api.post<any>(`/knowledge-bases?${queryParams.toString()}`).then(res => res.data)
  },

  updateKnowledgeBase: (id: number, name?: string, description?: string) => {
    const queryParams = new URLSearchParams()
    if (name) queryParams.append('name', name)
    if (description) queryParams.append('description', description)
    return api.put<any>(`/knowledge-bases/${id}?${queryParams.toString()}`).then(res => res.data)
  },

  deleteKnowledgeBase: (id: number) => api.delete<any>(`/knowledge-bases/${id}`).then(res => res.data),
}

export const memoryApi = {
  getAllMemories: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.get<any>(`/memory/session${queryParams}`).then(res => res.data)
  },

  getWorkingMemories: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.get<any[]>(`/memory/session/working${queryParams}`).then(res => res.data)
  },

  getEpisodicMemories: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.get<any[]>(`/memory/session/episodic${queryParams}`).then(res => res.data)
  },

  getSemanticMemories: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.get<any[]>(`/memory/session/semantic${queryParams}`).then(res => res.data)
  },

  getImportantMemories: (sessionId?: string, limit?: number) => {
    const queryParams = new URLSearchParams()
    if (sessionId) queryParams.append('sessionId', sessionId)
    if (limit) queryParams.append('limit', limit.toString())
    return api.get<any[]>(`/memory/session/important?${queryParams.toString()}`).then(res => res.data)
  },

  searchMemories: (sessionId: string, query: string) => {
    const queryParams = new URLSearchParams()
    queryParams.append('sessionId', sessionId)
    queryParams.append('query', query)
    return api.get<any[]>(`/memory/session/search?${queryParams.toString()}`).then(res => res.data)
  },

  getRecentMemories: (sessionId?: string, limit?: number) => {
    const queryParams = new URLSearchParams()
    if (sessionId) queryParams.append('sessionId', sessionId)
    if (limit) queryParams.append('limit', limit.toString())
    return api.get<any[]>(`/memory/session/recent?${queryParams.toString()}`).then(res => res.data)
  },

  deleteMemory: (id: number) =>
    api.delete<any>(`/memory/session/${id}`).then(res => res.data),

  clearWorkingMemory: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.delete<any>(`/memory/session/working/clear${queryParams}`).then(res => res.data)
  },

  clearEpisodicMemory: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.delete<any>(`/memory/session/episodic/clear${queryParams}`).then(res => res.data)
  },

  clearSemanticMemory: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.delete<any>(`/memory/session/semantic/clear${queryParams}`).then(res => res.data)
  },

  clearAllMemory: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.delete<any>(`/memory/session/clear${queryParams}`).then(res => res.data)
  },

  getMemoryStats: (sessionId?: string) => {
    const queryParams = sessionId ? `?sessionId=${sessionId}` : ''
    return api.get<any>(`/memory/session/stats${queryParams}`).then(res => res.data)
  },
}

export const mcpApi = {
  getMcpServers: () =>
    api.get<any[]>('/mcp/servers').then(res => res.data),

  getMcpServer: (id: number) =>
    api.get<any>(`/mcp/servers/${id}`).then(res => res.data),

  getMcpServerTools: (id: number) =>
    api.get<any[]>(`/mcp/servers/${id}/tools`).then(res => res.data),

  getEnabledMcpServers: () =>
    api.get<any[]>('/mcp/servers/enabled').then(res => res.data),

  getMcpServerStatus: (id: number) =>
    api.get<any>(`/mcp/servers/${id}/status`).then(res => res.data),

  addMcpServer: (server: any) =>
    api.post<any>('/mcp/servers', server).then(res => res.data),

  updateMcpServer: (id: number, server: any) =>
    api.put<any>(`/mcp/servers/${id}`, server).then(res => res.data),

  deleteMcpServer: (id: number) =>
    api.delete(`/mcp/servers/${id}`),

  enableMcpServer: (id: number) =>
    api.post(`/mcp/servers/${id}/enable`),

  disableMcpServer: (id: number) =>
    api.post(`/mcp/servers/${id}/disable`),

  reconnectMcpServer: (id: number) =>
    api.post(`/mcp/servers/${id}/reconnect`),

  reconnectAllMcpServers: () =>
    api.post('/mcp/servers/reconnect-all'),
}

export const agentConfigApi = {
  getAllAgentConfigs: () => api.get<any[]>('/agent-configs').then(res => res.data),

  getAgentConfigById: (id: number) => api.get<any>(`/agent-configs/${id}`).then(res => res.data),

  createAgentConfig: (config: any) => api.post<any>('/agent-configs', config).then(res => res.data.agentConfig),

  updateAgentConfig: (id: number, config: any) => api.put<any>(`/agent-configs/${id}`, config).then(res => res.data.agentConfig),

  deleteAgentConfig: (id: number) => api.delete<any>(`/agent-configs/${id}`).then(res => res.data.success),
}

export const knowledgeGraphApi = {
  getGraphData: (kbId: number) => api.get<any>(`/knowledge-graphs/${kbId}/data`).then(res => res.data),
  getFullGraph: (params: { nodeLimit?: number; edgeLimit?: number }) => api.get<any>('/knowledge-graphs/full', { params }).then(res => res.data),
  getStats: () => api.get<any>('/knowledge-graphs/stats').then(res => res.data),
  searchNodes: (keyword: string) => api.get<any>(`/knowledge-graphs/search?keyword=${keyword}`).then(res => res.data),
  createNode: (nodeData: any) => api.post<any>('/knowledge-graphs/nodes', nodeData).then(res => res.data),
  deleteNode: (nodeId: string) => api.delete<any>(`/knowledge-graphs/nodes/${nodeId}`).then(res => res.data),
}

export default api