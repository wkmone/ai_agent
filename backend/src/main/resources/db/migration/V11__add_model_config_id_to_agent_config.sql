-- 为 agent_config 表添加 model_config_id 字段
ALTER TABLE agent_config ADD COLUMN IF NOT EXISTS model_config_id BIGINT;

-- 添加外键约束
ALTER TABLE agent_config ADD CONSTRAINT fk_agent_config_model_config
    FOREIGN KEY (model_config_id) REFERENCES model_config(id)
    ON DELETE SET NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_agent_config_model_config_id ON agent_config(model_config_id);