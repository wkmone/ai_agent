-- =====================================================
-- RAG分块表迁移脚本
-- 用于存储文档分块的元数据信息
-- =====================================================

-- 创建RAG分块表
CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    heading_path VARCHAR(500),
    start_offset INTEGER,
    end_offset INTEGER,
    token_count INTEGER,
    rag_namespace VARCHAR(100) DEFAULT 'default',
    source_type VARCHAR(50),
    source_path VARCHAR(500),
    importance DOUBLE PRECISION DEFAULT 0.5,
    access_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS rag_chunk_document_idx ON rag_chunk(document_id);
CREATE INDEX IF NOT EXISTS rag_chunk_namespace_idx ON rag_chunk(rag_namespace);
CREATE INDEX IF NOT EXISTS rag_chunk_created_idx ON rag_chunk(created_at);

-- 复合索引：按命名空间和文档ID查询
CREATE INDEX IF NOT EXISTS rag_chunk_ns_doc_idx ON rag_chunk(rag_namespace, document_id);

-- 添加注释
COMMENT ON TABLE rag_chunk IS 'RAG文档分块存储表';
COMMENT ON COLUMN rag_chunk.id IS '分块唯一标识';
COMMENT ON COLUMN rag_chunk.document_id IS '文档唯一标识';
COMMENT ON COLUMN rag_chunk.content IS '分块内容';
COMMENT ON COLUMN rag_chunk.chunk_index IS '分块索引(从0开始)';
COMMENT ON COLUMN rag_chunk.heading_path IS '标题路径(如: 章节1 > 小节1.1)';
COMMENT ON COLUMN rag_chunk.start_offset IS '在原文中的起始偏移量';
COMMENT ON COLUMN rag_chunk.end_offset IS '在原文中的结束偏移量';
COMMENT ON COLUMN rag_chunk.token_count IS 'Token数量估算';
COMMENT ON COLUMN rag_chunk.rag_namespace IS 'RAG命名空间(用于隔离不同知识库)';
COMMENT ON COLUMN rag_chunk.source_type IS '来源类型(txt, pdf, docx等)';
COMMENT ON COLUMN rag_chunk.source_path IS '来源文件路径';
COMMENT ON COLUMN rag_chunk.importance IS '重要性评分(0-1)';
COMMENT ON COLUMN rag_chunk.access_count IS '访问次数';
COMMENT ON COLUMN rag_chunk.created_at IS '创建时间';
COMMENT ON COLUMN rag_chunk.updated_at IS '更新时间';

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_rag_chunk_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_rag_chunk_updated_at
BEFORE UPDATE ON rag_chunk
FOR EACH ROW
EXECUTE FUNCTION update_rag_chunk_updated_at();
