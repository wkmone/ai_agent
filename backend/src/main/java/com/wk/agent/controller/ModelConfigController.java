package com.wk.agent.controller;

import com.wk.agent.entity.ModelConfig;
import com.wk.agent.service.ModelConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型配置控制器
 */
@RestController
@RequestMapping("/api/v1/model-config")
public class ModelConfigController {

    @Autowired
    private ModelConfigService modelConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping
    public ResponseEntity<List<ModelConfig>> getAllConfigs() {
        List<ModelConfig> configs = modelConfigService.list();
        return ResponseEntity.ok(configs);
    }

    /**
     * 获取默认配置
     */
    @GetMapping("/default")
    public ResponseEntity<ModelConfig> getDefaultConfig() {
        ModelConfig config = modelConfigService.getDefaultConfig();
        return ResponseEntity.ok(config);
    }

    /**
     * 获取所有启用的配置
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<ModelConfig>> getEnabledConfigs() {
        List<ModelConfig> configs = modelConfigService.getEnabledConfigs();
        return ResponseEntity.ok(configs);
    }

    /**
     * 创建新配置
     */
    @PostMapping
    public ResponseEntity<ModelConfig> createConfig(@RequestBody ModelConfig config) {
        ModelConfig createdConfig = modelConfigService.createConfig(config);
        return ResponseEntity.ok(createdConfig);
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ResponseEntity<Boolean> updateConfig(@PathVariable Long id, @RequestBody ModelConfig config) {
        config.setId(id);
        boolean result = modelConfigService.updateConfig(config);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteConfig(@PathVariable Long id) {
        boolean result = modelConfigService.deleteConfig(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 设置为默认配置
     */
    @PutMapping("/{id}/default")
    public ResponseEntity<Boolean> setDefaultConfig(@PathVariable Long id) {
        boolean result = modelConfigService.setDefaultConfig(id);
        return ResponseEntity.ok(result);
    }
}
