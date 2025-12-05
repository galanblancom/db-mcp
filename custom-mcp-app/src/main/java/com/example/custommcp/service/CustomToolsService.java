package com.example.custommcp.service;

import com.indrard.dbmcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom MCP Tools Service
 * Provides additional tools beyond the base db-mcp library
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomToolsService {

    private final McpToolService mcpToolService;

    /**
     * Custom Tool: Get Database Summary
     * Returns a comprehensive summary of the database including table count, total rows, etc.
     */
    public Map<String, Object> getDatabaseSummary() {
        log.info("Getting database summary");
        
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Get all tables
            var tables = mcpToolService.listTables(null, null);
            summary.put("totalTables", tables.size());
            
            // Calculate total rows across all tables
            long totalRows = tables.stream()
                    .mapToLong(table -> table.getRowCount() != null ? table.getRowCount() : 0)
                    .sum();
            summary.put("totalRows", totalRows);
            
            // Get timestamp
            summary.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            summary.put("success", true);
            
            log.info("Database summary: {} tables, {} rows", tables.size(), totalRows);
            
        } catch (Exception e) {
            log.error("Error getting database summary", e);
            summary.put("success", false);
            summary.put("error", e.getMessage());
        }
        
        return summary;
    }

    /**
     * Custom Tool: Find Large Tables
     * Returns tables with row count above a threshold
     */
    public Map<String, Object> findLargeTables(Long minRows) {
        log.info("Finding large tables with min rows: {}", minRows);
        
        Map<String, Object> result = new HashMap<>();
        long threshold = minRows != null ? minRows : 1000;
        
        try {
            var allTables = mcpToolService.listTables(null, null);
            
            var largeTables = allTables.stream()
                    .filter(table -> table.getRowCount() != null && table.getRowCount() >= threshold)
                    .sorted((t1, t2) -> Long.compare(
                            t2.getRowCount() != null ? t2.getRowCount() : 0,
                            t1.getRowCount() != null ? t1.getRowCount() : 0
                    ))
                    .toList();
            
            result.put("success", true);
            result.put("threshold", threshold);
            result.put("count", largeTables.size());
            result.put("tables", largeTables);
            
            log.info("Found {} large tables", largeTables.size());
            
        } catch (Exception e) {
            log.error("Error finding large tables", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Custom Tool: Search Tables by Name Pattern
     * Returns tables matching a name pattern
     */
    public Map<String, Object> searchTablesByPattern(String pattern) {
        log.info("Searching tables with pattern: {}", pattern);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            var tables = mcpToolService.listTables(null, pattern);
            
            result.put("success", true);
            result.put("pattern", pattern);
            result.put("count", tables.size());
            result.put("tables", tables);
            
            log.info("Found {} tables matching pattern '{}'", tables.size(), pattern);
            
        } catch (Exception e) {
            log.error("Error searching tables", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Custom Tool: Get Table Statistics Summary
     * Returns aggregated statistics for a specific table
     */
    public Map<String, Object> getTableStatisticsSummary(String tableName, String schema) {
        log.info("Getting statistics summary for table: {}", tableName);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            var tableInfo = mcpToolService.getTableInfo(tableName, schema);
            
            result.put("success", true);
            result.put("tableName", tableName);
            result.put("schema", schema);
            result.put("columnCount", tableInfo.getColumns().size());
            result.put("rowCount", tableInfo.getRowCount());
            result.put("columns", tableInfo.getColumns());
            
            // Add primary key info
            var pkColumns = tableInfo.getColumns().stream()
                    .filter(col -> col.isPrimaryKey())
                    .map(col -> col.getName())
                    .toList();
            result.put("primaryKeyColumns", pkColumns);
            
            log.info("Table {} has {} columns, {} rows", tableName, tableInfo.getColumns().size(), tableInfo.getRowCount());
            
        } catch (Exception e) {
            log.error("Error getting table statistics", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Custom Tool: Compare Table Row Counts
     * Compares row counts between two tables
     */
    public Map<String, Object> compareTableRowCounts(String table1, String table2, String schema) {
        log.info("Comparing row counts between {} and {}", table1, table2);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            var info1 = mcpToolService.getTableInfo(table1, schema);
            var info2 = mcpToolService.getTableInfo(table2, schema);
            
            long rows1 = info1.getRowCount();
            long rows2 = info2.getRowCount();
            
            result.put("success", true);
            result.put("table1", Map.of("name", table1, "rows", rows1));
            result.put("table2", Map.of("name", table2, "rows", rows2));
            result.put("difference", Math.abs(rows1 - rows2));
            result.put("larger", rows1 > rows2 ? table1 : table2);
            
            log.info("{}: {} rows, {}: {} rows", table1, rows1, table2, rows2);
            
        } catch (Exception e) {
            log.error("Error comparing tables", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

}
