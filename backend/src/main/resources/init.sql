-- 创建数据库
CREATE DATABASE IF NOT EXISTS ai_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE ai_agent;

-- 创建对话表
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    name VARCHAR(255),
    system_prompt TEXT,
    model_name VARCHAR(255),
    status INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 创建消息表
CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    type INT NOT NULL,
    content TEXT NOT NULL,
    model_name VARCHAR(255),
    token_count BIGINT DEFAULT 0,
    status INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 创建Agent任务表
CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255),
    type VARCHAR(255) NOT NULL,
    content TEXT,
    params TEXT,
    status INT DEFAULT 0,
    result TEXT,
    start_time DATETIME,
    end_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 创建事实性记忆表（长期记忆类型1）
CREATE TABLE IF NOT EXISTS factual_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    fact_type VARCHAR(100),
    fact_content TEXT NOT NULL,
    source VARCHAR(255),
    importance DOUBLE DEFAULT 0.5,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    access_count INT DEFAULT 0
);

CREATE INDEX idx_factual_session ON factual_memory(session_id);
CREATE INDEX idx_factual_type ON factual_memory(fact_type);
CREATE INDEX idx_factual_importance ON factual_memory(importance);

-- 创建程序性记忆表（长期记忆类型2）
CREATE TABLE IF NOT EXISTS procedural_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    skill_description TEXT,
    steps TEXT NOT NULL,
    trigger_condition TEXT,
    success_rate DOUBLE DEFAULT 0.0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    execution_count INT DEFAULT 0
);

CREATE INDEX idx_procedural_session ON procedural_memory(session_id);
CREATE INDEX idx_procedural_skill ON procedural_memory(skill_name);

-- 创建语义记忆表（长期记忆类型3）
CREATE TABLE IF NOT EXISTS semantic_memory (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    concept VARCHAR(255) NOT NULL,
    definition TEXT NOT NULL,
    category VARCHAR(100),
    related_concepts TEXT,
    embedding TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    access_count INT DEFAULT 0
);

CREATE INDEX idx_semantic_session ON semantic_memory(session_id);
CREATE INDEX idx_semantic_concept ON semantic_memory(concept);
CREATE INDEX idx_semantic_category ON semantic_memory(category);