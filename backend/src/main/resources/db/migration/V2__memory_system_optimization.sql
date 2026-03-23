-- =====================================================
-- 记忆系统优化 - 数据库迁移脚本
-- 从5层记忆架构迁移到4种记忆类型架构
-- =====================================================

-- ==================== 1. 创建新表 ====================

-- 情景记忆表
CREATE TABLE IF NOT EXISTS episodic_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255),
    user_id VARCHAR(255),
    event_type VARCHAR(50),
    event_content TEXT,
    context TEXT,
    importance DOUBLE PRECISION,
    emotional_tone VARCHAR(50),
    timestamp TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    access_count INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_episodic_session ON episodic_memory(session_id);
CREATE INDEX IF NOT EXISTS idx_episodic_timestamp ON episodic_memory(timestamp);
CREATE INDEX IF NOT EXISTS idx_episodic_type ON episodic_memory(event_type);

-- 感知记忆表
CREATE TABLE IF NOT EXISTS perceptual_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255),
    user_id VARCHAR(255),
    modality VARCHAR(20),
    content TEXT,
    file_path VARCHAR(500),
    embedding TEXT,
    metadata TEXT,
    importance DOUBLE PRECISION,
    timestamp TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    access_count INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_perceptual_session ON perceptual_memory(session_id);
CREATE INDEX IF NOT EXISTS idx_perceptual_modality ON perceptual_memory(modality);

-- ==================== 2. 修改语义记忆表 ====================

-- 添加新字段
ALTER TABLE semantic_memory ADD COLUMN IF NOT EXISTS user_id VARCHAR(255);
ALTER TABLE semantic_memory ADD COLUMN IF NOT EXISTS memory_category VARCHAR(50) DEFAULT 'concept';
ALTER TABLE semantic_memory ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE semantic_memory ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION DEFAULT 0.5;

-- ==================== 3. 数据迁移 ====================

-- 迁移 factual_memory 数据到 semantic_memory
INSERT INTO semantic_memory (session_id, user_id, memory_category, content, importance, confidence, created_at, updated_at, access_count)
SELECT 
    session_id, 
    NULL as user_id,
    'fact' as memory_category,
    fact_content as content,
    importance,
    0.7 as confidence,
    created_at,
    updated_at,
    COALESCE(access_count, 0) as access_count
FROM factual_memory
WHERE NOT EXISTS (
    SELECT 1 FROM semantic_memory sm 
    WHERE sm.session_id = factual_memory.session_id 
    AND sm.content = factual_memory.fact_content
);

-- 迁移 procedural_memory 数据到 semantic_memory
INSERT INTO semantic_memory (session_id, user_id, memory_category, concept, content, importance, confidence, created_at, updated_at, access_count)
SELECT 
    session_id,
    NULL as user_id,
    'skill' as memory_category,
    skill_name as concept,
    skill_description as content,
    COALESCE(success_rate, 0.5) as importance,
    0.6 as confidence,
    created_at,
    updated_at,
    COALESCE(execution_count, 0) as access_count
FROM procedural_memory
WHERE NOT EXISTS (
    SELECT 1 FROM semantic_memory sm 
    WHERE sm.session_id = procedural_memory.session_id 
    AND sm.concept = procedural_memory.skill_name
);

-- ==================== 4. 清理旧表 ====================

-- 备份后删除旧表（请确认数据迁移成功后再执行）
-- DROP TABLE IF EXISTS factual_memory;
-- DROP TABLE IF EXISTS procedural_memory;

-- ==================== 5. 清理Spring AI ChatMemory相关表 ====================

-- 如果不再需要中期/长期记忆的ChatMemory存储，可以清理
-- 注意：这会删除chat_memory表中的数据
-- TRUNCATE TABLE chat_memory;

-- ==================== 6. 验证迁移结果 ====================

-- 查看迁移后的数据统计
SELECT 'episodic_memory' as table_name, COUNT(*) as count FROM episodic_memory
UNION ALL
SELECT 'semantic_memory' as table_name, COUNT(*) as count FROM semantic_memory
UNION ALL
SELECT 'perceptual_memory' as table_name, COUNT(*) as count FROM perceptual_memory;
