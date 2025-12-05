package com.example.custommcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.model.openai.FunctionDefinition.PropertyDefinition;
import com.indrard.dbmcp.service.FunctionCallHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom Function Call Handler
 * Extends the base function handler to add custom tools for AI
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomFunctionCallHandler {

    private final FunctionCallHandler baseFunctionCallHandler;
    private final CustomToolsService customToolsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get all function definitions including base and custom functions
     */
    public List<FunctionDefinition> getAllFunctionDefinitions() {
        List<FunctionDefinition> allFunctions = new ArrayList<>();
        
        // Add base functions from db-mcp library
        allFunctions.addAll(baseFunctionCallHandler.getFunctionDefinitions());
        
        // Add custom functions
        allFunctions.addAll(getCustomFunctionDefinitions());
        
        return allFunctions;
    }

    /**
     * Define custom functions for AI
     */
    public List<FunctionDefinition> getCustomFunctionDefinitions() {
        List<FunctionDefinition> functions = new ArrayList<>();

        // getDatabaseSummary
        functions.add(createFunctionDefinition(
                "getDatabaseSummary",
                "Gets a comprehensive summary of the database including total table count and total row count across all tables.",
                Map.of(),
                List.of()
        ));

        // findLargeTables
        functions.add(createFunctionDefinition(
                "findLargeTables",
                "Finds and returns tables with row count above a specified threshold, sorted by row count descending.",
                Map.of(
                        "minRows", new PropertyDefinition("integer", "Minimum row count threshold (default: 1000)")
                ),
                List.of()
        ));

        // searchTablesByPattern
        functions.add(createFunctionDefinition(
                "searchTablesByPattern",
                "Searches for tables matching a specific name pattern using SQL LIKE syntax (use % as wildcard).",
                Map.of(
                        "pattern", new PropertyDefinition("string", "SQL LIKE pattern to match table names (e.g., 'GT_%')")
                ),
                List.of("pattern")
        ));

        // getTableStatisticsSummary
        functions.add(createFunctionDefinition(
                "getTableStatisticsSummary",
                "Gets detailed statistics for a specific table including column count, row count, and primary key information.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to get statistics for"),
                        "schema", new PropertyDefinition("string", "Optional schema name")
                ),
                List.of("tableName")
        ));

        // compareTableRowCounts
        functions.add(createFunctionDefinition(
                "compareTableRowCounts",
                "Compares row counts between two tables and returns which table is larger and the difference.",
                Map.of(
                        "table1", new PropertyDefinition("string", "Name of the first table"),
                        "table2", new PropertyDefinition("string", "Name of the second table"),
                        "schema", new PropertyDefinition("string", "Optional schema name for both tables")
                ),
                List.of("table1", "table2")
        ));

        return functions;
    }

    /**
     * Execute a custom function call
     */
    public Object executeCustomFunction(String functionName, Map<String, Object> arguments) {
        log.info("Executing custom function: {} with arguments: {}", functionName, arguments);

        try {
            return switch (functionName) {
                case "getDatabaseSummary" -> customToolsService.getDatabaseSummary();
                
                case "findLargeTables" -> {
                    Long minRows = arguments.containsKey("minRows") 
                            ? ((Number) arguments.get("minRows")).longValue() 
                            : 1000L;
                    yield customToolsService.findLargeTables(minRows);
                }
                
                case "searchTablesByPattern" -> {
                    String pattern = (String) arguments.get("pattern");
                    yield customToolsService.searchTablesByPattern(pattern);
                }
                
                case "getTableStatisticsSummary" -> {
                    String tableName = (String) arguments.get("tableName");
                    String schema = (String) arguments.get("schema");
                    yield customToolsService.getTableStatisticsSummary(tableName, schema);
                }
                
                case "compareTableRowCounts" -> {
                    String table1 = (String) arguments.get("table1");
                    String table2 = (String) arguments.get("table2");
                    String schema = (String) arguments.get("schema");
                    yield customToolsService.compareTableRowCounts(table1, table2, schema);
                }
                
                default -> {
                    log.warn("Unknown custom function: {}", functionName);
                    yield Map.of("error", "Unknown custom function: " + functionName);
                }
            };
        } catch (Exception e) {
            log.error("Error executing custom function: {}", functionName, e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Execute any function (base or custom)
     */
    public Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception {
        // Check if it's a custom function
        List<String> customFunctionNames = getCustomFunctionDefinitions().stream()
                .map(FunctionDefinition::getName)
                .toList();
        
        if (customFunctionNames.contains(functionName)) {
            return executeCustomFunction(functionName, arguments);
        }
        
        // Otherwise, delegate to base function handler
        return baseFunctionCallHandler.executeFunction(functionName, arguments);
    }

    /**
     * Helper method to create function definitions
     */
    private FunctionDefinition createFunctionDefinition(
            String name,
            String description,
            Map<String, PropertyDefinition> properties,
            List<String> required) {
        
        FunctionDefinition.FunctionParameters parameters = new FunctionDefinition.FunctionParameters(
                properties,
                required
        );
        
        return new FunctionDefinition(name, description, parameters);
    }
}
