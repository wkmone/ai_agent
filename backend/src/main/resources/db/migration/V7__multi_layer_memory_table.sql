-- =====================================================
-- 多层记忆系统 - 扩展 session_memory 表
-- Layer 1: Spring AI ChatMemory (Redis) - 自动管理
-- Layer 2: Working Memory (Redis) - session_memory, memory_type='working'
-- Layer 3: Episodic Memory (JDBC) - session_memory, memory_type='episodic'
-- Layer 4: Semantic Memory (JDBC) - session_memory, memory_type='semantic'
-- =====================================================

ALTER TABLE session_memory ADD COLUMN IF NOT EXISTS layer_level INTEGER DEFAULT 2;
ALTER TABLE session_memory ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE session_memory ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE session_memory ADD COLUMN IF NOT EXISTS summary TEXT;

CREATE INDEX IF NOT EXISTS idx_session_memory_layer ON session_memory(layer_level);
CREATE INDEX IF NOT EXISTS idx_session_memory_expires ON session_memory(expires_at);
CREATE INDEX IF NOT EXISTS idx_session_memory_session_layer ON session_memory(session_id, layer_level);

COMMENT ON COLUMN session_memory.layer_level IS '记忆层级: 1=对话历史, 2=工作记忆, 3=情景记忆, 4=语义记忆';
COMMENT ON COLUMN session_memory.expires_at IS '过期时间, NULL表示永不过期';
COMMENT ON COLUMN session_memory.metadata IS '元数据JSON: 存储额外信息如source, tags等';
COMMENT ON COLUMN session_memory.summary IS '记忆摘要, 用于快速检索';