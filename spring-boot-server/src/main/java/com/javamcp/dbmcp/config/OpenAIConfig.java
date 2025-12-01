package com.javamcp.dbmcp.config;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Bean
    @ConditionalOnProperty(name = "openai.api.key", matchIfMissing = false)
    public OpenAiService openAiService() {
        System.out.println("INFO: OpenAI API configured successfully.");
        return new OpenAiService(apiKey, Duration.ofSeconds(60));
    }
}
