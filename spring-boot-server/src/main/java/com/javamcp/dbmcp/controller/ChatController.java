package com.javamcp.dbmcp.controller;

import com.javamcp.dbmcp.service.OpenAIChatService;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for chat functionality with conversation thread support
 * Only available when OpenAI is configured
 */
@RestController
@RequestMapping("/api/chat")
@ConditionalOnBean(OpenAIChatService.class)
public class ChatController {

    private final OpenAIChatService chatService;

    @Autowired
    public ChatController(OpenAIChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Send a message without thread ID (creates new conversation)
     * POST /api/chat
     * Body: { "message": "Your message here" }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Message is required"));
        }

        try {
            OpenAIChatService.ChatResult result = chatService.chat(message);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Send a message with thread ID (maintains conversation)
     * POST /api/chat/{threadId}
     * Body: { "message": "Your message here" }
     */
    @PostMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> chatWithThread(
            @PathVariable String threadId,
            @RequestBody Map<String, String> request) {
        
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Message is required"));
        }

        try {
            OpenAIChatService.ChatResult result = chatService.chat(threadId, message);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Get conversation history for a thread
     * GET /api/chat/{threadId}/history
     */
    @GetMapping("/{threadId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String threadId) {
        try {
            List<ChatMessage> history = chatService.getConversationHistory(threadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("threadId", threadId);
            response.put("messageCount", history.size());
            response.put("messages", history);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Clear conversation history for a thread
     * DELETE /api/chat/{threadId}
     */
    @DeleteMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> clearThread(@PathVariable String threadId) {
        try {
            chatService.clearConversation(threadId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Conversation cleared");
            response.put("threadId", threadId);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * List all active conversation threads
     * GET /api/chat/threads
     */
    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> listThreads() {
        try {
            List<String> threads = chatService.getActiveConversations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("count", threads.size());
            response.put("threads", threads);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Error: " + e.getMessage()));
        }
    }

    private Map<String, Object> createSuccessResponse(String message, String threadId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("response", message);
        if (threadId != null) {
            response.put("threadId", threadId);
        }
        return response;
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }
}
