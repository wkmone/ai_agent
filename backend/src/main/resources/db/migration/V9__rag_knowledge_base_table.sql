-- =====================================================
-- RAG知识库表迁移脚本
-- 用于存储知识库的基本信息
-- =====================================================

-- 创建RAG知识库表
CREATE TABLE IF NOT EXISTS rag_knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    namespace VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS rag_kb_name_idx ON rag_knowledge_base(name);
CREATE INDEX IF NOT EXISTS rag_kb_namespace_idx ON rag_knowledge_base(namespace);

-- 添加注释
COMMENT ON TABLE rag_knowledge_base IS 'RAG知识库表';
COMMENT ON COLUMN rag_knowledge_base.id IS '知识库唯一标识';
COMMENT ON COLUMN rag_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN rag_knowledge_base.description IS '知识库描述';
COMMENT ON COLUMN rag_knowledge_base.namespace IS '命名空间(用于向量存储隔离)';
COMMENT ON COLUMN rag_knowledge_base.created_at IS '创建时间';
COMMENT ON COLUMN rag_knowledge_base.updated_at IS '更新时间';

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_rag_knowledge_base_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_rag_knowledge_base_updated_at
BEFORE UPDATE ON rag_knowledge_base
FOR EACH ROW
EXECUTE FUNCTION update_rag_knowledge_base_updated_at();

-- 为现有表添加knowledge_base_id字段
ALTER TABLE rag_chunk ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT;
ALTER TABLE rag_document ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT;

-- 创建索引
CREATE INDEX IF NOT EXISTS rag_chunk_kb_idx ON rag_chunk(knowledge_base_id);
CREATE INDEX IF NOT EXISTS rag_document_kb_idx ON rag_document(knowledge_base_id);
