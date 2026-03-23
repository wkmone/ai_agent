-- 创建模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(255),
    api_key TEXT,
    base_url VARCHAR(500),
    model_name VARCHAR(255),
    temperature DOUBLE PRECISION,
    max_tokens INTEGER,
    is_default INTEGER DEFAULT 0,
    status INTEGER DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INTEGER DEFAULT 0
);

-- 插入默认模型配置
INSERT INTO model_config (id, name, provider, model_name, temperature, max_tokens, is_default, status, create_time, update_time, deleted)
VALUES (1, '默认配置', 'openai', 'gpt-4', 0.7, 2000, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;
