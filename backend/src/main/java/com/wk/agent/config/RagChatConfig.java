package com.wk.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagChatConfig {

    @Bean
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(6)
                .similarityThreshold(0.7)
                .build();

        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .template("""
                        {query}

                        Context information is below.

                        ---------------------
                        {question_answer_context}
                        ---------------------

                        Given the context information and no prior knowledge, answer the query.

                        Follow these rules:
                        1. If the answer is not in the context, just say that you don't know.
                        2. Avoid statements like "Based on the context..." or "The provided information...".
                        """)
                .build();

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .promptTemplate(customPromptTemplate)
                .build();
    }

    @Bean
    public ChatClient qaChatClient(ChatModel chatModel, QuestionAnswerAdvisor questionAnswerAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(questionAnswerAdvisor)
                .build();
    }
}