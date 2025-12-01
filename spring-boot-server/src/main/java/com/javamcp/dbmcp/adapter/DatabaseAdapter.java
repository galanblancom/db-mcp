package com.javamcp.dbmcp.adapter;

import com.javamcp.dbmcp.model.*;

import java.util.List;

public interface DatabaseAdapter {
    void connect() throws Exception;
    void disconnect() throws Exception;
    QueryResult executeQuery(String sql, int maxRows, boolean excludeLargeColumns) throws Exception;
    TableInfo getTableInfo(String tableName, String schema) throws Exception;
    List<TableListItem> listTables(String schema, String pattern) throws Exception;
    int getRowCount(String tableName, String schema, String whereClause) throws Exception;
    List<SchemaInfo> listSchemas() throws Exception;
    List<ViewInfo> listViews(String schema, String pattern) throws Exception;
    ViewDefinition getViewDefinition(String viewName, String schema) throws Exception;
    boolean testConnection();
    
    // Advanced operations
    List<IndexInfo> getIndexes(String tableName, String schema) throws Exception;
    List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception;
    List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception;
    TableStatistics getTableStatistics(String tableName, String schema) throws Exception;
    ExplainPlan explainQuery(String sql) throws Exception;
    QueryResult sampleTableData(String tableName, String schema, int limit, boolean random) throws Exception;
    
    // Transaction support
    List<QueryResult> executeTransaction(List<String> queries) throws Exception;
    List<QueryResult> executeMultiQuery(List<String> queries) throws Exception;
    
    // Schema comparison
    SchemaComparisonResult compareSchemas(String table1, String schema1, String table2, String schema2) throws Exception;
}
