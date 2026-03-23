package com.wk.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wk.agent.entity.ModelConfig;

import java.util.List;

/**
 * 模型配置服务接口
 */
public interface ModelConfigService extends IService<ModelConfig> {

    /**
     * 获取默认配置
     */
    ModelConfig getDefaultConfig();

    /**
     * 设置默认配置
     */
    boolean setDefaultConfig(Long id);

    /**
     * 获取所有启用的配置
     */
    List<ModelConfig> getEnabledConfigs();

    /**
     * 创建配置
     */
    ModelConfig createConfig(ModelConfig config);

    /**
     * 更新配置
     */
    boolean updateConfig(ModelConfig config);

    /**
     * 删除配置
     */
    boolean deleteConfig(Long id);
}
