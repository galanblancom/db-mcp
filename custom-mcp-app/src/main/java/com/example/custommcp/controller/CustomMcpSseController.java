package com.example.custommcp.controller;

import com.example.custommcp.service.CustomMcpService;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Custom MCP SSE Controller
 * Overrides the base SSE controller to expose custom tools
 */
@RestController
@Slf4j
public class CustomMcpSseController {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CustomMcpService customMcpService;
    private final com.indrard.dbmcp.service.ChatService chatService;

    public CustomMcpSseController(CustomMcpService customMcpService,
                                  com.indrard.dbmcp.service.ChatService chatService) {
        this.customMcpService = customMcpService;
        this.chatService = chatService;
        log.info("Custom MCP SSE Controller initialized with custom tools");
    }

    @GetMapping(path = "/mcp/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSse() {
        log.info("New SSE connection established");
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("SSE connection closed");
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.warn("SSE connection timeout");
        });
        emitter.onError((e) -> {
            emitters.remove(emitter);
            log.error("SSE connection error", e);
        });

        emitters.add(emitter);

        // Send initial connection event
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("endpoint")
                        .data("{\"endpoint\":\"/mcp/message\"}"));
                log.info("Initial SSE event sent");
            } catch (IOException e) {
                log.error("Error sending initial SSE event", e);
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

        log.info("MCP message received - method: {}, id: {}", method, id);

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
                log.warn("Unknown method: {}", method);
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
        
        log.info("MCP response sent for method: {}", method);
        return response;
    }

    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        log.info("Initializing MCP connection");
        return Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of()),
                "serverInfo", Map.of(
                        "name", "custom-mcp-server",
                        "version", "1.0.0"));
    }

    private Map<String, Object> handleToolsList(Map<String, Object> params) {
        List<Map<String, Object>> tools = customMcpService.getTools();
        log.info("Returning {} tools (base + custom)", tools.size());
        return Map.of("tools", tools);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        try {
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

            log.info("Executing tool: {} with arguments: {}", toolName, arguments);
            Map<String, Object> result = customMcpService.executeTool(toolName, arguments);
            log.info("Tool execution completed: {}", toolName);
            return result;
            
        } catch (Exception e) {
            log.error("Error executing tool", e);
            return Map.of(
                    "content", List.of(
                            Map.of(
                                    "type", "text",
                                    "text", "Error executing tool: " + e.getMessage())));
        }
    }

    private Map<String, Object> handleChatMessage(Map<String, Object> params) {
        String userMessage = (String) params.get("message");
        log.info("Processing chat message: {}", userMessage);

        // Use ChatService for intelligent responses
        String responseText = chatService.processMessage(userMessage);

        return Map.of(
                "message", responseText,
                "timestamp", System.currentTimeMillis());
    }
}
