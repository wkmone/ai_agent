package com.wk.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wk.agent.entity.ModelConfig;
import com.wk.agent.mapper.ModelConfigMapper;
import com.wk.agent.service.ModelConfigService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型配置服务实现类
 */
@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Override
    public ModelConfig getDefaultConfig() {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getIsDefault, 1)
               .eq(ModelConfig::getStatus, 1)
               .eq(ModelConfig::getDeleted, 0);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    public boolean setDefaultConfig(Long id) {
        try {
            // 先将所有配置的isDefault设置为0
            ModelConfig updateConfig = new ModelConfig();
            updateConfig.setIsDefault(0);
            update(updateConfig, new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getDeleted, 0));

            // 再将指定配置的isDefault设置为1
            updateConfig.setId(id);
            updateConfig.setIsDefault(1);
            return updateById(updateConfig);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ModelConfig> getEnabledConfigs() {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getStatus, 1)
               .eq(ModelConfig::getDeleted, 0)
               .orderByDesc(ModelConfig::getIsDefault)
               .orderByDesc(ModelConfig::getUpdateTime);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public ModelConfig createConfig(ModelConfig config) {
        config.setCreateTime(LocalDateTime.now())
              .setUpdateTime(LocalDateTime.now())
              .setStatus(1)
              .setDeleted(0);

        // 如果是第一个配置，设置为默认
        if (count(new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getDeleted, 0)) == 0) {
            config.setIsDefault(1);
        } else {
            config.setIsDefault(0);
        }

        save(config);
        return config;
    }

    @Override
    public boolean updateConfig(ModelConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }

    @Override
    public boolean deleteConfig(Long id) {
        ModelConfig config = new ModelConfig();
        config.setId(id)
              .setDeleted(1)
              .setUpdateTime(LocalDateTime.now());
        return updateById(config);
    }
}
