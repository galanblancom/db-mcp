package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.*;
import com.indrard.dbmcp.model.request.QueryRequest;
import com.indrard.dbmcp.util.QueryLogger;
import com.indrard.dbmcp.util.QueryTemplates;
import com.indrard.dbmcp.util.UptimeTracker;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class McpToolService {

    private final DatabaseService databaseService;
    private final QueryLogger queryLogger;
    private final UptimeTracker uptimeTracker;
    private final QueryTemplates queryTemplates;
    private final ExportCacheService exportCacheService;
    
    // Store last executed query for export functionality
    private String lastExecutedQuery = null;
    private List<String> lastQueryNics = null;

    @Autowired
    public McpToolService(DatabaseService databaseService, QueryLogger queryLogger, 
                         UptimeTracker uptimeTracker, QueryTemplates queryTemplates,
                         ExportCacheService exportCacheService) {
        this.databaseService = databaseService;
        this.queryLogger = queryLogger;
        this.uptimeTracker = uptimeTracker;
        this.queryTemplates = queryTemplates;
        this.exportCacheService = exportCacheService;
    }

    @Tool(description = "Executes a SELECT or WITH query against the database and returns the results.")
    public QueryResult runQuery(QueryRequest request) throws Exception {
        int maxRows = request.getMaxRows() != null ? request.getMaxRows() : 1000;
        boolean excludeLarge = request.getExcludeLargeColumns() != null ? request.getExcludeLargeColumns() : false;
        return databaseService.executeQuery(request.getSql(), maxRows, excludeLarge);
    }

    @Tool(description = "Lists all SQL database tables accessible by the current user with row counts. ONLY use for SQL database tables, NOT for CSV files or ChromaDB file content.")
    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        return databaseService.listTables(schema, pattern);
    }

    @Tool(description = "Retrieves detailed information about a SQL database table including columns, data types, constraints, primary keys, and foreign keys. ONLY use for SQL database tables, NOT for CSV files or ChromaDB file content.")
    public TableInfo getTableInfo(String tableName, String schema) throws Exception {
        return databaseService.getTableInfo(tableName, schema);
    }

    @Tool(description = "Lists all schemas/databases accessible in the current database instance.")
    public List<SchemaInfo> listSchemas() throws Exception {
        return databaseService.listSchemas();
    }

    @Tool(description = "Lists all views accessible by the current user.")
    public List<ViewInfo> listViews(String schema, String pattern) throws Exception {
        return databaseService.listViews(schema, pattern);
    }

    @Tool(description = "Retrieves the SQL definition of a view.")
    public ViewDefinition getViewDefinition(String viewName, String schema) throws Exception {
        return databaseService.getViewDefinition(viewName, schema);
    }

    @Tool(description = "Gets the total number of rows in a table quickly without retrieving data.")
    public int getRowCount(String tableName, String schema, String whereClause) throws Exception {
        return databaseService.getRowCount(tableName, schema, whereClause);
    }

    @Tool(description = "Retrieves index information for a table including column names, uniqueness, and index type.")
    public List<IndexInfo> getIndexes(String tableName, String schema) throws Exception {
        return databaseService.getIndexes(tableName, schema);
    }

    @Tool(description = "Retrieves foreign key relationships for a table including referenced tables, columns, and cascade rules.")
    public List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception {
        return databaseService.getForeignKeys(tableName, schema);
    }

    @Tool(description = "Lists all stored procedures and functions accessible in the database.")
    public List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception {
        return databaseService.listStoredProcedures(schema, pattern);
    }

    @Tool(description = "Retrieves advanced table statistics including size in MB, index size, row count, and last analyzed date.")
    public TableStatistics getTableStatistics(String tableName, String schema) throws Exception {
        return databaseService.getTableStatistics(tableName, schema);
    }

    @Tool(description = "Analyzes a query's execution plan without actually executing it. Shows estimated costs and rows.")
    public ExplainPlan explainQuery(String sql) throws Exception {
        return databaseService.explainQuery(sql);
    }

    @Tool(description = "Quickly preview table data without writing SQL. Supports random sampling.")
    public QueryResult sampleTableData(String tableName, String schema, Integer limit, Boolean random) throws Exception {
        int sampleLimit = limit != null ? limit : 10;
        boolean useRandom = random != null ? random : false;
        return databaseService.sampleTableData(tableName, schema, sampleLimit, useRandom);
    }

    @Tool(description = "Tests the database connection and returns connection details and status.")
    public boolean testConnection() {
        return databaseService.testConnection();
    }

    @Tool(description = "Executes multiple SELECT queries within a single read-only transaction to ensure data consistency across all queries.")
    public List<QueryResult> runTransaction(List<String> queries) throws Exception {
        return databaseService.executeTransaction(queries);
    }

    @Tool(description = "Returns query performance statistics including average execution time, success rate, and slowest queries.")
    public QueryLogger.QueryStats getPerformanceMetrics() {
        return queryLogger.getStats();
    }

    @Tool(description = "Performs a comprehensive health check of the database server including uptime, connection status, and query performance.")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", databaseService.testConnection() ? "healthy" : "unhealthy");
        health.put("uptime", uptimeTracker.getUptimeFormatted());
        health.put("uptimeMs", uptimeTracker.getUptimeMillis());
        health.put("performanceMetrics", queryLogger.getStats());
        return health;
    }

    @Tool(description = "Executes multiple SELECT queries in sequence and returns an array of results. Unlike run-transaction, queries are not executed within a transaction.")
    public List<QueryResult> runMultiQuery(List<String> queries) throws Exception {
        return databaseService.executeMultiQuery(queries);
    }

    @Tool(description = "Lists all available query templates with their IDs, names, descriptions, and required parameters.")
    public List<QueryTemplates.QueryTemplate> listQueryTemplates() {
        return queryTemplates.list();
    }

    @Tool(description = "Executes a pre-defined SQL query template by ID with parameter substitution. ONLY use for SQL database queries, NOT for file content or ChromaDB files.")
    public QueryResult executeTemplate(String templateId, Map<String, String> parameters) throws Exception {
        String sql = queryTemplates.execute(templateId, parameters);
        return databaseService.executeQuery(sql, 1000, false);
    }

    @Tool(description = "Compares the structure of two tables (same table in different schemas or different tables). Returns differences in columns, types, and constraints.")
    public SchemaComparisonResult compareSchemas(String table1, String schema1, String table2, String schema2) throws Exception {
        return databaseService.compareSchemas(table1, schema1, table2, schema2);
    }

    // Custom database analysis tools
    @Tool(description = "Get a comprehensive summary of the SQL database including total tables and basic statistics. ONLY use for SQL database questions, NOT for file content or ChromaDB files.")
    public Map<String, Object> getDatabaseSummary() throws Exception {
        List<TableListItem> tables = databaseService.listTables(null, null);
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTables", tables.size());
        summary.put("timestamp", System.currentTimeMillis());
        summary.put("success", true);
        
        return summary;
    }

    @Tool(description = "Search for tables matching a specific name pattern. Use % as wildcard (e.g., 'USER_%' or '%_HISTORY')")
    public Map<String, Object> searchTablesByPattern(String pattern) throws Exception {
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "%";
        }
        
        List<TableListItem> tables = databaseService.listTables(null, pattern);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("pattern", pattern);
        result.put("matchingTables", tables.size());
        result.put("tables", tables);
        
        return result;
    }

    @Tool(description = "Get comprehensive statistics for a specific SQL database table including row count, column count, and column details. ONLY use for actual database tables, NOT for CSV files or file content from ChromaDB.")
    public Map<String, Object> getTableStatisticsSummary(String tableName, String schema) throws Exception {
        if (tableName == null || tableName.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Table name is required");
            return error;
        }
        
        TableInfo tableInfo = databaseService.getTableInfo(tableName, schema);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("success", true);
        stats.put("tableName", tableName);
        stats.put("schema", schema);
        stats.put("rowCount", tableInfo.getRowCount());
        stats.put("columnCount", tableInfo.getColumns() != null ? tableInfo.getColumns().size() : 0);
        stats.put("columns", tableInfo.getColumns());
        
        return stats;
    }

    @Tool(description = "Compare row counts between two tables to identify data discrepancies or synchronization issues")
    public Map<String, Object> compareTableRowCounts(String table1, String table2, String schema) throws Exception {
        if (table1 == null || table2 == null || table1.trim().isEmpty() || table2.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Both table names are required");
            return error;
        }
        
        TableInfo info1 = databaseService.getTableInfo(table1, schema);
        TableInfo info2 = databaseService.getTableInfo(table2, schema);
        
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("success", true);
        comparison.put("table1", Map.of("name", table1, "rowCount", info1.getRowCount()));
        comparison.put("table2", Map.of("name", table2, "rowCount", info2.getRowCount()));
        comparison.put("difference", Math.abs(info1.getRowCount() - info2.getRowCount()));
        comparison.put("schema", schema);
        
        return comparison;
    }

    @Tool(description = "Find tables with row count greater than or equal to the specified minimum rows. Default minimum is 1000 rows.")
    public Map<String, Object> findLargeTables(Long minRows) throws Exception {
        if (minRows == null) {
            minRows = 1000L;
        }
        
        List<TableListItem> allTables = databaseService.listTables(null, null);
        final Long threshold = minRows;
        List<TableListItem> largeTables = allTables.stream()
            .filter(t -> t.getRowCount() >= threshold)
            .collect(java.util.stream.Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("minRows", threshold);
        result.put("totalTables", allTables.size());
        result.put("largeTablesCount", largeTables.size());
        result.put("largeTables", largeTables);
        
        return result;
    }

    @Tool(description = "Get pending invoices (facturas por pagar) for one or more contract NICs. Use this when user asks about 'facturas por pagar', 'pending invoices', 'deudas', or 'invoices to pay'. Returns invoices with due date (F_VCTO_FAC), invoice number (SIMBOLO_VAR), and debt amount (DEUDA). Parameters: nics (required, list of contract numbers), maxRows (optional, default 1000). Example: nics=['12345', '67890']")
    public QueryResult getInvoicesToPayByContract(List<String> nics, Integer maxRows) throws Exception {
        if (nics == null || nics.isEmpty()) {
            throw new IllegalArgumentException("NICs parameter is required. Please provide at least one contract NIC number.");
        }
        
        int limit = maxRows != null ? maxRows : 1000;
        
        String query = "SELECT NIC, F_VCTO_FAC, SIMBOLO_VAR, " +
            "RECIBOS.IMP_TOT_REC - RECIBOS.IMP_CTA - ( " +
            "    SELECT NVL(SUM(CTI.IMP_COB_BCO),0) FROM COBTEMP CTI " +
            "    WHERE CTI.IND_PROC = '2' AND CTI.TIP_COBRO IN ('CB001', 'CB002') " +
            "    AND CTI.SIMBOLO_VAR = RECIBOS.SIMBOLO_VAR " +
            ") AS DEUDA " +
            "FROM RECIBOS WHERE RECIBOS.NIC IN (" + nics.stream().map(nic -> "'" + nic + "'").collect(Collectors.joining(", ")) + ") " +
            "AND RECIBOS.IMP_TOT_REC > RECIBOS.IMP_CTA " +
            "AND INSTR((SELECT RTRIM(XMLAGG(XMLELEMENT(E,EST_REC,',').EXTRACT('//text()') ORDER BY EST_REC).GETCLOBVAL(),',') " +
            "FROM GRUPO_EST WHERE CO_GRUPO = 'GE116' OR CO_GRUPO = 'GE118'),RECIBOS.EST_ACT) <> 0";
        
        // Store for potential export
        this.lastExecutedQuery = query;
        this.lastQueryNics = nics;
        
        return databaseService.executeQuery(query, limit, false);
    }

    // ==================== FILE EXPORT TOOLS ====================

    @Tool(description = "Export the last query results to CSV/Excel format. Use this after running any query (getInvoicesToPayByContract, runQuery, etc.) when user asks to 'export to Excel', 'download as CSV', or 'save the results'. Automatically uses the last executed query.")
    public Map<String, Object> exportLastQueryToCsv(String title, Integer maxRows) throws Exception {
        if (lastExecutedQuery == null) {
            throw new IllegalArgumentException("No query has been executed yet. Please run a query first before exporting.");
        }
        
        // Execute the last query again to get fresh data
        int rows = maxRows != null ? maxRows : 10000;
        QueryResult result = databaseService.executeQuery(lastExecutedQuery, rows, false);
        
        String fileName = title != null ? title : "export";
        
        // Cache the result and get a download ID
        String exportId = exportCacheService.cacheExport(lastExecutedQuery, fileName, result);
        String downloadUrl = "http://localhost:8081/api/export/download-csv/" + exportId;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("action", "export_to_csv");
        response.put("fileName", fileName + ".csv");
        response.put("rowsExported", result.getRows().size());
        response.put("columns", result.getColumns());
        response.put("downloadUrl", downloadUrl);
        response.put("exportId", exportId);
        response.put("message", String.format("✅ Export ready! '%s.csv' with %d rows. Download: %s",
            fileName, result.getRows().size(), downloadUrl));
        
        return response;
    }

    @Tool(description = "Export table data directly to CSV/Excel. Specify the table name and optionally schema and maxRows (default: 10000). Returns download information. Use when user asks to 'export the [table] table', 'download users table', etc.")
    public Map<String, Object> exportTableToCsv(String tableName, String schema, Integer maxRows) throws Exception {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName is required");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("action", "export_table");
        response.put("tableName", tableName);
        response.put("schema", schema);
        response.put("maxRows", maxRows != null ? maxRows : 10000);
        
        // Build the export URL for the frontend to call
        String exportUrl = "/api/export/table-to-csv?table=" + tableName;
        if (schema != null) {
            exportUrl += "&schema=" + schema;
        }
        exportUrl += "&maxRows=" + (maxRows != null ? maxRows : 10000);
        
        response.put("exportUrl", exportUrl);
        response.put("message", "Table '" + tableName + "' can be exported via: GET " + exportUrl);
        response.put("instructions", "Call this endpoint to download the CSV file with " + (maxRows != null ? maxRows : 10000) + " rows maximum");
        
        return response;
    }

    @Tool(description = "Export custom SQL query results to CSV/Excel. Provide the SQL SELECT query, optional title, and maxRows (default: 10000). Use when user asks to 'export the results of [query]' or 'save this query to Excel'.")
    public Map<String, Object> exportQueryToCsv(String sql, String title, Integer maxRows) throws Exception {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("sql query is required");
        }

        if (!sql.trim().toUpperCase().startsWith("SELECT") && !sql.trim().toUpperCase().startsWith("WITH")) {
            throw new IllegalArgumentException("Only SELECT and WITH queries are allowed for export");
        }

        // Execute the query to get the actual data
        int rows = maxRows != null ? maxRows : 10000;
        QueryResult result = databaseService.executeQuery(sql, rows, false);
        
        String fileName = title != null ? title : "export";
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("action", "export_to_csv");
        response.put("fileName", fileName + ".csv");
        response.put("rowsExported", result.getRows().size());
        response.put("columns", result.getColumns());
        response.put("downloadEndpoint", "/api/export/query-to-csv");
        response.put("message", String.format("Successfully prepared CSV export '%s.csv' with %d rows and %d columns. " +
            "To download, make a POST request to /api/export/query-to-csv with the SQL query in the body.",
            fileName, result.getRows().size(), result.getColumns().size()));
        
        return response;
    }

    @Tool(description = "Export query results to PDF report. Provide SQL SELECT query, optional title, and maxRows (default: 1000, PDFs work best with smaller datasets). Use when user asks for a 'PDF report', 'print-friendly version', or 'formatted report'.")
    public Map<String, Object> exportQueryToPdf(String sql, String title, Integer maxRows) throws Exception {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("sql query is required");
        }

        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed for export");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("action", "export_to_pdf");
        response.put("sql", sql);
        response.put("title", title != null ? title : "Query Results Report");
        response.put("maxRows", maxRows != null ? maxRows : 1000);
        response.put("endpoint", "POST /api/export/query-to-pdf");
        response.put("message", "Query results can be exported to PDF by calling POST /api/export/query-to-pdf");
        response.put("instructions", "Send POST request with JSON body: { \"sql\": \"" + sql + "\", \"title\": \"" + (title != null ? title : "Query Results") + "\", \"maxRows\": " + (maxRows != null ? maxRows : 1000) + " }");
        response.put("note", "PDF exports work best with up to 1000 rows for readability");
        
        return response;
    }
}
