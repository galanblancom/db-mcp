package com.javamcp.dbmcp.service.ai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI implementation of AiChatProvider
 */
public class OpenAiChatProvider implements AiChatProvider {
    
    private final OpenAiService openAiService;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final ObjectMapper objectMapper;
    
    public OpenAiChatProvider(OpenAiService openAiService, String model, double temperature, int maxTokens) {
        this.openAiService = openAiService;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<FunctionDefinition> functions) {
        try {
            // Convert messages
            List<com.theokanning.openai.completion.chat.ChatMessage> openAiMessages = 
                messages.stream().map(this::convertMessage).collect(Collectors.toList());
            
            // Build request
            ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(openAiMessages)
                    .temperature(temperature)
                    .maxTokens(maxTokens);
            
            // Add functions if provided
            if (functions != null && !functions.isEmpty()) {
                List<ChatFunction> chatFunctions = functions.stream()
                        .map(this::convertFunction)
                        .collect(Collectors.toList());
                builder.functions(chatFunctions);
            }
            
            ChatCompletionRequest request = builder.build();
            
            // Get response
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            com.theokanning.openai.completion.chat.ChatMessage responseMessage = 
                    result.getChoices().get(0).getMessage();
            
            // Convert response
            String content = responseMessage.getContent();
            FunctionCall functionCall = null;
            
            if (responseMessage.getFunctionCall() != null) {
                com.theokanning.openai.completion.chat.ChatFunctionCall openAiFuncCall = 
                        responseMessage.getFunctionCall();
                String arguments = objectMapper.writeValueAsString(openAiFuncCall.getArguments());
                functionCall = new FunctionCall(openAiFuncCall.getName(), arguments);
            }
            
            return new ChatResponse(content, functionCall);
            
        } catch (Exception e) {
            System.err.println("OpenAI error: " + e.getMessage());
            e.printStackTrace();
            return new ChatResponse("Error communicating with OpenAI: " + e.getMessage(), null);
        }
    }
    
    @Override
    public String getProviderName() {
        return "OpenAI (" + model + ")";
    }
    
    private com.theokanning.openai.completion.chat.ChatMessage convertMessage(ChatMessage msg) {
        com.theokanning.openai.completion.chat.ChatMessage openAiMsg = 
                new com.theokanning.openai.completion.chat.ChatMessage(msg.getRole(), msg.getContent());
        
        if (msg.getName() != null) {
            openAiMsg.setName(msg.getName());
        }
        
        if (msg.getFunctionCall() != null) {
            com.theokanning.openai.completion.chat.ChatFunctionCall funcCall = 
                    new com.theokanning.openai.completion.chat.ChatFunctionCall();
            funcCall.setName(msg.getFunctionCall().getName());
            try {
                funcCall.setArguments(objectMapper.readTree(msg.getFunctionCall().getArguments()));
            } catch (Exception e) {
                // If parsing fails, set as empty object
                funcCall.setArguments(objectMapper.createObjectNode());
            }
            openAiMsg.setFunctionCall(funcCall);
        }
        
        return openAiMsg;
    }
    
    private ChatFunction convertFunction(FunctionDefinition func) {
        return ChatFunction.builder()
                .name(func.getName())
                .description(func.getDescription())
                .executor(Object.class, obj -> null) // Executor not used, handled manually
                .build();
    }
}
