package com.javamcp.dbmcp.service.ai;

import java.util.List;
import java.util.Map;

/**
 * Interface for AI chat providers (OpenAI, Ollama, etc.)
 */
public interface AiChatProvider {
    
    /**
     * Send a chat message and get response
     * 
     * @param messages List of chat messages in the conversation
     * @param functions List of available functions for function calling
     * @return AI response with function call information if applicable
     */
    ChatResponse chat(List<ChatMessage> messages, List<FunctionDefinition> functions);
    
    /**
     * Get the provider name
     */
    String getProviderName();
    
    /**
     * Chat message structure
     */
    class ChatMessage {
        private String role;
        private String content;
        private String name; // For function responses
        private FunctionCall functionCall; // For assistant function calls
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public FunctionCall getFunctionCall() { return functionCall; }
        public void setFunctionCall(FunctionCall functionCall) { this.functionCall = functionCall; }
    }
    
    /**
     * Function call structure
     */
    class FunctionCall {
        private String name;
        private String arguments; // JSON string
        
        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
    
    /**
     * Function definition structure
     */
    class FunctionDefinition {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        
        public FunctionDefinition(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    /**
     * Chat response structure
     */
    class ChatResponse {
        private String content;
        private FunctionCall functionCall;
        
        public ChatResponse(String content, FunctionCall functionCall) {
            this.content = content;
            this.functionCall = functionCall;
        }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public FunctionCall getFunctionCall() { return functionCall; }
        public void setFunctionCall(FunctionCall functionCall) { this.functionCall = functionCall; }
        public boolean hasFunctionCall() { return functionCall != null; }
    }
}
