package com.example.custommcp.service;

import com.indrard.dbmcp.service.AIChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Custom AI Chat Service Wrapper
 * Wraps the base AIChatService to expose custom functions
 * 
 * NOTE: The base AIChatService already has access to all database functions.
 * Custom functions are available via CustomToolsService REST endpoints.
 * This service provides a convenient wrapper for the controller.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(AIChatService.class)
public class CustomAIChatService {

    private final AIChatService baseAIChatService;


    /**
     * Chat with AI - delegates to base service
     */
    public AIChatService.ChatResult chat(String userMessage) {
        log.info("Custom AI Chat: {}", userMessage);
        return baseAIChatService.chat(userMessage);
    }

    /**
     * Chat with AI in existing thread - delegates to base service
     */
    public AIChatService.ChatResult chat(String threadId, String userMessage) {
        log.info("Custom AI Chat (thread {}): {}", threadId, userMessage);
        return baseAIChatService.chat(threadId, userMessage);
    }

    /**
     * Get conversation history
     */
    public List<?> getConversationHistory(String threadId) {
        return baseAIChatService.getConversationHistory(threadId);
    }

    /**
     * Clear conversation
     */
    public void clearConversation(String threadId) {
        baseAIChatService.clearConversation(threadId);
    }

    /**
     * Get active conversations
     */
    public List<String> getActiveConversations() {
        return baseAIChatService.getActiveConversations();
    }
}
