package com.wk.agent.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.embedding.api-key:EMPTY}")
    private String embeddingApiKey;

    @Value("${spring.ai.embedding.model:text-embedding-v4}")
    private String embeddingModelName;

    @Bean
    public OpenAiApi embeddingOpenAiApi() {
        return OpenAiApi.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(OpenAiApi embeddingOpenAiApi) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .build();
        return new OpenAiEmbeddingModel(embeddingOpenAiApi, MetadataMode.EMBED, options);
    }
}
