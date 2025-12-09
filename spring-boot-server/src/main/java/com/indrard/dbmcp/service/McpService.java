package com.indrard.dbmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.service.function.AnnotationBasedFunctionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Service that bridges between MCP protocol and the annotation-based function provider.
 * Delegates tool discovery and execution to AnnotationBasedFunctionProvider.
 */
@Service
public class McpService {

    private final AnnotationBasedFunctionProvider functionProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpService(AnnotationBasedFunctionProvider functionProvider) {
        this.functionProvider = functionProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all available tools in MCP format.
     * Converts from FunctionDefinition to MCP tool schema.
     */
    public List<Map<String, Object>> getTools() {
        List<FunctionDefinition> functionDefinitions = functionProvider.getFunctionDefinitions();
        
        return functionDefinitions.stream()
            .map(this::convertToMcpTool)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert FunctionDefinition to MCP tool format
     */
    private Map<String, Object> convertToMcpTool(FunctionDefinition functionDef) {
        Map<String, Object> properties = new LinkedHashMap<>();
        
        if (functionDef.getParameters() != null && functionDef.getParameters().getProperties() != null) {
            functionDef.getParameters().getProperties().forEach((name, propDef) -> {
                Map<String, Object> prop = new HashMap<>();
                prop.put("type", propDef.getType());
                prop.put("description", propDef.getDescription());
                properties.put(name, prop);
            });
        }
        
        List<String> required = functionDef.getParameters() != null 
            ? functionDef.getParameters().getRequired() 
            : List.of();
        
        return Map.of(
            "name", functionDef.getName(),
            "description", functionDef.getDescription(),
            "inputSchema", Map.of(
                "type", "object",
                "properties", properties,
                "required", required
            )
        );
    }
    
    /**
     * Execute a tool by name with the given arguments.
     * Delegates to AnnotationBasedFunctionProvider and formats result for MCP protocol.
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (!functionProvider.supports(toolName)) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        
        // Execute the function
        Object result = functionProvider.executeFunction(toolName, arguments);
        
        // Format the result for MCP protocol
        return Map.of(
            "content", List.of(
                Map.of(
                    "type", "text",
                    "text", objectMapper.writeValueAsString(result)
                )
            )
        );
    }
}
