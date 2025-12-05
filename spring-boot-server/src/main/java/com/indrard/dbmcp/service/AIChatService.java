package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.service.ai.AIChatProvider;
import com.indrard.dbmcp.service.ai.AIChatProvider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@ConditionalOnBean(AIChatProvider.class)
public class AIChatService {

    private final AIChatProvider aiProvider;
    private final FunctionCallHandler functionCallHandler;
    private final ObjectMapper objectMapper;

    @Value("${openai.conversation.max-history:50}")
    private int maxHistorySize;

    @Value("${openai.conversation.timeout-minutes:30}")
    private long conversationTimeoutMinutes;

    private static final int MAX_ITERATIONS = 10;
    private static final String SYSTEM_MESSAGE = 
            "You are a helpful database assistant. " +
            "When users tell you personal information (like their name), just acknowledge it and remember it in our conversation - do NOT try to store it in the database. " +
            "Only call database functions when users specifically ask about data IN the database tables (like listing tables, querying data, etc.). " +
            "When you do write SQL queries, always use proper syntax with single quotes around strings: WHERE name = 'value'. " +
            "When presenting database results, format them as clear, readable lists or tables - not as raw JSON descriptions. " +
            "For example, when listing tables, show table names with their row counts in a simple format. " +
            "Maintain conversation context across messages. " +
            "IMPORTANT: When calling functions, always use the exact parameter names as defined in camelCase format (e.g., contractNic, tableName, maxRows). Never convert parameter names to snake_case or change their capitalization. " +
            "CRITICAL: When a user asks to reformat, summarize, or transform previous results (e.g., 'format as HTML', 'make it a table', 'summarize that'), DO NOT call the function again. Instead, use the data from the previous function result that is already in the conversation history. Only call functions when NEW data is needed from the database.";

    // Thread-safe conversation storage
    private final Map<String, ConversationThread> conversations = new ConcurrentHashMap<>();

    public AIChatService(AIChatProvider aiProvider, FunctionCallHandler functionCallHandler) {
        this.aiProvider = aiProvider;
        this.functionCallHandler = functionCallHandler;
        this.objectMapper = new ObjectMapper();
        
        System.out.println("INFO: Chat service initialized with provider: " + aiProvider.getProviderName());
        
        // Start cleanup thread for expired conversations
        startConversationCleanup();
    }

    /**
     * Chat with a new conversation (no thread ID)
     */
    public ChatResult chat(String userMessage) {
        String threadId = UUID.randomUUID().toString();
        return chat(threadId, userMessage);
    }

    /**
     * Chat with an existing conversation thread
     */
    public ChatResult chat(String threadId, String userMessage) {
        if (aiProvider == null) {
            return new ChatResult(
                "AI provider is not configured. Please configure either OpenAI or Ollama.",
                threadId
            );
        }

        try {
            // Get or create conversation thread
            ConversationThread thread = getOrCreateThread(threadId);
            thread.updateAccessTime();

            // Add user message to conversation history
            thread.messages.add(new InternalChatMessage("user", userMessage));

            // Trim history if it exceeds max size (keep system message + recent messages)
            if (thread.messages.size() > maxHistorySize) {
                List<InternalChatMessage> trimmed = new ArrayList<>();
                trimmed.add(thread.messages.get(0)); // Keep system message
                trimmed.addAll(thread.messages.subList(
                    thread.messages.size() - maxHistorySize + 1, 
                    thread.messages.size()
                ));
                thread.messages.clear();
                thread.messages.addAll(trimmed);
            }

            // Build function definitions
            List<FunctionDefinition> functionDefinitions = functionCallHandler.getFunctionDefinitions();
            
            // Convert to provider-agnostic format
            List<AIChatProvider.FunctionDefinition> providerFunctions = functionDefinitions.stream()
                    .map(f -> new AIChatProvider.FunctionDefinition(f.getName(), f.getDescription(), convertParameters(f.getParameters())))
                    .collect(Collectors.toList());

            // Convert thread messages to provider format
            List<AIChatProvider.ChatMessage> providerMessages = thread.messages.stream()
                    .map(this::convertToProviderMessage)
                    .collect(Collectors.toList());

            // Execute conversation with automatic function calling
            int iteration = 0;
            while (iteration < MAX_ITERATIONS) {
                iteration++;
                System.out.println("=== Iteration " + iteration + " ===");
                
                // Get response from AI provider
                ChatResponse response = aiProvider.chat(providerMessages, providerFunctions);
                
                // Check if AI wants to call a function
                if (response.hasFunctionCall()) {
                    FunctionCall functionCall = response.getFunctionCall();
                    String functionName = functionCall.getName();
                    
                    System.out.println("Function call: " + functionName);
                    System.out.println("Arguments: " + functionCall.getArguments());
                    
                    try {
                        // Parse arguments
                        Map<String, Object> arguments = objectMapper.readValue(functionCall.getArguments(), Map.class);
                        
                        // Execute function
                        Object functionResult = functionCallHandler.executeFunction(functionName, arguments);
                        String resultJson = objectMapper.writeValueAsString(functionResult);
                        
                        System.out.println("Function result: " + resultJson + "...");
                        
                        // Add assistant message with function call
                        if (response.getContent() != null && !response.getContent().isEmpty()) {
                            AIChatProvider.ChatMessage assistantMsg = new AIChatProvider.ChatMessage("assistant", response.getContent());
                            assistantMsg.setFunctionCall(functionCall);
                            providerMessages.add(assistantMsg);
                            thread.messages.add(convertFromProviderMessage(assistantMsg));
                        }

                        // Add function result
                        AIChatProvider.ChatMessage functionResultMsg = new AIChatProvider.ChatMessage("function", resultJson);
                        functionResultMsg.setName(functionName);
                        providerMessages.add(functionResultMsg);
                        thread.messages.add(convertFromProviderMessage(functionResultMsg));
                        
                        // Continue loop to let AI process the result
                        continue;
                        
                    } catch (Exception e) {
                        System.err.println("Error executing function: " + e.getMessage());
                        e.printStackTrace();
                        
                        // Add error as function result
                        AIChatProvider.ChatMessage assistantMsg = new AIChatProvider.ChatMessage("assistant", null);
                        assistantMsg.setFunctionCall(functionCall);
                        providerMessages.add(assistantMsg);
                        thread.messages.add(convertFromProviderMessage(assistantMsg));
                        
                        // Get error message safely
                        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        if (e.getCause() != null && e.getCause().getMessage() != null) {
                            errorMessage += ": " + e.getCause().getMessage();
                        }
                        errorMessage = errorMessage.replace("\"", "\\\"").replace("\n", " ");
                        
                        AIChatProvider.ChatMessage errorMsg = new AIChatProvider.ChatMessage("function", 
                                "{\"error\": \"" + errorMessage + "\"}");
                        errorMsg.setName(functionName);
                        providerMessages.add(errorMsg);
                        thread.messages.add(convertFromProviderMessage(errorMsg));
                        continue;
                    }
                }
                
                // No function call - return the final response
                String content = response.getContent();
                System.out.println("Final response: " + (content != null ? content.substring(0, Math.min(100, content.length())) : "null"));
                
                // Add final assistant response to thread history
                AIChatProvider.ChatMessage finalMsg = new AIChatProvider.ChatMessage("assistant", content);
                thread.messages.add(convertFromProviderMessage(finalMsg));
                
                return new ChatResult(content != null ? content : "No response from AI", threadId);
            }

            return new ChatResult("Max iterations reached. The conversation has been truncated.", threadId);

        } catch (Exception e) {
            System.err.println("Chat error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Convert internal message to provider message format
     */
    private AIChatProvider.ChatMessage convertToProviderMessage(InternalChatMessage msg) {
        AIChatProvider.ChatMessage providerMsg = new AIChatProvider.ChatMessage(msg.role, msg.content);
        providerMsg.setName(msg.name);
        if (msg.functionCall != null) {
            providerMsg.setFunctionCall(new FunctionCall(msg.functionCall.name, msg.functionCall.arguments));
        }
        return providerMsg;
    }

    /**
     * Convert provider message to internal message format
     */
    private InternalChatMessage convertFromProviderMessage(AIChatProvider.ChatMessage msg) {
        InternalChatMessage internalMsg = new InternalChatMessage(msg.getRole(), msg.getContent());
        internalMsg.name = msg.getName();
        if (msg.getFunctionCall() != null) {
            internalMsg.functionCall = new InternalFunctionCall(
                    msg.getFunctionCall().getName(), 
                    msg.getFunctionCall().getArguments()
            );
        }
        return internalMsg;
    }

    /**
     * Internal message structure for conversation history
     */
    private static class InternalChatMessage {
        String role;
        String content;
        String name;
        InternalFunctionCall functionCall;

        InternalChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * Internal function call structure
     */
    private static class InternalFunctionCall {
        String name;
        String arguments;

        InternalFunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * Get conversation history for a thread
     */
    public List<AIChatProvider.ChatMessage> getConversationHistory(String threadId) {
        ConversationThread thread = conversations.get(threadId);
        if (thread == null) return new ArrayList<>();
        return thread.messages.stream()
                .map(this::convertToProviderMessage)
                .collect(Collectors.toList());
    }

    /**
     * Clear conversation history for a thread
     */
    public void clearConversation(String threadId) {
        conversations.remove(threadId);
    }

    /**
     * Get all active conversation thread IDs
     */
    public List<String> getActiveConversations() {
        return new ArrayList<>(conversations.keySet());
    }

    /**
     * Get or create a conversation thread
     */
    private ConversationThread getOrCreateThread(String threadId) {
        return conversations.computeIfAbsent(threadId, id -> {
            ConversationThread thread = new ConversationThread(id);
            // Add system message to new conversations
            thread.messages.add(new InternalChatMessage("system", SYSTEM_MESSAGE));
            return thread;
        });
    }

    /**
     * Convert function parameters to Map format
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertParameters(Object params) {
        if (params instanceof Map) {
            return (Map<String, Object>) params;
        }
        return objectMapper.convertValue(params, Map.class);
    }

    /**
     * Cleanup expired conversations
     */
    private void startConversationCleanup() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Check every minute
                    
                    long currentTime = Instant.now().toEpochMilli();
                    conversations.entrySet().removeIf(entry -> {
                        long age = currentTime - entry.getValue().lastAccessTime;
                        boolean expired = age > conversationTimeoutMinutes * 60 * 1000;
                        if (expired) {
                            System.out.println("Removing expired conversation: " + entry.getKey());
                        }
                        return expired;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("conversation-cleanup");
        cleanupThread.start();
    }

    /**
     * Inner class to hold conversation thread data
     */
    private static class ConversationThread {
        final String threadId;
        final List<InternalChatMessage> messages;
        long lastAccessTime;

        ConversationThread(String threadId) {
            this.threadId = threadId;
            this.messages = new ArrayList<>();
            this.lastAccessTime = Instant.now().toEpochMilli();
        }

        void updateAccessTime() {
            this.lastAccessTime = Instant.now().toEpochMilli();
        }
    }

    /**
     * Inner class to hold chat response with thread ID
     */
    public static class ChatResult {
        private final String response;
        private final String threadId;

        public ChatResult(String response, String threadId) {
            this.response = response;
            this.threadId = threadId;
        }

        public String getResponse() {
            return response;
        }

        public String getThreadId() {
            return threadId;
        }
    }
}
