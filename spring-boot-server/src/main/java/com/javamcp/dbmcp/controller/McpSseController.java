package com.javamcp.dbmcp.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class McpSseController {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final com.javamcp.dbmcp.service.McpService mcpService;
    private final com.javamcp.dbmcp.service.ChatService chatService;

    public McpSseController(com.javamcp.dbmcp.service.McpService mcpService,
            com.javamcp.dbmcp.service.ChatService chatService) {
        this.mcpService = mcpService;
        this.chatService = chatService;
    }

    @GetMapping(path = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSse() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        emitters.add(emitter);

        // Send initial connection event
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("endpoint")
                        .data("{\"endpoint\":\"/mcp/message\"}"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/mcp/message")
    public Map<String, Object> handleMessage(@RequestBody Map<String, Object> message) {
        String method = (String) message.get("method");
        Object id = message.get("id");
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Map.of());

        // Route to appropriate handler based on method
        Map<String, Object> result;
        switch (method != null ? method : "") {
            case "initialize":
                result = handleInitialize(params);
                break;
            case "tools/list":
                result = handleToolsList(params);
                break;
            case "tools/call":
                result = handleToolsCall(params);
                break;
            case "chat/message":
                result = handleChatMessage(params);
                break;
            default:
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", id);
                errorResponse.put("error", Map.of(
                        "code", -32601,
                        "message", "Method not found: " + method));
                return errorResponse;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "db-mcp-server",
                        "version", "1.0.0"));
    }

    private Map<String, Object> handleToolsList(Map<String, Object> params) {
        return Map.of("tools", mcpService.getTools());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        try {
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

            return mcpService.executeTool(toolName, arguments);
        } catch (Exception e) {
            return Map.of(
                    "content", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", "Error executing tool: " + e.getMessage())));
        }
    }

    private Map<String, Object> handleChatMessage(Map<String, Object> params) {
        String userMessage = (String) params.get("message");

        // Use ChatService for intelligent responses
        String responseText = chatService.processMessage(userMessage);

        return Map.of(
                "message", responseText,
                "timestamp", System.currentTimeMillis());
    }
}
