package com.javamcp.dbmcp.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ollama implementation of AiChatProvider
 */
public class OllamaChatProvider implements AiChatProvider {
    
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public OllamaChatProvider(String baseUrl, String model, double temperature) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<FunctionDefinition> functions) {
        try {
            String url = baseUrl + "/api/chat";
            
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", convertMessages(messages));
            requestBody.put("stream", false);
            
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature);
            requestBody.put("options", options);
            
            // Add tools/functions if provided
            if (functions != null && !functions.isEmpty()) {
                requestBody.put("tools", convertFunctions(functions));
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                entity, 
                String.class
            );
            
            return parseResponse(response.getBody());
            
        } catch (Exception e) {
            System.err.println("Ollama error: " + e.getMessage());
            e.printStackTrace();
            return new ChatResponse("Error communicating with Ollama: " + e.getMessage(), null);
        }
    }
    
    @Override
    public String getProviderName() {
        return "Ollama (" + model + ")";
    }
    
    private List<Map<String, Object>> convertMessages(List<ChatMessage> messages) {
        return messages.stream().map(msg -> {
            Map<String, Object> m = new HashMap<>();
            
            // Convert "function" role to "tool" for Ollama
            String role = msg.getRole();
            if ("function".equals(role)) {
                role = "tool";
            }
            m.put("role", role);
            
            if (msg.getContent() != null) {
                m.put("content", msg.getContent());
            }
            
            // Handle function call in message
            if (msg.getFunctionCall() != null) {
                Map<String, Object> toolCall = new HashMap<>();
                toolCall.put("name", msg.getFunctionCall().getName());
                try {
                    toolCall.put("arguments", objectMapper.readValue(msg.getFunctionCall().getArguments(), Map.class));
                } catch (Exception e) {
                    toolCall.put("arguments", msg.getFunctionCall().getArguments());
                }
                m.put("tool_calls", List.of(toolCall));
            }
            
            return m;
        }).collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> convertFunctions(List<FunctionDefinition> functions) {
        return functions.stream().map(func -> {
            Map<String, Object> tool = new HashMap<>();
            tool.put("type", "function");
            
            Map<String, Object> function = new HashMap<>();
            function.put("name", func.getName());
            function.put("description", func.getDescription());
            
            // Ollama expects 'parameters' to be a proper JSON schema
            Map<String, Object> parameters = func.getParameters();
            if (parameters != null) {
                function.put("parameters", parameters);
            } else {
                // Default empty schema
                Map<String, Object> emptyParams = new HashMap<>();
                emptyParams.put("type", "object");
                emptyParams.put("properties", new HashMap<>());
                function.put("parameters", emptyParams);
            }
            
            tool.put("function", function);
            return tool;
        }).collect(Collectors.toList());
    }
    
    private ChatResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageNode = root.get("message");
            
            if (messageNode == null) {
                return new ChatResponse("No message in response", null);
            }
            
            // Extract content
            String content = messageNode.has("content") ? messageNode.get("content").asText() : null;
            
            // Extract function call from tool_calls
            FunctionCall functionCall = null;
            JsonNode toolCallsNode = messageNode.has("tool_calls") ? 
                messageNode.get("tool_calls") : 
                (root.has("tool_calls") ? root.get("tool_calls") : null);
            
            if (toolCallsNode != null && toolCallsNode.isArray() && toolCallsNode.size() > 0) {
                JsonNode firstCall = toolCallsNode.get(0);
                
                // Parse function call structure
                String name = null;
                JsonNode argumentsNode = null;
                
                if (firstCall.has("function")) {
                    JsonNode function = firstCall.get("function");
                    name = function.has("name") ? function.get("name").asText() : null;
                    argumentsNode = function.has("arguments") ? function.get("arguments") : null;
                } else if (firstCall.has("name")) {
                    name = firstCall.get("name").asText();
                    argumentsNode = firstCall.has("arguments") ? firstCall.get("arguments") : null;
                }
                
                if (name != null) {
                    String arguments = argumentsNode != null ? argumentsNode.toString() : "{}";
                    functionCall = new FunctionCall(name, arguments);
                }
            }
            
            return new ChatResponse(content, functionCall);
            
        } catch (Exception e) {
            System.err.println("Error parsing Ollama response: " + e.getMessage());
            e.printStackTrace();
            return new ChatResponse("Error parsing response: " + e.getMessage(), null);
        }
    }
}
