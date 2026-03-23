-- =====================================================
-- 向量存储表迁移脚本
-- 用于存储记忆的向量嵌入
-- =====================================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建向量存储表
CREATE TABLE IF NOT EXISTS memory_vectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    memory_type VARCHAR(50),
    session_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量索引 (HNSW)
CREATE INDEX IF NOT EXISTS memory_vectors_embedding_idx 
ON memory_vectors 
USING hnsw (embedding vector_cosine_ops);

-- 创建其他索引
CREATE INDEX IF NOT EXISTS memory_vectors_session_idx ON memory_vectors(session_id);
CREATE INDEX IF NOT EXISTS memory_vectors_type_idx ON memory_vectors(memory_type);
CREATE INDEX IF NOT EXISTS memory_vectors_created_idx ON memory_vectors(created_at);

-- 添加注释
COMMENT ON TABLE memory_vectors IS '记忆向量存储表';
COMMENT ON COLUMN memory_vectors.id IS '记忆唯一标识';
COMMENT ON COLUMN memory_vectors.content IS '记忆内容';
COMMENT ON COLUMN memory_vectors.embedding IS '向量嵌入(1536维)';
COMMENT ON COLUMN memory_vectors.metadata IS '元数据(JSON格式)';
COMMENT ON COLUMN memory_vectors.memory_type IS '记忆类型: episodic, semantic, perceptual';
COMMENT ON COLUMN memory_vectors.session_id IS '会话ID';
COMMENT ON COLUMN memory_vectors.created_at IS '创建时间';
COMMENT ON COLUMN memory_vectors.updated_at IS '更新时间';

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_memory_vectors_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_memory_vectors_updated_at
BEFORE UPDATE ON memory_vectors
FOR EACH ROW
EXECUTE FUNCTION update_memory_vectors_updated_at();
