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
    @ConditionalOnProperty(name = "openai.api.key")
    public OpenAiService openAiService() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println(
                    "WARNING: OpenAI API key not configured. AI chat will not work. Set OPENAI_API_KEY environment variable.");
            return null;
        }

        System.out.println("INFO: OpenAI API configured successfully.");
        return new OpenAiService(apiKey, Duration.ofSeconds(60));
    }
}
