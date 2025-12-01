package com.javamcp.dbmcp.adapter.impl;

import com.javamcp.dbmcp.adapter.DatabaseAdapter;
import com.javamcp.dbmcp.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MssqlAdapter implements DatabaseAdapter {

    private JdbcTemplate jdbcTemplate;
    private DataSource dataSource;

    public MssqlAdapter(String host, int port, String database, String user, String password, boolean encrypt,
            boolean trustServerCertificate) {
        // SQL Server JDBC URL format
        String url = String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=%s;trustServerCertificate=%s",
                host, port, database, encrypt, trustServerCertificate);

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);

        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(ds);
    }

    @Override
    public void connect() throws Exception {
        testConnection();
    }

    @Override
    public void disconnect() throws Exception {
    }

    @Override
    public QueryResult executeQuery(String sql, int maxRows, boolean excludeLargeColumns) throws Exception {
        // MSSQL uses TOP
        // Simple regex replacement or subquery approach
        String limitedSql = sql;
        if (!sql.toUpperCase().contains("TOP ")) {
            limitedSql = sql.replaceFirst("(?i)SELECT", "SELECT TOP " + maxRows);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(limitedSql);

        List<String> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        }

        return new QueryResult(rows, rows.size(), columns);
    }

    @Override
    public TableInfo getTableInfo(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        String columnQuery = """
            SELECT 
              c.COLUMN_NAME,
              c.DATA_TYPE,
              c.CHARACTER_MAXIMUM_LENGTH,
              c.NUMERIC_PRECISION,
              c.NUMERIC_SCALE,
              c.IS_NULLABLE,
              c.COLUMN_DEFAULT,
              CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END as IS_PRIMARY_KEY,
              CASE WHEN fk.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END as IS_FOREIGN_KEY
            FROM INFORMATION_SCHEMA.COLUMNS c
            LEFT JOIN (
              SELECT kcu.COLUMN_NAME, kcu.TABLE_NAME, kcu.TABLE_SCHEMA
              FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
              JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu 
                ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
              WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'
            ) pk ON c.COLUMN_NAME = pk.COLUMN_NAME 
              AND c.TABLE_NAME = pk.TABLE_NAME 
              AND c.TABLE_SCHEMA = pk.TABLE_SCHEMA
            LEFT JOIN (
              SELECT kcu.COLUMN_NAME, kcu.TABLE_NAME, kcu.TABLE_SCHEMA
              FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
              JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu 
                ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
              WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
            ) fk ON c.COLUMN_NAME = fk.COLUMN_NAME 
              AND c.TABLE_NAME = fk.TABLE_NAME 
              AND c.TABLE_SCHEMA = fk.TABLE_SCHEMA
            WHERE c.TABLE_NAME = ? AND c.TABLE_SCHEMA = ?
            ORDER BY c.ORDINAL_POSITION
            """;

        List<ColumnInfo> columns = jdbcTemplate.query(columnQuery,
            (rs, rowNum) -> {
                ColumnInfo col = new ColumnInfo();
                col.setName(rs.getString("COLUMN_NAME"));
                col.setType(rs.getString("DATA_TYPE"));
                col.setLength(rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null ? rs.getInt("CHARACTER_MAXIMUM_LENGTH") : null);
                col.setPrecision(rs.getObject("NUMERIC_PRECISION") != null ? rs.getInt("NUMERIC_PRECISION") : null);
                col.setScale(rs.getObject("NUMERIC_SCALE") != null ? rs.getInt("NUMERIC_SCALE") : null);
                col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                col.setPrimaryKey(rs.getInt("IS_PRIMARY_KEY") == 1);
                col.setForeignKey(rs.getInt("IS_FOREIGN_KEY") == 1);
                return col;
            },
            tableName, schemaName
        );

        String countQuery = "SELECT COUNT(*) FROM " + schemaName + "." + tableName;
        Integer countObj = jdbcTemplate.queryForObject(countQuery, Integer.class);
        int rowCount = (countObj != null) ? countObj : 0;

        return new TableInfo(tableName, schemaName, schemaName, rowCount, columns);
    }

    @Override
    @SuppressWarnings("null")
    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        StringBuilder query = new StringBuilder(
            "SELECT " +
            "  t.TABLE_NAME, " +
            "  p.rows as ROW_COUNT " +
            "FROM INFORMATION_SCHEMA.TABLES t " +
            "LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name " +
            "LEFT JOIN sys.partitions p ON st.object_id = p.object_id AND p.index_id IN (0, 1) " +
            "WHERE t.TABLE_SCHEMA = ? AND t.TABLE_TYPE = 'BASE TABLE'"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND t.TABLE_NAME LIKE ?");
            params.add(pattern);
        }

        query.append(" ORDER BY t.TABLE_NAME");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr,
            (rs, rowNum) -> {
                TableListItem item = new TableListItem();
                item.setName(rs.getString("TABLE_NAME"));
                item.setRowCount(rs.getObject("ROW_COUNT") != null ? rs.getInt("ROW_COUNT") : null);
                item.setLastAnalyzed(null);
                return item;
            },
            params.toArray()
        );
    }

    @Override
    public int getRowCount(String tableName, String schema, String whereClause) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + (schema != null ? schema + "." : "") + tableName;
        if (whereClause != null && !whereClause.isEmpty()) {
            sql += " WHERE " + whereClause;
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return (count != null) ? count : 0;
    }

    @Override
    public List<SchemaInfo> listSchemas() throws Exception {
        String query = """
            SELECT 
              s.name as schema_name,
              (SELECT COUNT(*) FROM sys.tables t WHERE t.schema_id = s.schema_id) as table_count,
              (SELECT COUNT(*) FROM sys.views v WHERE v.schema_id = s.schema_id) as view_count
            FROM sys.schemas s
            WHERE s.name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', 'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', 'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter')
            ORDER BY s.name
            """;

        return jdbcTemplate.query(query,
            (rs, rowNum) -> {
                SchemaInfo info = new SchemaInfo();
                info.setName(rs.getString("schema_name"));
                info.setTableCount(rs.getInt("table_count"));
                info.setViewCount(rs.getInt("view_count"));
                return info;
            }
        );
    }

    @Override
    @SuppressWarnings("null")
    public List<ViewInfo> listViews(String schema, String pattern) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        StringBuilder query = new StringBuilder(
            "SELECT TABLE_NAME as view_name, TABLE_SCHEMA as schema_name " +
            "FROM INFORMATION_SCHEMA.VIEWS " +
            "WHERE TABLE_SCHEMA = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND TABLE_NAME LIKE ?");
            params.add(pattern);
        }

        query.append(" ORDER BY TABLE_NAME");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr,
            (rs, rowNum) -> {
                ViewInfo info = new ViewInfo();
                info.setName(rs.getString("view_name"));
                info.setSchema(rs.getString("schema_name"));
                return info;
            },
            params.toArray()
        );
    }

    @Override
    public ViewDefinition getViewDefinition(String viewName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        String query = """
            SELECT 
              v.TABLE_NAME as view_name,
              v.TABLE_SCHEMA as schema_name,
              m.definition as view_definition
            FROM INFORMATION_SCHEMA.VIEWS v
            INNER JOIN sys.views sv ON v.TABLE_NAME = sv.name
            INNER JOIN sys.schemas s ON sv.schema_id = s.schema_id AND s.name = v.TABLE_SCHEMA
            INNER JOIN sys.sql_modules m ON sv.object_id = m.object_id
            WHERE v.TABLE_NAME = ? AND v.TABLE_SCHEMA = ?
            """;

        List<ViewDefinition> results = jdbcTemplate.query(query,
            (rs, rowNum) -> {
                ViewDefinition def = new ViewDefinition();
                def.setName(rs.getString("view_name"));
                def.setSchema(rs.getString("schema_name"));
                def.setDefinition(rs.getString("view_definition"));
                return def;
            },
            viewName, schemaName
        );

        if (results.isEmpty()) {
            throw new Exception("View " + schemaName + "." + viewName + " not found");
        }

        return results.get(0);
    }

    @Override
    public boolean testConnection() {
        try {
            jdbcTemplate.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<IndexInfo> getIndexes(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        String query = """
            SELECT 
              i.name as index_name,
              t.name as table_name,
              i.is_unique,
              i.type_desc as index_type,
              STRING_AGG(c.name, ', ') WITHIN GROUP (ORDER BY ic.key_ordinal) as columns
            FROM sys.indexes i
            INNER JOIN sys.tables t ON i.object_id = t.object_id
            INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
            INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
            INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
            WHERE t.name = ? AND s.name = ?
            GROUP BY i.name, t.name, i.is_unique, i.type_desc
            ORDER BY i.name
            """;

        return jdbcTemplate.query(query,
            (rs, rowNum) -> {
                IndexInfo info = new IndexInfo();
                info.setIndexName(rs.getString("index_name"));
                info.setTableName(rs.getString("table_name"));
                info.setUnique(rs.getBoolean("is_unique"));
                info.setIndexType(rs.getString("index_type"));
                info.setSchema(schemaName);
                String columnsStr = rs.getString("columns");
                info.setColumns(columnsStr != null ? List.of(columnsStr.split(", ")) : new ArrayList<>());
                return info;
            },
            tableName, schemaName
        );
    }

    @Override
    public List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        String query = """
            SELECT 
              fk.name as constraint_name,
              tp.name as table_name,
              cp.name as column_name,
              tr.name as referenced_table,
              cr.name as referenced_column,
              fk.delete_referential_action_desc as on_delete,
              fk.update_referential_action_desc as on_update
            FROM sys.foreign_keys fk
            INNER JOIN sys.tables tp ON fk.parent_object_id = tp.object_id
            INNER JOIN sys.schemas sp ON tp.schema_id = sp.schema_id
            INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
            INNER JOIN sys.columns cp ON fkc.parent_object_id = cp.object_id AND fkc.parent_column_id = cp.column_id
            INNER JOIN sys.tables tr ON fk.referenced_object_id = tr.object_id
            INNER JOIN sys.columns cr ON fkc.referenced_object_id = cr.object_id AND fkc.referenced_column_id = cr.column_id
            WHERE tp.name = ? AND sp.name = ?
            ORDER BY fk.name
            """;

        return jdbcTemplate.query(query,
            (rs, rowNum) -> {
                ForeignKeyInfo info = new ForeignKeyInfo();
                info.setConstraintName(rs.getString("constraint_name"));
                info.setTableName(rs.getString("table_name"));
                info.setColumnName(rs.getString("column_name"));
                info.setReferencedTable(rs.getString("referenced_table"));
                info.setReferencedColumn(rs.getString("referenced_column"));
                info.setOnDelete(rs.getString("on_delete"));
                info.setOnUpdate(rs.getString("on_update"));
                info.setSchema(schemaName);
                return info;
            },
            tableName, schemaName
        );
    }

    @Override
    @SuppressWarnings("null")
    public List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        StringBuilder query = new StringBuilder(
            "SELECT " +
            "  p.name, " +
            "  s.name as schema_name, " +
            "  CASE p.type " +
            "    WHEN 'P' THEN 'PROCEDURE' " +
            "    WHEN 'FN' THEN 'FUNCTION' " +
            "    WHEN 'IF' THEN 'FUNCTION' " +
            "    WHEN 'TF' THEN 'FUNCTION' " +
            "    ELSE 'PROCEDURE' " +
            "  END as type " +
            "FROM sys.objects p " +
            "INNER JOIN sys.schemas s ON p.schema_id = s.schema_id " +
            "WHERE p.type IN ('P', 'FN', 'IF', 'TF') AND s.name = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND p.name LIKE ?");
            params.add(pattern);
        }

        query.append(" ORDER BY p.name");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr,
            (rs, rowNum) -> {
                StoredProcedureInfo info = new StoredProcedureInfo();
                info.setName(rs.getString("name"));
                info.setSchema(rs.getString("schema_name"));
                info.setType(rs.getString("type"));
                return info;
            },
            params.toArray()
        );
    }

    @Override
    public TableStatistics getTableStatistics(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "dbo";

        String query = """
            SELECT 
              t.name as table_name,
              SUM(p.rows) as row_count,
              SUM(a.total_pages) * 8 / 1024.0 as size_mb,
              SUM(a.used_pages) * 8 / 1024.0 - SUM(CASE WHEN p.index_id IN (0,1) THEN a.used_pages ELSE 0 END) * 8 / 1024.0 as index_size_mb,
              STATS_DATE(t.object_id, i.index_id) as last_analyzed
            FROM sys.tables t
            INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
            INNER JOIN sys.partitions p ON t.object_id = p.object_id
            INNER JOIN sys.allocation_units a ON p.partition_id = a.container_id
            LEFT JOIN sys.indexes i ON t.object_id = i.object_id AND i.index_id IN (0,1)
            WHERE t.name = ? AND s.name = ?
            GROUP BY t.name, t.object_id, i.index_id
            """;

        List<TableStatistics> results = jdbcTemplate.query(query,
            (rs, rowNum) -> {
                TableStatistics stats = new TableStatistics();
                stats.setTableName(rs.getString("table_name"));
                stats.setSchema(schemaName);
                stats.setRowCount(rs.getObject("row_count") != null ? rs.getInt("row_count") : 0);
                stats.setSizeInMB(rs.getObject("size_mb") != null ? rs.getDouble("size_mb") : null);
                stats.setIndexSizeInMB(rs.getObject("index_size_mb") != null ? rs.getDouble("index_size_mb") : null);
                stats.setLastAnalyzed(rs.getTimestamp("last_analyzed"));
                return stats;
            },
            tableName, schemaName
        );

        if (results.isEmpty()) {
            throw new Exception("Table " + schemaName + "." + tableName + " not found");
        }

        return results.get(0);
    }

    @Override
    @SuppressWarnings("null")
    public ExplainPlan explainQuery(String sql) throws Exception {
        jdbcTemplate.execute("SET SHOWPLAN_TEXT ON");
        
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            StringBuilder planBuilder = new StringBuilder();
            
            for (Map<String, Object> row : result) {
                Object planText = row.get("SHOWPLAN TEXT");
                if (planText == null) {
                    planText = row.get("StmtText");
                }
                if (planText != null) {
                    planBuilder.append(planText.toString()).append("\n");
                }
            }
            
            ExplainPlan explainPlan = new ExplainPlan();
            explainPlan.setQuery(sql);
            explainPlan.setPlan(planBuilder.toString());
            return explainPlan;
        } finally {
            jdbcTemplate.execute("SET SHOWPLAN_TEXT OFF");
        }
    }

    @Override
    public QueryResult sampleTableData(String tableName, String schema, int limit, boolean random) throws Exception {
        String sql = "SELECT TOP " + limit + " * FROM " + (schema != null ? schema + "." : "") + tableName;
        if (random) {
            sql += " ORDER BY NEWID()";
        }
        return executeQuery(sql, limit, false);
    }

    @Override
    public List<QueryResult> executeTransaction(List<String> queries) throws Exception {
        List<QueryResult> results = new ArrayList<>();
        
        jdbcTemplate.execute((java.sql.Connection connection) -> {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                connection.setReadOnly(true);
                
                for (String query : queries) {
                    results.add(executeQuery(query, 1000, false));
                }
                
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException("Transaction failed: " + e.getMessage(), e);
            } finally {
                connection.setReadOnly(false);
                connection.setAutoCommit(originalAutoCommit);
            }
            return null;
        });
        
        return results;
    }

    @Override
    public List<QueryResult> executeMultiQuery(List<String> queries) throws Exception {
        List<QueryResult> results = new ArrayList<>();
        for (String query : queries) {
            results.add(executeQuery(query, 1000, false));
        }
        return results;
    }

    @Override
    public SchemaComparisonResult compareSchemas(String table1, String schema1, String table2, String schema2) throws Exception {
        SchemaComparisonResult result = new SchemaComparisonResult();
        result.setTable1(table1);
        result.setSchema1(schema1);
        result.setTable2(table2);
        result.setSchema2(schema2);
        result.setColumnDifferences(new ArrayList<>());
        result.setColumnsOnlyInTable1(new ArrayList<>());
        result.setColumnsOnlyInTable2(new ArrayList<>());
        result.setSummary("Schema comparison not fully implemented");
        return result;
    }
}
