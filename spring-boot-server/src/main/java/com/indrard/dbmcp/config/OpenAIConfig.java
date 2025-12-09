package com.indrard.dbmcp.config;

import com.indrard.dbmcp.service.ai.AIChatProvider;
import com.indrard.dbmcp.service.ai.OllamaChatProvider;
import com.indrard.dbmcp.service.ai.OpenAiChatProvider;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenAIConfig {

    @Value("${ai.provider:openai}")
    private String provider;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o}")
    private String openaiModel;

    @Value("${openai.temperature:0.7}")
    private double openaiTemperature;

    @Value("${openai.max.tokens:2000}")
    private int openaiMaxTokens;

    @Value("${ollama.api.url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.1}")
    private String ollamaModel;

    @Value("${ollama.temperature:0.7}")
    private double ollamaTemperature;

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "openai", matchIfMissing = true)
    public AIChatProvider openAiChatProvider() {
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("WARNING: OpenAI API key not configured. Ollama chat provider will be used if configured.");

            System.out.println("INFO: Configuring Ollama chat provider");
            System.out.println("INFO: Base URL: " + ollamaBaseUrl);
            System.out.println("INFO: Model: " + ollamaModel);
            
            return new OllamaChatProvider(ollamaBaseUrl, ollamaModel, ollamaTemperature);
        }
        
        System.out.println("INFO: Configuring OpenAI chat provider");
        System.out.println("INFO: Model: " + openaiModel);
        
        OpenAiService openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
        return new OpenAiChatProvider(openAiService, openaiModel, openaiTemperature, openaiMaxTokens);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
    public AIChatProvider ollamaChatProvider() {
        System.out.println("INFO: Configuring Ollama chat provider");
        System.out.println("INFO: Base URL: " + ollamaBaseUrl);
        System.out.println("INFO: Model: " + ollamaModel);
        
        return new OllamaChatProvider(ollamaBaseUrl, ollamaModel, ollamaTemperature);
    }
}
