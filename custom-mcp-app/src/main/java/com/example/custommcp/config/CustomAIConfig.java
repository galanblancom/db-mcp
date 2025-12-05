package com.example.custommcp.config;

import com.indrard.dbmcp.service.AIChatService;
import com.indrard.dbmcp.service.FunctionCallHandler;
import com.indrard.dbmcp.service.ai.AIChatProvider;
import com.indrard.dbmcp.service.ai.OllamaChatProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit AI configuration for custom MCP app
 * Creates beans directly without conditionals to avoid bean detection timing issues
 */
@Configuration
public class CustomAIConfig {

    @Value("${ollama.base.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.2}")
    private String ollamaModel;

    @Value("${ollama.temperature:0.7}")
    private double ollamaTemperature;

    @Bean
    public AIChatProvider aiChatProvider() {
        System.out.println("CustomAIConfig: Creating Ollama chat provider");
        System.out.println("CustomAIConfig: Base URL: " + ollamaBaseUrl);
        System.out.println("CustomAIConfig: Model: " + ollamaModel);
        return new OllamaChatProvider(ollamaBaseUrl, ollamaModel, ollamaTemperature);
    }

    @Bean
    public AIChatService aiChatService(AIChatProvider aiChatProvider, FunctionCallHandler functionCallHandler) {
        System.out.println("CustomAIConfig: Creating AIChatService");
        return new AIChatService(aiChatProvider, functionCallHandler);
    }
}
