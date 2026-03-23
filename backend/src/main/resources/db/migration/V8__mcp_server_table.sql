-- MCP 服务器配置表
CREATE TABLE IF NOT EXISTS mcp_server (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    server_type VARCHAR(50) NOT NULL,
    config TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_enabled ON mcp_server(enabled);
CREATE INDEX IF NOT EXISTS idx_server_type ON mcp_server(server_type);

-- 添加 updated_at 自动更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_mcp_server_updated_at ON mcp_server;
CREATE TRIGGER update_mcp_server_updated_at
    BEFORE UPDATE ON mcp_server
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();