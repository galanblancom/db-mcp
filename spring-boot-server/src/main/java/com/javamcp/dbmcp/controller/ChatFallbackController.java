package com.javamcp.dbmcp.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.javamcp.dbmcp.service.OpenAIChatService;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback chat controller that provides helpful error messages
 * when OpenAI is not configured
 */
@RestController
@RequestMapping("/api/chat")
@ConditionalOnMissingBean(OpenAIChatService.class)
public class ChatFallbackController {

    private static final String ERROR_MESSAGE = 
        "OpenAI Chat is not configured. Please set the OPENAI_API_KEY environment variable or in application.properties. " +
        "You can get an API key from https://platform.openai.com/api-keys";

    @PostMapping
    public ResponseEntity<Map<String, Object>> chatNew(@RequestBody Map<String, String> request) {
        return createErrorResponse();
    }

    @PostMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> chatWithThread(
            @PathVariable String threadId,
            @RequestBody Map<String, String> request) {
        return createErrorResponse();
    }

    @GetMapping("/{threadId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String threadId) {
        return createErrorResponse();
    }

    @DeleteMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> clearThread(@PathVariable String threadId) {
        return createErrorResponse();
    }

    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> listThreads() {
        return createErrorResponse();
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", ERROR_MESSAGE);
        response.put("configured", false);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
