package com.javamcp.dbmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Service
public class McpService {

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpService(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> getTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // Dynamically discover all @Tool annotated methods
        Method[] methods = McpToolService.class.getDeclaredMethods();
        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                String name = method.getName();
                String description = toolAnnotation.description();
                
                // Build parameters from method signature
                Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                
                for (Parameter param : method.getParameters()) {
                    String paramName = param.getName();
                    Class<?> paramType = param.getType();
                    
                    Map<String, Object> paramSchema = new HashMap<>();
                    paramSchema.put("type", getJsonType(paramType));
                    paramSchema.put("description", "Parameter: " + paramName);
                    
                    properties.put(paramName, paramSchema);
                    
                    // Mark as required if not Optional or has primitive wrapper
                    if (!paramType.equals(Integer.class) && !paramType.equals(Boolean.class) 
                        && !paramType.equals(String.class) && paramType.isPrimitive()) {
                        required.add(paramName);
                    }
                }
                
                tools.add(createTool(name, description, properties, required));
            }
        }
        
        return tools;
    }
    
    private String getJsonType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == int.class || clazz == Long.class || clazz == long.class) return "integer";
        if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
        if (clazz == Double.class || clazz == double.class || clazz == Float.class || clazz == float.class) return "number";
        if (List.class.isAssignableFrom(clazz)) return "array";
        if (Map.class.isAssignableFrom(clazz)) return "object";
        return "object";
    }

    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        // Find the method by name
        Method[] methods = McpToolService.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(toolName) && method.isAnnotationPresent(Tool.class)) {
                // Prepare arguments for method invocation
                Parameter[] parameters = method.getParameters();
                Object[] args = new Object[parameters.length];
                
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = parameters[i].getName();
                    Class<?> paramType = parameters[i].getType();
                    Object value = arguments.get(paramName);
                    
                    // Convert value to appropriate type
                    if (value != null) {
                        args[i] = convertValue(value, paramType);
                    } else {
                        args[i] = null;
                    }
                }
                
                // Invoke the method
                Object result = method.invoke(mcpToolService, args);
                
                // Format the result
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
        
        throw new IllegalArgumentException("Unknown tool: " + toolName);
    }
    
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        
        // Handle basic conversions
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        } else if (List.class.isAssignableFrom(targetType)) {
            return value instanceof List ? value : List.of(value);
        } else if (Map.class.isAssignableFrom(targetType)) {
            return value instanceof Map ? value : Map.of();
        } else {
            // For complex objects, try to convert via ObjectMapper
            return objectMapper.convertValue(value, targetType);
        }
    }

    private Map<String, Object> createTool(String name, String description,
            Map<String, Map<String, Object>> properties,
            List<String> required) {
        return Map.of(
                "name", name,
                "description", description,
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", properties,
                        "required", required));
    }
}
