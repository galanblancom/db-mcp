package com.indrard.dbmcp.config;

import com.indrard.dbmcp.model.*;
import com.indrard.dbmcp.model.request.QueryRequest;
import com.indrard.dbmcp.service.McpToolService;
import com.indrard.dbmcp.service.SemanticFolderService;
import com.indrard.dbmcp.service.function.ToolDefinition;
import com.indrard.dbmcp.service.function.ToolParameter;
import com.indrard.dbmcp.util.QueryLogger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Centralized configuration file for ALL database tools.
 * All tools are defined in this single file with annotations.
 * 
 * To add a new tool:
 * 1. Add a method with @ToolDefinition annotation
 * 2. Add @ToolParameter annotations to method parameters
 * 3. The tool will be automatically discovered and registered
 * 
 * No need to modify multiple files or add case statements!
 */
@Component
public class ToolsConfiguration {

    private final McpToolService mcpToolService;
    private final SemanticFolderService semanticFolderService;

    public ToolsConfiguration(McpToolService mcpToolService, SemanticFolderService semanticFolderService) {
        this.mcpToolService = mcpToolService;
        this.semanticFolderService = semanticFolderService;
    }

    @ToolDefinition(
        name = "runQuery",
        description = "Executes a SELECT or WITH query against the database and returns the results. Present results in a clear table format or list, highlighting key information.",
        priority = 100
    )
    public QueryResult runQuery(
        @ToolParameter(name = "sql", description = "The SQL SELECT query to execute", required = true, type = "string") String sql,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to return (default: 1000)", type = "integer") Integer maxRows,
        @ToolParameter(name = "excludeLargeColumns", description = "Whether to exclude large text/blob columns (default: false)", type = "boolean") Boolean excludeLargeColumns
    ) throws Exception {
        QueryRequest request = new QueryRequest();
        request.setSql(sql);
        request.setMaxRows(maxRows != null ? maxRows : 1000);
        request.setExcludeLargeColumns(excludeLargeColumns != null ? excludeLargeColumns : false);
        return mcpToolService.runQuery(request);
    }

    @ToolDefinition(
        name = "listTables",
        description = "Lists all tables accessible by the current user with row counts. Present table names with their row counts in a simple, organized list format.",
        priority = 100
    )
    public List<TableListItem> listTables(
        @ToolParameter(name = "schema", description = "Optional schema name to filter tables", type = "string") String schema,
        @ToolParameter(name = "pattern", description = "Optional pattern to filter table names (SQL LIKE pattern)", type = "string") String pattern
    ) throws Exception {
        return mcpToolService.listTables(schema, pattern);
    }

    @ToolDefinition(
        name = "getTableInfo",
        description = "Retrieves detailed information about a table including columns, data types, constraints, primary keys, and foreign keys. Present the structure clearly showing column names, types, and constraints. Highlight primary keys and important constraints.",
        priority = 100
    )
    public TableInfo getTableInfo(
        @ToolParameter(name = "tableName", description = "Name of the table to inspect", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getTableInfo(tableName, schema);
    }

    @ToolDefinition(
        name = "listSchemas",
        description = "Lists all schemas/databases accessible in the current database instance.",
        priority = 100
    )
    public List<SchemaInfo> listSchemas() throws Exception {
        return mcpToolService.listSchemas();
    }

    @ToolDefinition(
        name = "listViews",
        description = "Lists all views accessible by the current user.",
        priority = 100
    )
    public List<ViewInfo> listViews(
        @ToolParameter(name = "schema", description = "Optional schema name to filter views", type = "string") String schema,
        @ToolParameter(name = "pattern", description = "Optional pattern to filter view names (SQL LIKE pattern)", type = "string") String pattern
    ) throws Exception {
        return mcpToolService.listViews(schema, pattern);
    }

    @ToolDefinition(
        name = "getViewDefinition",
        description = "Retrieves the SQL definition of a view.",
        priority = 100
    )
    public ViewDefinition getViewDefinition(
        @ToolParameter(name = "viewName", description = "Name of the view to inspect", required = true, type = "string") String viewName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getViewDefinition(viewName, schema);
    }

    @ToolDefinition(
        name = "getRowCount",
        description = "Gets the total number of rows in a table quickly without retrieving data.",
        priority = 100
    )
    public int getRowCount(
        @ToolParameter(name = "tableName", description = "Name of the table to count rows", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema,
        @ToolParameter(name = "whereClause", description = "Optional WHERE clause to filter rows (without 'WHERE' keyword)", type = "string") String whereClause
    ) throws Exception {
        return mcpToolService.getRowCount(tableName, schema, whereClause);
    }

    @ToolDefinition(
        name = "getIndexes",
        description = "Retrieves index information for a table including column names, uniqueness, and index type.",
        priority = 100
    )
    public List<IndexInfo> getIndexes(
        @ToolParameter(name = "tableName", description = "Name of the table to get indexes for", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getIndexes(tableName, schema);
    }

    @ToolDefinition(
        name = "getForeignKeys",
        description = "Retrieves foreign key relationships for a table including referenced tables, columns, and cascade rules.",
        priority = 100
    )
    public List<ForeignKeyInfo> getForeignKeys(
        @ToolParameter(name = "tableName", description = "Name of the table to get foreign keys for", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getForeignKeys(tableName, schema);
    }

    @ToolDefinition(
        name = "listStoredProcedures",
        description = "Lists all stored procedures and functions accessible in the database.",
        priority = 100
    )
    public List<StoredProcedureInfo> listStoredProcedures(
        @ToolParameter(name = "schema", description = "Optional schema name to filter procedures", type = "string") String schema,
        @ToolParameter(name = "pattern", description = "Optional pattern to filter procedure names (SQL LIKE pattern)", type = "string") String pattern
    ) throws Exception {
        return mcpToolService.listStoredProcedures(schema, pattern);
    }

    @ToolDefinition(
        name = "getTableStatistics",
        description = "Retrieves advanced table statistics including size in MB, index size, row count, and last analyzed date.",
        priority = 100
    )
    public TableStatistics getTableStatistics(
        @ToolParameter(name = "tableName", description = "Name of the table to get statistics for", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getTableStatistics(tableName, schema);
    }

    @ToolDefinition(
        name = "explainQuery",
        description = "Analyzes a query's execution plan without actually executing it. Shows estimated costs and rows.",
        priority = 100
    )
    public ExplainPlan explainQuery(
        @ToolParameter(name = "sql", description = "The SQL query to analyze", required = true, type = "string") String sql
    ) throws Exception {
        return mcpToolService.explainQuery(sql);
    }

    @ToolDefinition(
        name = "sampleTableData",
        description = "Quickly preview table data without writing SQL. Supports random sampling.",
        priority = 100
    )
    public QueryResult sampleTableData(
        @ToolParameter(name = "tableName", description = "Name of the table to sample", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema,
        @ToolParameter(name = "limit", description = "Number of rows to return (default: 10)", type = "integer") Integer limit,
        @ToolParameter(name = "random", description = "Whether to use random sampling (default: false)", type = "boolean") Boolean random
    ) throws Exception {
        return mcpToolService.sampleTableData(tableName, schema, limit, random);
    }

    @ToolDefinition(
        name = "testConnection",
        description = "Tests the database connection and returns connection details and status.",
        priority = 100
    )
    public boolean testConnection() {
        return mcpToolService.testConnection();
    }

    @ToolDefinition(
        name = "runTransaction",
        description = "Executes multiple SELECT queries within a single read-only transaction to ensure data consistency across all queries.",
        priority = 100
    )
    public List<QueryResult> runTransaction(
        @ToolParameter(name = "queries", description = "Array of SQL SELECT queries to execute in transaction", required = true, type = "array") List<String> queries
    ) throws Exception {
        return mcpToolService.runTransaction(queries);
    }

    @ToolDefinition(
        name = "getPerformanceMetrics",
        description = "Returns query performance statistics including average execution time, success rate, and slowest queries.",
        priority = 100
    )
    public QueryLogger.QueryStats getPerformanceMetrics() {
        return mcpToolService.getPerformanceMetrics();
    }

    @ToolDefinition(
        name = "healthCheck",
        description = "Performs a comprehensive health check of the database server including uptime, connection status, and query performance.",
        priority = 100
    )
    public Map<String, Object> healthCheck() {
        return mcpToolService.healthCheck();
    }

    @ToolDefinition(
        name = "runMultiQuery",
        description = "Executes multiple SELECT queries in sequence and returns an array of results. Unlike run-transaction, queries are not executed within a transaction.",
        priority = 100
    )
    public List<QueryResult> runMultiQuery(
        @ToolParameter(name = "queries", description = "Array of SQL SELECT queries to execute", required = true, type = "array") List<String> queries
    ) throws Exception {
        return mcpToolService.runMultiQuery(queries);
    }

    @ToolDefinition(
        name = "compareSchemas",
        description = "Compares the structure of two tables (same table in different schemas or different tables). Returns differences in columns, types, and constraints.",
        priority = 100
    )
    public SchemaComparisonResult compareSchemas(
        @ToolParameter(name = "table1", description = "First table name", required = true, type = "string") String table1,
        @ToolParameter(name = "schema1", description = "Schema for first table", type = "string") String schema1,
        @ToolParameter(name = "table2", description = "Second table name", required = true, type = "string") String table2,
        @ToolParameter(name = "schema2", description = "Schema for second table", type = "string") String schema2
    ) throws Exception {
        return mcpToolService.compareSchemas(table1, schema1, table2, schema2);
    }

    @ToolDefinition(
        name = "listQueryTemplates",
        description = "Lists all available query templates with their IDs, names, descriptions, and required parameters.",
        priority = 100
    )
    public List<?> listQueryTemplates() {
        return mcpToolService.listQueryTemplates();
    }

    @ToolDefinition(
        name = "executeTemplate",
        description = "Executes a pre-defined query template by ID with parameter substitution.",
        priority = 100
    )
    public QueryResult executeTemplate(
        @ToolParameter(name = "templateId", description = "The ID of the template to execute", required = true, type = "string") String templateId,
        @ToolParameter(name = "parameters", description = "Key-value pairs for template parameter substitution", type = "object") Map<String, String> parameters
    ) throws Exception {
        return mcpToolService.executeTemplate(templateId, parameters);
    }

    @ToolDefinition(
        name = "getDatabaseSummary",
        description = "Get a comprehensive summary of the database including total tables and basic statistics.",
        priority = 100
    )
    public Map<String, Object> getDatabaseSummary() throws Exception {
        return mcpToolService.getDatabaseSummary();
    }

    @ToolDefinition(
        name = "searchTablesByPattern",
        description = "Search for tables matching a specific name pattern. Use % as wildcard (e.g., 'USER_%' or '%_HISTORY').",
        priority = 100
    )
    public Map<String, Object> searchTablesByPattern(
        @ToolParameter(name = "pattern", description = "Pattern to search for (use % as wildcard)", type = "string") String pattern
    ) throws Exception {
        return mcpToolService.searchTablesByPattern(pattern);
    }

    @ToolDefinition(
        name = "getTableStatisticsSummary",
        description = "Get comprehensive statistics for a specific table including row count, column count, and column details.",
        priority = 100
    )
    public Map<String, Object> getTableStatisticsSummary(
        @ToolParameter(name = "tableName", description = "Name of the table to get statistics for", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.getTableStatisticsSummary(tableName, schema);
    }

    @ToolDefinition(
        name = "compareTableRowCounts",
        description = "Compare row counts between two tables to identify data discrepancies or synchronization issues.",
        priority = 100
    )
    public Map<String, Object> compareTableRowCounts(
        @ToolParameter(name = "table1", description = "First table name", required = true, type = "string") String table1,
        @ToolParameter(name = "table2", description = "Second table name", required = true, type = "string") String table2,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema
    ) throws Exception {
        return mcpToolService.compareTableRowCounts(table1, table2, schema);
    }

    @ToolDefinition(
        name = "findLargeTables",
        description = "Find tables with row count greater than or equal to the specified minimum rows. Default minimum is 1000 rows.",
        priority = 100
    )
    public Map<String, Object> findLargeTables(
        @ToolParameter(name = "minRows", description = "Minimum number of rows (default: 1000)", type = "integer") Long minRows
    ) throws Exception {
        return mcpToolService.findLargeTables(minRows);
    }

    @ToolDefinition(
        name = "getInvoicesToPayByContract",
        description = "Get invoices to pay by contract. Returns pending invoices with due date, invoice number, and debt amount for one or more contract NICs. Present each invoice with its due date, invoice number (SIMBOLO_VAR), and debt amount in a clear, easy-to-read format. Highlight overdue invoices if applicable.",
        priority = 100
    )
    public Map<String, Object> getInvoicesToPayByContract(
        @ToolParameter(name = "nics", description = "Array of contract NIC identifiers to query", required = true, type = "array") List<String> nics,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to return (default: 1000)", type = "integer") Integer maxRows
    ) throws Exception {
        QueryResult result = mcpToolService.getInvoicesToPayByContract(nics, maxRows);

        // Calculate summary statistics
        double totalDebt = result.getRows().stream()
            .mapToDouble(row -> {
                Object deudaObj = row.get("DEUDA");
                if (deudaObj instanceof Number) {
                    return ((Number) deudaObj).doubleValue();
                }
                return 0;
            }).sum();

        // Build the SQL query used (for potential export)
        String sqlQuery = "SELECT NIC, F_VCTO_FAC, SIMBOLO_VAR, " +
            "RECIBOS.IMP_TOT_REC - RECIBOS.IMP_CTA - ( " +
            "    SELECT NVL(SUM(CTI.IMP_COB_BCO),0) FROM COBTEMP CTI " +
            "    WHERE CTI.IND_PROC = '2' AND CTI.TIP_COBRO IN ('CB001', 'CB002') " +
            "    AND CTI.SIMBOLO_VAR = RECIBOS.SIMBOLO_VAR " +
            ") AS DEUDA " +
            "FROM RECIBOS WHERE RECIBOS.NIC IN (" + 
            nics.stream().map(nic -> "'" + nic + "'").collect(java.util.stream.Collectors.joining(", ")) + 
            ") AND RECIBOS.IMP_TOT_REC > RECIBOS.IMP_CTA " +
            "AND INSTR((SELECT RTRIM(XMLAGG(XMLELEMENT(E,EST_REC,',').EXTRACT('//text()') ORDER BY EST_REC).GETCLOBVAL(),',') " +
            "FROM GRUPO_EST WHERE CO_GRUPO = 'GE116' OR CO_GRUPO = 'GE118'),RECIBOS.EST_ACT) <> 0";

        // Prepare structured response
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("totalInvoices", result.getRows().size());
        response.put("totalDebt", totalDebt);
        response.put("invoices", result.getRows());
        response.put("sqlQuery", sqlQuery);  // Include SQL for potential export
        response.put("summary", String.format("Found %d pending invoices for contract(s) %s with total debt of $%.2f", 
            result.getRows().size(), 
            String.join(", ", nics), 
            totalDebt));

        return response;
    }

    @ToolDefinition(
        name = "indexFolderForSemanticSearch",
        description = "Index a folder's contents into a vector database for semantic search. This allows you to find files based on their meaning, not just keywords. Use this before searching files semantically.",
        priority = 90
    )
    public String indexFolderForSemanticSearch(
        @ToolParameter(name = "folderPath", description = "Path to folder to index for semantic search", required = true, type = "string") String folderPath
    ) {
        return semanticFolderService.indexFolder(folderPath);
    }

    @ToolDefinition(
        name = "searchFilesBySemantic",
        description = "Search indexed files using semantic similarity. Find files by describing what you're looking for (e.g., 'database connection', 'error handling', 'REST API'). Must index folder first.",
        priority = 90
    )
    public String searchFilesBySemantic(
        @ToolParameter(name = "query", description = "What you're looking for described in natural language", required = true, type = "string") String query,
        @ToolParameter(name = "limit", description = "Maximum number of results to return (default: 5)", type = "integer") Integer limit
    ) {
        int resultLimit = limit != null ? limit : 5;
        return semanticFolderService.searchFiles(query, resultLimit);
    }

    @ToolDefinition(
        name = "getVectorStoreStats",
        description = "Get statistics about the semantic search index (how many files are indexed, collection status, etc.)",
        priority = 90
    )
    public String getVectorStoreStats() {
        return semanticFolderService.getIndexStats();
    }

    @ToolDefinition(
        name = "clearVectorStore",
        description = "Clear all indexed files from the semantic search vector store. Use this to start fresh or free up space.",
        priority = 90
    )
    public String clearVectorStore() {
        return semanticFolderService.clearIndex();
    }

    // ==================== EXPORT TOOLS ====================

    @ToolDefinition(
        name = "exportLastQueryToCsv",
        description = "[PRIMARY EXPORT TOOL] Export the last query results to CSV/Excel. Use this whenever user asks to 'export', 'download', or 'save' data. Automatically uses the last query - NO SQL needed. Only requires a title parameter. Example: exportLastQueryToCsv(title='facturas'). This is the DEFAULT choice for all exports.",
        priority = 90
    )
    public Map<String, Object> exportLastQueryToCsv(
        @ToolParameter(name = "title", description = "Title for the exported file (default: 'query_export')", type = "string") String title,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to export (default: 10000)", type = "integer") Integer maxRows
    ) throws Exception {
        return mcpToolService.exportLastQueryToCsv(title, maxRows);
    }

    @ToolDefinition(
        name = "exportTableToCsv",
        description = "Export entire table data to CSV/Excel format. Use when user asks to 'export the [table] table', 'download users table', etc. Returns download information.",
        priority = 80
    )
    public Map<String, Object> exportTableToCsv(
        @ToolParameter(name = "tableName", description = "Name of the table to export", required = true, type = "string") String tableName,
        @ToolParameter(name = "schema", description = "Optional schema name (uses default if not provided)", type = "string") String schema,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to export (default: 10000)", type = "integer") Integer maxRows
    ) throws Exception {
        return mcpToolService.exportTableToCsv(tableName, schema, maxRows);
    }

    @ToolDefinition(
        name = "exportQueryToCsv",
        description = "[ADVANCED] Export custom SQL query to CSV/Excel. Requires providing the exact SQL query as a parameter. ONLY use this if you need to export a NEW query that hasn't been executed yet. For exporting data the user just viewed, use exportLastQueryToCsv instead.",
        priority = 70
    )
    public Map<String, Object> exportQueryToCsv(
        @ToolParameter(name = "sql", description = "SQL SELECT query to execute and export (must be the EXACT query that retrieved the data)", required = true, type = "string") String sql,
        @ToolParameter(name = "title", description = "Title for the exported file (default: 'query_export')", type = "string") String title,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to export (default: 10000)", type = "integer") Integer maxRows
    ) throws Exception {
        return mcpToolService.exportQueryToCsv(sql, title, maxRows);
    }

    @ToolDefinition(
        name = "exportQueryToPdf",
        description = "Export SQL query results to PDF report. Best for smaller datasets (up to 1000 rows). Use when user asks for a 'PDF report', 'print-friendly version', or 'formatted report'.",
        priority = 80
    )
    public Map<String, Object> exportQueryToPdf(
        @ToolParameter(name = "sql", description = "SQL SELECT query to execute and export to PDF", required = true, type = "string") String sql,
        @ToolParameter(name = "title", description = "Title for the PDF report (default: 'Query Results Report')", type = "string") String title,
        @ToolParameter(name = "maxRows", description = "Maximum number of rows to include (default: 1000, PDFs work best with smaller datasets)", type = "integer") Integer maxRows
    ) throws Exception {
        return mcpToolService.exportQueryToPdf(sql, title, maxRows);
    }
}
