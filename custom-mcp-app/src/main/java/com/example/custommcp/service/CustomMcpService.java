package com.example.custommcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indrard.dbmcp.service.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Custom MCP Service that includes both base and custom tools
 * Extends the base MCP service functionality to discover custom tools
 */
@Service
@Slf4j
public class CustomMcpService {

    private final McpToolService mcpToolService;
    private final CustomToolsService customToolsService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CustomMcpService(McpToolService mcpToolService, CustomToolsService customToolsService) {
        this.mcpToolService = mcpToolService;
        this.customToolsService = customToolsService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all tools (base + custom)
     */
    public List<Map<String, Object>> getTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // Add base tools from McpToolService
        tools.addAll(getToolsFromService(mcpToolService, McpToolService.class));
        
        // Add custom tools from CustomToolsService
        tools.addAll(getCustomTools());
        
        log.info("Total MCP tools available: {} (base + custom)", tools.size());
        return tools;
    }

    /**
     * Get tools from a service class by scanning @Tool annotations
     */
    private List<Map<String, Object>> getToolsFromService(Object service, Class<?> serviceClass) {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        Method[] methods = serviceClass.getDeclaredMethods();
        for (Method method : methods) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                String name = method.getName();
                String description = toolAnnotation.description();
                
                Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();
                
                for (Parameter param : method.getParameters()) {
                    String paramName = param.getName();
                    Class<?> paramType = param.getType();
                    
                    Map<String, Object> paramSchema = new HashMap<>();
                    paramSchema.put("type", getJsonType(paramType));
                    paramSchema.put("description", "Parameter: " + paramName);
                    
                    properties.put(paramName, paramSchema);
                    
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

    /**
     * Get custom tools definitions
     */
    private List<Map<String, Object>> getCustomTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        
        // 1. getDatabaseSummary
        tools.add(createTool(
            "getDatabaseSummary",
            "Get a comprehensive summary of the database including total tables and rows",
            Map.of(),
            List.of()
        ));
        
        // 2. findLargeTables
        Map<String, Map<String, Object>> largeTablesProps = new LinkedHashMap<>();
        largeTablesProps.put("minRows", Map.of(
            "type", "integer",
            "description", "Minimum number of rows to filter tables"
        ));
        tools.add(createTool(
            "findLargeTables",
            "Find tables that have a row count greater than or equal to the specified minimum",
            largeTablesProps,
            List.of()
        ));
        
        // 3. searchTablesByPattern
        Map<String, Map<String, Object>> searchProps = new LinkedHashMap<>();
        searchProps.put("pattern", Map.of(
            "type", "string",
            "description", "SQL LIKE pattern to search for table names (e.g., 'GT_%')"
        ));
        tools.add(createTool(
            "searchTablesByPattern",
            "Search for tables matching a SQL LIKE pattern",
            searchProps,
            List.of("pattern")
        ));
        
        // 4. getTableStatisticsSummary
        Map<String, Map<String, Object>> statsProps = new LinkedHashMap<>();
        statsProps.put("tableName", Map.of(
            "type", "string",
            "description", "Name of the table to get statistics for"
        ));
        statsProps.put("schema", Map.of(
            "type", "string",
            "description", "Schema name (optional)"
        ));
        tools.add(createTool(
            "getTableStatisticsSummary",
            "Get detailed statistics for a specific table including row count, column count, and primary keys",
            statsProps,
            List.of("tableName")
        ));
        
        // 5. compareTableRowCounts
        Map<String, Map<String, Object>> compareProps = new LinkedHashMap<>();
        compareProps.put("table1", Map.of(
            "type", "string",
            "description", "First table name to compare"
        ));
        compareProps.put("table2", Map.of(
            "type", "string",
            "description", "Second table name to compare"
        ));
        compareProps.put("schema", Map.of(
            "type", "string",
            "description", "Schema name (optional)"
        ));
        tools.add(createTool(
            "compareTableRowCounts",
            "Compare row counts between two tables and show which is larger",
            compareProps,
            List.of("table1", "table2")
        ));
        
        return tools;
    }

    /**
     * Execute a tool by name
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        log.info("Executing tool: {} with arguments: {}", toolName, arguments);
        
        // Check if it's a custom tool
        if (isCustomTool(toolName)) {
            return executeCustomTool(toolName, arguments);
        }
        
        // Otherwise, execute base tool
        return executeBaseTool(toolName, arguments);
    }

    /**
     * Check if tool is a custom tool
     */
    private boolean isCustomTool(String toolName) {
        return toolName.equals("getDatabaseSummary") ||
               toolName.equals("findLargeTables") ||
               toolName.equals("searchTablesByPattern") ||
               toolName.equals("getTableStatisticsSummary") ||
               toolName.equals("compareTableRowCounts");
    }

    /**
     * Execute custom tool
     */
    private Map<String, Object> executeCustomTool(String toolName, Map<String, Object> arguments) {
        try {
            Object result;
            
            switch (toolName) {
                case "getDatabaseSummary":
                    result = customToolsService.getDatabaseSummary();
                    break;
                    
                case "findLargeTables":
                    Long minRows = arguments.get("minRows") != null 
                        ? convertToLong(arguments.get("minRows")) 
                        : null;
                    result = customToolsService.findLargeTables(minRows);
                    break;
                    
                case "searchTablesByPattern":
                    String pattern = (String) arguments.get("pattern");
                    result = customToolsService.searchTablesByPattern(pattern);
                    break;
                    
                case "getTableStatisticsSummary":
                    String tableName = (String) arguments.get("tableName");
                    String schema = (String) arguments.get("schema");
                    result = customToolsService.getTableStatisticsSummary(tableName, schema);
                    break;
                    
                case "compareTableRowCounts":
                    String table1 = (String) arguments.get("table1");
                    String table2 = (String) arguments.get("table2");
                    String compareSchema = (String) arguments.get("schema");
                    result = customToolsService.compareTableRowCounts(table1, table2, compareSchema);
                    break;
                    
                default:
                    throw new IllegalArgumentException("Unknown custom tool: " + toolName);
            }
            
            // Format result for MCP protocol
            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", objectMapper.writeValueAsString(result)
                    )
                )
            );
            
        } catch (Exception e) {
            log.error("Error executing custom tool: {}", toolName, e);
            return Map.of(
                "content", List.of(
                    Map.of(
                        "type", "text",
                        "text", "Error: " + e.getMessage()
                    )
                )
            );
        }
    }

    /**
     * Execute base tool from McpToolService
     */
    private Map<String, Object> executeBaseTool(String toolName, Map<String, Object> arguments) throws Exception {
        Method[] methods = McpToolService.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(toolName) && method.isAnnotationPresent(Tool.class)) {
                Parameter[] parameters = method.getParameters();
                Object[] args = new Object[parameters.length];
                
                for (int i = 0; i < parameters.length; i++) {
                    String paramName = parameters[i].getName();
                    Class<?> paramType = parameters[i].getType();
                    Object value = arguments.get(paramName);
                    
                    if (value != null) {
                        args[i] = convertValue(value, paramType);
                    } else {
                        args[i] = null;
                    }
                }
                
                Object result = method.invoke(mcpToolService, args);
                
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
        
        throw new IllegalArgumentException("Tool not found: " + toolName);
    }

    /**
     * Helper methods
     */
    private Map<String, Object> createTool(String name, String description, 
                                          Map<String, Map<String, Object>> properties,
                                          List<String> required) {
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        
        return tool;
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

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }
        
        return value;
    }

    private Long convertToLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }
}
