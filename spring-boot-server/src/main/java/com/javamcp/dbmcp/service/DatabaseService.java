package com.javamcp.dbmcp.service;

import com.javamcp.dbmcp.adapter.DatabaseAdapter;
import com.javamcp.dbmcp.model.*;
import com.javamcp.dbmcp.util.QueryLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseService {

    private final DatabaseAdapter databaseAdapter;
    private final QueryLogger queryLogger;

    @Autowired
    public DatabaseService(DatabaseAdapter databaseAdapter, QueryLogger queryLogger) {
        this.databaseAdapter = databaseAdapter;
        this.queryLogger = queryLogger;
    }

    public QueryResult executeQuery(String sql, int maxRows, boolean excludeLargeColumns) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            QueryResult result = databaseAdapter.executeQuery(sql, maxRows, excludeLargeColumns);
            long executionTime = System.currentTimeMillis() - startTime;
            queryLogger.log(sql, executionTime, true, null);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            queryLogger.log(sql, executionTime, false, e.getMessage());
            throw e;
        }
    }

    public TableInfo getTableInfo(String tableName, String schema) throws Exception {
        return databaseAdapter.getTableInfo(tableName, schema);
    }

    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        return databaseAdapter.listTables(schema, pattern);
    }

    public int getRowCount(String tableName, String schema, String whereClause) throws Exception {
        return databaseAdapter.getRowCount(tableName, schema, whereClause);
    }

    public List<SchemaInfo> listSchemas() throws Exception {
        return databaseAdapter.listSchemas();
    }

    public List<ViewInfo> listViews(String schema, String pattern) throws Exception {
        return databaseAdapter.listViews(schema, pattern);
    }

    public ViewDefinition getViewDefinition(String viewName, String schema) throws Exception {
        return databaseAdapter.getViewDefinition(viewName, schema);
    }

    public boolean testConnection() {
        return databaseAdapter.testConnection();
    }

    public List<IndexInfo> getIndexes(String tableName, String schema) throws Exception {
        return databaseAdapter.getIndexes(tableName, schema);
    }

    public List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception {
        return databaseAdapter.getForeignKeys(tableName, schema);
    }

    public List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception {
        return databaseAdapter.listStoredProcedures(schema, pattern);
    }

    public TableStatistics getTableStatistics(String tableName, String schema) throws Exception {
        return databaseAdapter.getTableStatistics(tableName, schema);
    }

    public ExplainPlan explainQuery(String sql) throws Exception {
        return databaseAdapter.explainQuery(sql);
    }

    public QueryResult sampleTableData(String tableName, String schema, int limit, boolean random) throws Exception {
        return databaseAdapter.sampleTableData(tableName, schema, limit, random);
    }

    public List<QueryResult> executeTransaction(List<String> queries) throws Exception {
        return databaseAdapter.executeTransaction(queries);
    }

    public List<QueryResult> executeMultiQuery(List<String> queries) throws Exception {
        return databaseAdapter.executeMultiQuery(queries);
    }

    public SchemaComparisonResult compareSchemas(String table1, String schema1, String table2, String schema2) throws Exception {
        return databaseAdapter.compareSchemas(table1, schema1, table2, schema2);
    }
}
