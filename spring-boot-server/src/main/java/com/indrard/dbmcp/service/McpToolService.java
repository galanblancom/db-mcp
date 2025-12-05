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

    @Autowired
    public McpToolService(DatabaseService databaseService, QueryLogger queryLogger, 
                         UptimeTracker uptimeTracker, QueryTemplates queryTemplates) {
        this.databaseService = databaseService;
        this.queryLogger = queryLogger;
        this.uptimeTracker = uptimeTracker;
        this.queryTemplates = queryTemplates;
    }

    @Tool(description = "Executes a SELECT or WITH query against the database and returns the results.")
    public QueryResult runQuery(QueryRequest request) throws Exception {
        int maxRows = request.getMaxRows() != null ? request.getMaxRows() : 1000;
        boolean excludeLarge = request.getExcludeLargeColumns() != null ? request.getExcludeLargeColumns() : false;
        return databaseService.executeQuery(request.getSql(), maxRows, excludeLarge);
    }

    @Tool(description = "Lists all tables accessible by the current user with row counts.")
    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        return databaseService.listTables(schema, pattern);
    }

    @Tool(description = "Retrieves detailed information about a table including columns, data types, constraints, primary keys, and foreign keys.")
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

    @Tool(description = "Executes a pre-defined query template by ID with parameter substitution.")
    public QueryResult executeTemplate(String templateId, Map<String, String> parameters) throws Exception {
        String sql = queryTemplates.execute(templateId, parameters);
        return databaseService.executeQuery(sql, 1000, false);
    }

    @Tool(description = "Compares the structure of two tables (same table in different schemas or different tables). Returns differences in columns, types, and constraints.")
    public SchemaComparisonResult compareSchemas(String table1, String schema1, String table2, String schema2) throws Exception {
        return databaseService.compareSchemas(table1, schema1, table2, schema2);
    }

    // Custom database analysis tools
    @Tool(description = "Get a comprehensive summary of the database including total tables and basic statistics")
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

    @Tool(description = "Get comprehensive statistics for a specific table including row count, column count, and column details")
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

    @Tool(description = "Get invoices to pay by contract NIC. Returns pending invoices with due date, invoice number, and debt amount for a specific contract. Optional maxRows parameter (default: 1000).")
    public QueryResult getInvoicesToPayByContract(List<String> nics, Integer maxRows) throws Exception {
        if (nics == null || nics.isEmpty()) {
            throw new IllegalArgumentException("NICs parameter is required");
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
        
        return databaseService.executeQuery(query, limit, false);
    }
}
