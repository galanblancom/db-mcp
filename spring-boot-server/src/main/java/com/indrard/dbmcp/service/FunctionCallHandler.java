package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.openai.FunctionDefinition;
import com.indrard.dbmcp.model.openai.FunctionDefinition.FunctionParameters;
import com.indrard.dbmcp.model.openai.FunctionDefinition.PropertyDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Service
public class FunctionCallHandler {

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FunctionCallHandler(McpToolService mcpToolService) {
        this.mcpToolService = mcpToolService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all available functions as OpenAI function definitions
     */
    public List<FunctionDefinition> getFunctionDefinitions() {
        List<FunctionDefinition> functions = new ArrayList<>();

        // runQuery
        functions.add(createFunctionDefinition(
                "runQuery",
                "Executes a SELECT or WITH query against the database and returns the results.",
                Map.of(
                        "sql", new PropertyDefinition("string", "The SQL SELECT query to execute"),
                        "maxRows", new PropertyDefinition("integer", "Maximum number of rows to return (default: 1000)"),
                        "excludeLargeColumns", new PropertyDefinition("boolean", "Whether to exclude large text/blob columns (default: false)")
                ),
                List.of("sql")
        ));

        // listTables
        functions.add(createFunctionDefinition(
                "listTables",
                "Lists all tables accessible by the current user with row counts.",
                Map.of(
                        "schema", new PropertyDefinition("string", "Optional schema name to filter tables"),
                        "pattern", new PropertyDefinition("string", "Optional pattern to filter table names (SQL LIKE pattern)")
                ),
                List.of()
        ));

        // getTableInfo
        functions.add(createFunctionDefinition(
                "getTableInfo",
                "Retrieves detailed information about a table including columns, data types, constraints, primary keys, and foreign keys.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to inspect"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)")
                ),
                List.of("tableName")
        ));

        // listSchemas
        functions.add(createFunctionDefinition(
                "listSchemas",
                "Lists all schemas/databases accessible in the current database instance.",
                Map.of(),
                List.of()
        ));

        // listViews
        functions.add(createFunctionDefinition(
                "listViews",
                "Lists all views accessible by the current user.",
                Map.of(
                        "schema", new PropertyDefinition("string", "Optional schema name to filter views"),
                        "pattern", new PropertyDefinition("string", "Optional pattern to filter view names (SQL LIKE pattern)")
                ),
                List.of()
        ));

        // getViewDefinition
        functions.add(createFunctionDefinition(
                "getViewDefinition",
                "Retrieves the SQL definition of a view.",
                Map.of(
                        "viewName", new PropertyDefinition("string", "Name of the view to inspect"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)")
                ),
                List.of("viewName")
        ));

        // getRowCount
        functions.add(createFunctionDefinition(
                "getRowCount",
                "Gets the total number of rows in a table quickly without retrieving data.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to count rows"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)"),
                        "whereClause", new PropertyDefinition("string", "Optional WHERE clause to filter rows (without 'WHERE' keyword)")
                ),
                List.of("tableName")
        ));

        // getIndexes
        functions.add(createFunctionDefinition(
                "getIndexes",
                "Retrieves index information for a table including column names, uniqueness, and index type.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to get indexes for"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)")
                ),
                List.of("tableName")
        ));

        // getForeignKeys
        functions.add(createFunctionDefinition(
                "getForeignKeys",
                "Retrieves foreign key relationships for a table including referenced tables, columns, and cascade rules.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to get foreign keys for"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)")
                ),
                List.of("tableName")
        ));

        // listStoredProcedures
        functions.add(createFunctionDefinition(
                "listStoredProcedures",
                "Lists all stored procedures and functions accessible in the database.",
                Map.of(
                        "schema", new PropertyDefinition("string", "Optional schema name to filter procedures"),
                        "pattern", new PropertyDefinition("string", "Optional pattern to filter procedure names (SQL LIKE pattern)")
                ),
                List.of()
        ));

        // getTableStatistics
        functions.add(createFunctionDefinition(
                "getTableStatistics",
                "Retrieves advanced table statistics including size in MB, index size, row count, and last analyzed date.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to get statistics for"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)")
                ),
                List.of("tableName")
        ));

        // explainQuery
        functions.add(createFunctionDefinition(
                "explainQuery",
                "Analyzes a query's execution plan without actually executing it. Shows estimated costs and rows.",
                Map.of(
                        "sql", new PropertyDefinition("string", "The SQL query to analyze")
                ),
                List.of("sql")
        ));

        // sampleTableData
        functions.add(createFunctionDefinition(
                "sampleTableData",
                "Quickly preview table data without writing SQL. Supports random sampling.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to sample"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)"),
                        "limit", new PropertyDefinition("integer", "Number of rows to return (default: 10)"),
                        "random", new PropertyDefinition("boolean", "Whether to use random sampling (default: false)")
                ),
                List.of("tableName")
        ));

        // testConnection
        functions.add(createFunctionDefinition(
                "testConnection",
                "Tests the database connection and returns connection details and status.",
                Map.of(),
                List.of()
        ));

        // runTransaction
        functions.add(createFunctionDefinition(
                "runTransaction",
                "Executes multiple SELECT queries within a single read-only transaction to ensure data consistency across all queries.",
                Map.of(
                        "queries", new PropertyDefinition("array", "Array of SQL SELECT queries to execute in transaction",
                                new PropertyDefinition("string", "SQL query"))
                ),
                List.of("queries")
        ));

        // getPerformanceMetrics
        functions.add(createFunctionDefinition(
                "getPerformanceMetrics",
                "Returns query performance statistics including average execution time, success rate, and slowest queries.",
                Map.of(),
                List.of()
        ));

        // healthCheck
        functions.add(createFunctionDefinition(
                "healthCheck",
                "Performs a comprehensive health check of the database server including uptime, connection status, and query performance.",
                Map.of(),
                List.of()
        ));

        // runMultiQuery
        functions.add(createFunctionDefinition(
                "runMultiQuery",
                "Executes multiple SELECT queries in sequence and returns an array of results. Unlike run-transaction, queries are not executed within a transaction.",
                Map.of(
                        "queries", new PropertyDefinition("array", "Array of SQL SELECT queries to execute",
                                new PropertyDefinition("string", "SQL query"))
                ),
                List.of("queries")
        ));

        // sampleTableData
        functions.add(createFunctionDefinition(
                "sampleTableData",
                "Quickly preview table data without writing SQL. Supports random sampling.",
                Map.of(
                        "tableName", new PropertyDefinition("string", "Name of the table to sample"),
                        "schema", new PropertyDefinition("string", "Optional schema name (uses default if not provided)"),
                        "limit", new PropertyDefinition("integer", "Number of rows to return (default: 10)"),
                        "random", new PropertyDefinition("boolean", "Whether to use random sampling (default: false)")
                ),
                List.of("tableName")
        ));

        // compareSchemas
        functions.add(createFunctionDefinition(
                "compareSchemas",
                "Compares the structure of two tables (same table in different schemas or different tables). Returns differences in columns, types, and constraints.",
                Map.of(
                        "table1", new PropertyDefinition("string", "First table name"),
                        "schema1", new PropertyDefinition("string", "Schema for first table"),
                        "table2", new PropertyDefinition("string", "Second table name"),
                        "schema2", new PropertyDefinition("string", "Schema for second table")
                ),
                List.of("table1", "table2")
        ));

        return functions;
    }

    /**
     * Execute a function call
     */
    public Object executeFunction(String functionName, Map<String, Object> arguments) throws Exception {
        // Ensure arguments is not null
        if (arguments == null) {
            arguments = new java.util.HashMap<>();
        }
        
        switch (functionName) {
            case "runQuery":
                return mcpToolService.runQuery(objectMapper.convertValue(arguments, com.indrard.dbmcp.model.request.QueryRequest.class));
            
            case "listTables":
                return mcpToolService.listTables(
                        (String) arguments.get("schema"),
                        (String) arguments.get("pattern")
                );
            
            case "getTableInfo":
                String tableName = (String) arguments.get("tableName");
                if (tableName == null || tableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for getTableInfo function");
                }
                return mcpToolService.getTableInfo(
                        tableName,
                        (String) arguments.get("schema")
                );
            
            case "listSchemas":
                return mcpToolService.listSchemas();
            
            case "listViews":
                return mcpToolService.listViews(
                        (String) arguments.get("schema"),
                        (String) arguments.get("pattern")
                );
            
            case "getViewDefinition":
                String viewName = (String) arguments.get("viewName");
                if (viewName == null || viewName.trim().isEmpty()) {
                    throw new IllegalArgumentException("viewName is required for getViewDefinition function");
                }
                return mcpToolService.getViewDefinition(
                        viewName,
                        (String) arguments.get("schema")
                );
            
            case "getRowCount":
                String rowCountTableName = (String) arguments.get("tableName");
                if (rowCountTableName == null || rowCountTableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for getRowCount function");
                }
                return mcpToolService.getRowCount(
                        rowCountTableName,
                        (String) arguments.get("schema"),
                        (String) arguments.get("whereClause")
                );
            
            case "getIndexes":
                String indexTableName = (String) arguments.get("tableName");
                if (indexTableName == null || indexTableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for getIndexes function");
                }
                return mcpToolService.getIndexes(
                        indexTableName,
                        (String) arguments.get("schema")
                );
            
            case "getForeignKeys":
                String fkTableName = (String) arguments.get("tableName");
                if (fkTableName == null || fkTableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for getForeignKeys function");
                }
                return mcpToolService.getForeignKeys(
                        fkTableName,
                        (String) arguments.get("schema")
                );
            
            case "listStoredProcedures":
                return mcpToolService.listStoredProcedures(
                        (String) arguments.get("schema"),
                        (String) arguments.get("pattern")
                );
            
            case "getTableStatistics":
                String statsTableName = (String) arguments.get("tableName");
                if (statsTableName == null || statsTableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for getTableStatistics function");
                }
                return mcpToolService.getTableStatistics(
                        statsTableName,
                        (String) arguments.get("schema")
                );
            
            case "explainQuery":
                String sql = (String) arguments.get("sql");
                if (sql == null || sql.trim().isEmpty()) {
                    throw new IllegalArgumentException("sql is required for explainQuery function");
                }
                return mcpToolService.explainQuery(sql);
            
            case "sampleTableData":
                String sampleTableName = (String) arguments.get("tableName");
                if (sampleTableName == null || sampleTableName.trim().isEmpty()) {
                    throw new IllegalArgumentException("tableName is required for sampleTableData function");
                }
                return mcpToolService.sampleTableData(
                        sampleTableName,
                        (String) arguments.get("schema"),
                        (Integer) arguments.get("limit"),
                        (Boolean) arguments.get("random")
                );
            
            case "testConnection":
                return mcpToolService.testConnection();
            
            case "runTransaction":
                @SuppressWarnings("unchecked")
                List<String> transactionQueries = (List<String>) arguments.get("queries");
                return mcpToolService.runTransaction(transactionQueries);
            
            case "getPerformanceMetrics":
                return mcpToolService.getPerformanceMetrics();
            
            case "healthCheck":
                return mcpToolService.healthCheck();
            
            case "runMultiQuery":
                @SuppressWarnings("unchecked")
                List<String> multiQueries = (List<String>) arguments.get("queries");
                return mcpToolService.runMultiQuery(multiQueries);
            
            case "compareSchemas":
                String table1 = (String) arguments.get("table1");
                String table2 = (String) arguments.get("table2");
                if (table1 == null || table1.trim().isEmpty()) {
                    throw new IllegalArgumentException("table1 is required for compareSchemas function");
                }
                if (table2 == null || table2.trim().isEmpty()) {
                    throw new IllegalArgumentException("table2 is required for compareSchemas function");
                }
                return mcpToolService.compareSchemas(
                        table1,
                        (String) arguments.get("schema1"),
                        table2,
                        (String) arguments.get("schema2")
                );
            
            default:
                throw new IllegalArgumentException("Unknown function: " + functionName);
        }
    }

    private FunctionDefinition createFunctionDefinition(String name, String description,
                                                        Map<String, PropertyDefinition> properties,
                                                        List<String> required) {
        FunctionParameters params = new FunctionParameters(properties, required);
        return new FunctionDefinition(name, description, params);
    }
}
