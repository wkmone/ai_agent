-- =====================================================
-- Agent笔记表迁移脚本
-- 用于存储结构化笔记，支持长时程任务追踪
-- =====================================================

-- 创建Agent笔记表
CREATE TABLE IF NOT EXISTS agent_note (
    id BIGSERIAL PRIMARY KEY,
    note_id VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    note_type VARCHAR(50) DEFAULT 'general',
    tags VARCHAR(500),
    session_id VARCHAR(255),
    project_name VARCHAR(255),
    importance DOUBLE PRECISION DEFAULT 0.5,
    access_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS agent_note_session_idx ON agent_note(session_id);
CREATE INDEX IF NOT EXISTS agent_note_type_idx ON agent_note(note_type);
CREATE INDEX IF NOT EXISTS agent_note_project_idx ON agent_note(project_name);
CREATE INDEX IF NOT EXISTS agent_note_importance_idx ON agent_note(importance DESC);
CREATE INDEX IF NOT EXISTS agent_note_created_idx ON agent_note(created_at);

-- 复合索引：按会话和类型查询
CREATE INDEX IF NOT EXISTS agent_note_session_type_idx ON agent_note(session_id, note_type);

-- 添加注释
COMMENT ON TABLE agent_note IS 'Agent结构化笔记表';
COMMENT ON COLUMN agent_note.id IS '主键';
COMMENT ON COLUMN agent_note.note_id IS '笔记唯一标识';
COMMENT ON COLUMN agent_note.title IS '笔记标题';
COMMENT ON COLUMN agent_note.content IS '笔记内容';
COMMENT ON COLUMN agent_note.note_type IS '笔记类型(task_state/conclusion/blocker/action/reference)';
COMMENT ON COLUMN agent_note.tags IS '标签(逗号分隔)';
COMMENT ON COLUMN agent_note.session_id IS '会话ID';
COMMENT ON COLUMN agent_note.project_name IS '项目名称';
COMMENT ON COLUMN agent_note.importance IS '重要性评分(0-1)';
COMMENT ON COLUMN agent_note.access_count IS '访问次数';
COMMENT ON COLUMN agent_note.created_at IS '创建时间';
COMMENT ON COLUMN agent_note.updated_at IS '更新时间';

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_agent_note_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_agent_note_updated_at ON agent_note;
CREATE TRIGGER trigger_update_agent_note_updated_at
BEFORE UPDATE ON agent_note
FOR EACH ROW
EXECUTE FUNCTION update_agent_note_updated_at();
