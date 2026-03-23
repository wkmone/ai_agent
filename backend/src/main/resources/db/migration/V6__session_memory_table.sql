-- =====================================================
-- 简化记忆系统 - 新增 session_memory 表
-- =====================================================

-- 创建会话记忆表（替代 episodic_memory 和 semantic_memory）
CREATE TABLE IF NOT EXISTS session_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255),
    user_id VARCHAR(255),
    memory_type VARCHAR(50),
    content TEXT,
    keywords VARCHAR(500),
    importance DOUBLE PRECISION,
    created_at TIMESTAMP,
    accessed_at TIMESTAMP,
    access_count INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_session_memory_session ON session_memory(session_id);
CREATE INDEX IF NOT EXISTS idx_session_memory_type ON session_memory(memory_type);
CREATE INDEX IF NOT EXISTS idx_session_memory_importance ON session_memory(importance);
