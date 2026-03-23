
-- Agent 配置表
CREATE TABLE IF NOT EXISTS agent_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    base_agent_type VARCHAR(100) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    temperature DECIMAL(3, 2) DEFAULT 0.7,
    knowledge_base_id BIGINT,
    tools TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_agent_config_name ON agent_config(name);
CREATE INDEX IF NOT EXISTS idx_agent_config_base_agent_type ON agent_config(base_agent_type);
CREATE INDEX IF NOT EXISTS idx_agent_config_knowledge_base_id ON agent_config(knowledge_base_id);
