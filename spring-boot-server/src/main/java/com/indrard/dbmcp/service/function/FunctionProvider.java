package com.indrard.dbmcp.service.function;

import com.indrard.dbmcp.model.openai.FunctionDefinition;

import java.util.List;
import java.util.Map;

/**
 * Interface for providing AI-callable functions.
 * Implement this interface in your Spring Boot application to add custom functions
 * that can be called by the AI chat service.
 * 
 * Example:
 * <pre>
 * {@code
 * @Service
 * public class CustomFunctionProvider implements FunctionProvider {
 *     
 *     @Override
 *     public List<FunctionDefinition> getFunctionDefinitions() {
 *         // Return your custom function definitions
 *     }
 *     
 *     @Override
 *     public Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception {
 *         // Execute your custom functions
 *     }
 *     
 *     @Override
 *     public boolean supports(String functionName) {
 *         return "myCustomFunction".equals(functionName);
 *     }
 * }
 * }
 * </pre>
 */
public interface FunctionProvider {
    
    /**
     * Get all function definitions provided by this provider.
     * These will be exposed to the AI for function calling.
     * 
     * @return List of function definitions
     */
    List<FunctionDefinition> getFunctionDefinitions();
    
    /**
     * Execute a function with the given arguments.
     * 
     * @param functionName The name of the function to execute
     * @param arguments The function arguments as a map
     * @return The result of the function execution
     * @throws Exception if the function execution fails
     */
    Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception;
    
    /**
     * Check if this provider supports the given function name.
     * 
     * @param functionName The function name to check
     * @return true if this provider can execute the function
     */
    boolean supports(String functionName);
    
    /**
     * Get the priority of this provider. Higher priority providers are checked first.
     * Default priority is 0. Database functions have priority 100.
     * 
     * @return The provider priority
     */
    default int getPriority() {
        return 0;
    }
}
