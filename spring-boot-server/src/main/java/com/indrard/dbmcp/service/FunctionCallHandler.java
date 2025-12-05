package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.service.function.FunctionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates function calling by delegating to registered FunctionProvider instances.
 * This allows for extensibility - client projects can add custom functions by implementing
 * the FunctionProvider interface.
 */
@Service
public class FunctionCallHandler {

    private final List<FunctionProvider> functionProviders;

    @Autowired
    public FunctionCallHandler(List<FunctionProvider> functionProviders) {
        // Sort by priority (higher priority first)
        this.functionProviders = functionProviders.stream()
                .sorted(Comparator.comparingInt(FunctionProvider::getPriority).reversed())
                .collect(Collectors.toList());
        
        System.out.println("INFO: Registered " + functionProviders.size() + " function provider(s)");
        functionProviders.forEach(provider -> 
            System.out.println("  - " + provider.getClass().getSimpleName() + 
                             " (priority: " + provider.getPriority() + ")")
        );
    }

    /**
     * Get all available functions from all registered providers.
     * 
     * @return Combined list of function definitions from all providers
     */
    public List<FunctionDefinition> getFunctionDefinitions() {
        List<FunctionDefinition> allFunctions = new ArrayList<>();
        
        for (FunctionProvider provider : functionProviders) {
            List<FunctionDefinition> providerFunctions = provider.getFunctionDefinitions();
            if (providerFunctions != null) {
                allFunctions.addAll(providerFunctions);
            }
        }
        
        return allFunctions;
    }

    /**
     * Execute a function by delegating to the appropriate provider.
     * Providers are checked in priority order (highest first).
     * 
     * @param functionName The name of the function to execute
     * @param arguments The function arguments
     * @return The result of function execution
     * @throws Exception if no provider supports the function or execution fails
     */
    public Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception {
        if (arguments == null) {
            arguments = new HashMap<>();
        }
        
        // Find the first provider that supports this function
        for (FunctionProvider provider : functionProviders) {
            if (provider.supports(functionName)) {
                return provider.executeFunction(functionName, arguments);
            }
        }
        
        // No provider found
        throw new IllegalArgumentException(
            "Unknown function: " + functionName + 
            ". No registered provider supports this function."
        );
    }
    
    /**
     * Get all registered function providers.
     * Useful for debugging or inspecting available providers.
     * 
     * @return List of registered providers in priority order
     */
    public List<FunctionProvider> getProviders() {
        return new ArrayList<>(functionProviders);
    }
}
