package com.javamcp.dbmcp.service;

import com.javamcp.dbmcp.model.openai.FunctionDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
@ConditionalOnBean(OpenAiService.class)
public class OpenAIChatService {

    private final OpenAiService openAiService;
    private final FunctionCallHandler functionCallHandler;
    private final ObjectMapper objectMapper;

    @Value("${openai.model:gpt-4o}")
    private String model;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    @Value("${openai.max.tokens:2000}")
    private int maxTokens;

    @Value("${openai.conversation.max-history:50}")
    private int maxHistorySize;

    @Value("${openai.conversation.timeout-minutes:30}")
    private long conversationTimeoutMinutes;

    private static final int MAX_ITERATIONS = 10;
    private static final String SYSTEM_MESSAGE = 
            "You are a helpful database assistant. When users ask about database operations, " +
            "automatically call the appropriate functions to retrieve real data. " +
            "Format the results clearly and helpfully for the user. " +
            "Remember previous messages in the conversation and maintain context.";

    // Thread-safe conversation storage
    private final Map<String, ConversationThread> conversations = new ConcurrentHashMap<>();

    public OpenAIChatService(OpenAiService openAiService, FunctionCallHandler functionCallHandler) {
        this.openAiService = openAiService;
        this.functionCallHandler = functionCallHandler;
        this.objectMapper = new ObjectMapper();
        
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
        if (openAiService == null) {
            return new ChatResult(
                "OpenAI is not configured. Please set the OPENAI_API_KEY environment variable. " +
                "You can get an API key from https://platform.openai.com/api-keys",
                threadId
            );
        }

        try {
            // Get or create conversation thread
            ConversationThread thread = getOrCreateThread(threadId);
            thread.updateAccessTime();

            // Add user message to conversation history
            thread.messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

            // Trim history if it exceeds max size (keep system message + recent messages)
            if (thread.messages.size() > maxHistorySize) {
                List<ChatMessage> trimmed = new ArrayList<>();
                trimmed.add(thread.messages.get(0)); // Keep system message
                trimmed.addAll(thread.messages.subList(
                    thread.messages.size() - maxHistorySize + 1, 
                    thread.messages.size()
                ));
                thread.messages.clear();
                thread.messages.addAll(trimmed);
            }

            // Build functions in native OpenAI format
            List<ChatFunction> functions = buildNativeFunctions();

            // Use conversation history as messages
            List<ChatMessage> messages = new ArrayList<>(thread.messages);

            // Execute conversation with automatic function calling
            int iteration = 0;
            while (iteration < MAX_ITERATIONS) {
                iteration++;

                // Create chat completion request with functions
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(model)
                        .messages(messages)
                        .functions(functions)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build();

                System.out.println("=== Iteration " + iteration + " ===");
                
                // Get response from OpenAI
                ChatCompletionResult result = openAiService.createChatCompletion(request);
                ChatMessage responseMessage = result.getChoices().get(0).getMessage();
                
                // Check if OpenAI wants to call a function
                ChatFunctionCall functionCall = responseMessage.getFunctionCall();
                
                if (functionCall != null) {
                    // Function call requested
                    String functionName = functionCall.getName();
                    Object argumentsObj = functionCall.getArguments();
                    
                    System.out.println("Function call: " + functionName);
                    System.out.println("Arguments: " + argumentsObj);
                    
                    try {
                        // Parse arguments
                        Map<String, Object> arguments = parseArguments(argumentsObj);
                        
                        // Execute function
                        Object functionResult = functionCallHandler.executeFunction(functionName, arguments);
                        String resultJson = objectMapper.writeValueAsString(functionResult);
                        
                        System.out.println("Function result: " + resultJson.substring(0, Math.min(200, resultJson.length())) + "...");
                        
                        // Add assistant message and function result to conversation
                        messages.add(responseMessage);
                        thread.messages.add(responseMessage);
                        
                        ChatMessage functionResultMessage = new ChatMessage(ChatMessageRole.FUNCTION.value(), resultJson);
                        functionResultMessage.setName(functionName);
                        messages.add(functionResultMessage);
                        thread.messages.add(functionResultMessage);
                        
                        // Continue loop to let OpenAI process the result
                        continue;
                        
                    } catch (Exception e) {
                        System.err.println("Error executing function: " + e.getMessage());
                        e.printStackTrace();
                        
                        // Add error as function result
                        messages.add(responseMessage);
                        thread.messages.add(responseMessage);
                        
                        ChatMessage errorMessage = new ChatMessage(ChatMessageRole.FUNCTION.value(), 
                                "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}");
                        errorMessage.setName(functionName);
                        messages.add(errorMessage);
                        thread.messages.add(errorMessage);
                        continue;
                    }
                }
                
                // No function call - return the final response
                String content = responseMessage.getContent();
                System.out.println("Final response: " + (content != null ? content.substring(0, Math.min(100, content.length())) : "null"));
                
                // Add final assistant response to thread history
                thread.messages.add(responseMessage);
                
                return new ChatResult(content != null ? content : "No response from AI", threadId);
            }

            return new ChatResult("Max iterations reached. The conversation has been truncated.", threadId);

        } catch (Exception e) {
            System.err.println("Chat error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private List<ChatFunction> buildNativeFunctions() {
        List<FunctionDefinition> functionDefinitions = functionCallHandler.getFunctionDefinitions();
        List<ChatFunction> chatFunctions = new ArrayList<>();
        
        for (FunctionDefinition funcDef : functionDefinitions) {
            try {
                // Convert our FunctionDefinition to OpenAI's ChatFunction format
                // The library expects the parameters as a Map or Class
                ChatFunction chatFunction = ChatFunction.builder()
                        .name(funcDef.getName())
                        .description(funcDef.getDescription())
                        .executor(Object.class, obj -> {
                            // This executor won't be used since we handle execution manually
                            return null;
                        })
                        .build();
                chatFunctions.add(chatFunction);
            } catch (Exception e) {
                System.err.println("Error building function " + funcDef.getName() + ": " + e.getMessage());
            }
        }
        
        return chatFunctions;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object argumentsObj) throws Exception {
        if (argumentsObj instanceof String) {
            return objectMapper.readValue((String) argumentsObj, Map.class);
        } else if (argumentsObj instanceof Map) {
            return (Map<String, Object>) argumentsObj;
        } else {
            return objectMapper.convertValue(argumentsObj, Map.class);
        }
    }

    /**
     * Get conversation history for a thread
     */
    public List<ChatMessage> getConversationHistory(String threadId) {
        ConversationThread thread = conversations.get(threadId);
        return thread != null ? new ArrayList<>(thread.messages) : new ArrayList<>();
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
            thread.messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_MESSAGE));
            return thread;
        });
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
        final List<ChatMessage> messages;
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
