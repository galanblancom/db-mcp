package com.example.custommcp.controller;

import com.indrard.dbmcp.service.AIChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Chat Controller
 * Provides chat functionality using AIChatService (Ollama or OpenAI)
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class CustomChatController {

    private final AIChatService chatService;

    @Autowired
    public CustomChatController(AIChatService chatService) {
        this.chatService = chatService;
        System.out.println("CustomChatController initialized with AIChatService: " + (chatService != null));
    }

    /**
     * Start a new chat conversation or continue in default thread
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        try {
            System.out.println("CustomChatController.chat() called");
            System.out.println("chatService is null: " + (chatService == null));
            
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }

            System.out.println("About to call chatService.chat() with message: " + message);
            AIChatService.ChatResult chatResult = chatService.chat(message);
            System.out.println("chatService.chat() returned successfully");
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", chatResult.getResponse());
            result.put("threadId", chatResult.getThreadId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("Error in CustomChatController.chat(): " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Chat service error: " + e.getMessage());
            error.put("exception", e.getClass().getName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Chat in a specific thread
     */
    @PostMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> chatInThread(
            @PathVariable String threadId,
            @RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
            }

            AIChatService.ChatResult chatResult = chatService.chat(threadId, message);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", chatResult.getResponse());
            result.put("threadId", chatResult.getThreadId());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Chat service error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Get conversation history for a thread
     */
    @GetMapping("/{threadId}/history")
    public ResponseEntity<?> getHistory(@PathVariable String threadId) {
        try {
            return ResponseEntity.ok(chatService.getConversationHistory(threadId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error retrieving history: " + e.getMessage()));
        }
    }

    /**
     * List all active conversation threads
     */
    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> listThreads() {
        try {
            var threads = chatService.getActiveConversations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("count", threads.size());
            response.put("threads", threads);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error listing threads: " + e.getMessage()));
        }
    }
}
