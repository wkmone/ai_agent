package com.wk.agent.factory;

import com.wk.agent.advisor.MyLoggerAdvisor;
import com.wk.agent.entity.ModelConfig;
import com.wk.agent.service.ModelConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamicChatClientFactory {

    @Autowired
    ModelConfigService modelConfigService;

    @Autowired
    private ChatMemory chatMemory;

    public ChatModel createChatModel(Long modelConfigId) {
        if (modelConfigId == null) {
            throw new IllegalArgumentException("modelConfigId不能为空");
        }
        ModelConfig config = modelConfigService.getById(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("未找到modelConfigId为 " + modelConfigId + " 的模型配置");
        }
        if (config.getModelName() == null || config.getBaseUrl() == null || config.getApiKey() == null) {
            throw new IllegalArgumentException("模型配置不完整，请检查modelName、baseUrl和apiKey是否都已设置");
        }
        return OpenAiChatModel.builder()
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModelName())
                        .temperature(config.getTemperature())
                        .build())
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(config.getBaseUrl())
                        .apiKey(config.getApiKey())
                        .build())
                .build();

//        return ChatClient.builder(chatModel)
//                .defaultAdvisors(
//                    MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                    MyLoggerAdvisor.builder()
//                            .showAvailableTools(true)
//                            .showSystemMessage(true)
//                            .build())
//                .build();
    }
}
