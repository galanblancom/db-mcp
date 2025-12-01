package com.javamcp.dbmcp.adapter.impl;

import com.javamcp.dbmcp.adapter.DatabaseAdapter;
import com.javamcp.dbmcp.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostgresAdapter implements DatabaseAdapter {

    private JdbcTemplate jdbcTemplate;
    private DataSource dataSource;

    public PostgresAdapter(String host, int port, String database, String user, String password) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);

        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(ds);
    }

    @Override
    public void connect() throws Exception {
        // JDBC connects on demand, but we can test connection here
        testConnection();
    }

    @Override
    public void disconnect() throws Exception {
        // No explicit disconnect needed for JdbcTemplate with simple DataSource
    }

    @Override
    public QueryResult executeQuery(String sql, int maxRows, boolean excludeLargeColumns) throws Exception {
        String limitedSql = sql + " LIMIT " + maxRows;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(limitedSql);

        List<String> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        }

        // Handle excludeLargeColumns logic if needed (simplified here)

        return new QueryResult(rows, rows.size(), columns);
    }

    @Override
    public TableInfo getTableInfo(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";
        String tableNameLower = tableName.toLowerCase();

        String columnQuery = """
            SELECT 
              c.column_name,
              c.data_type,
              c.character_maximum_length,
              c.numeric_precision,
              c.numeric_scale,
              c.is_nullable,
              c.column_default,
              CASE WHEN pk.column_name IS NOT NULL THEN true ELSE false END as is_primary_key,
              CASE WHEN fk.column_name IS NOT NULL THEN true ELSE false END as is_foreign_key
            FROM information_schema.columns c
            LEFT JOIN (
              SELECT kcu.column_name, kcu.table_name, kcu.table_schema
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu 
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
              WHERE tc.constraint_type = 'PRIMARY KEY'
            ) pk ON c.column_name = pk.column_name 
              AND c.table_name = pk.table_name 
              AND c.table_schema = pk.table_schema
            LEFT JOIN (
              SELECT kcu.column_name, kcu.table_name, kcu.table_schema
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu 
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
              WHERE tc.constraint_type = 'FOREIGN KEY'
            ) fk ON c.column_name = fk.column_name 
              AND c.table_name = fk.table_name 
              AND c.table_schema = fk.table_schema
            WHERE c.table_name = ? AND c.table_schema = ?
            ORDER BY c.ordinal_position
            """;

        List<ColumnInfo> columns = jdbcTemplate.query(columnQuery,
            (rs, rowNum) -> {
                ColumnInfo col = new ColumnInfo();
                col.setName(rs.getString("column_name"));
                col.setType(rs.getString("data_type"));
                col.setLength(rs.getObject("character_maximum_length") != null ? rs.getInt("character_maximum_length") : null);
                col.setPrecision(rs.getObject("numeric_precision") != null ? rs.getInt("numeric_precision") : null);
                col.setScale(rs.getObject("numeric_scale") != null ? rs.getInt("numeric_scale") : null);
                col.setNullable("YES".equals(rs.getString("is_nullable")));
                col.setDefaultValue(rs.getString("column_default"));
                col.setPrimaryKey(rs.getBoolean("is_primary_key"));
                col.setForeignKey(rs.getBoolean("is_foreign_key"));
                return col;
            },
            tableNameLower, schemaName
        );

        String countQuery = "SELECT COUNT(*) FROM " + schemaName + "." + tableNameLower;
        Integer countObj = jdbcTemplate.queryForObject(countQuery, Integer.class);
        int rowCount = (countObj != null) ? countObj : 0;

        return new TableInfo(tableNameLower, schemaName, schemaName, rowCount, columns);
    }

    @Override
    @SuppressWarnings("null")
    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";

        StringBuilder query = new StringBuilder(
            "SELECT table_name, " +
            "(SELECT reltuples::bigint FROM pg_class WHERE relname = table_name " +
            "AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)) as row_count " +
            "FROM information_schema.tables " +
            "WHERE table_schema = ? AND table_type = 'BASE TABLE'"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND table_name LIKE ?");
            params.add(pattern.toLowerCase());
        }

        query.append(" ORDER BY table_name");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr,
            (rs, rowNum) -> {
                TableListItem item = new TableListItem();
                item.setName(rs.getString("table_name"));
                item.setRowCount(rs.getObject("row_count") != null ? rs.getInt("row_count") : null);
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
              schema_name,
              (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = s.schema_name AND table_type = 'BASE TABLE') as table_count,
              (SELECT COUNT(*) FROM information_schema.views WHERE table_schema = s.schema_name) as view_count
            FROM information_schema.schemata s
            WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
            ORDER BY schema_name
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
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";

        StringBuilder query = new StringBuilder(
            "SELECT table_name as view_name, table_schema as schema_name " +
            "FROM information_schema.views " +
            "WHERE table_schema = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND table_name LIKE ?");
            params.add(pattern.toLowerCase());
        }

        query.append(" ORDER BY table_name");

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
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";
        String viewNameLower = viewName.toLowerCase();

        String query = """
            SELECT table_name as view_name, table_schema as schema_name, view_definition
            FROM information_schema.views
            WHERE table_name = ? AND table_schema = ?
            """;

        List<ViewDefinition> results = jdbcTemplate.query(query,
            (rs, rowNum) -> {
                ViewDefinition def = new ViewDefinition();
                def.setName(rs.getString("view_name"));
                def.setSchema(rs.getString("schema_name"));
                def.setDefinition(rs.getString("view_definition"));
                return def;
            },
            viewNameLower, schemaName
        );

        if (results.isEmpty()) {
            throw new Exception("View " + schemaName + "." + viewNameLower + " not found");
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
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";
        String tableNameLower = tableName.toLowerCase();

        String query = """
            SELECT
              i.relname as index_name,
              t.relname as table_name,
              ix.indisunique as is_unique,
              am.amname as index_type,
              array_agg(a.attname ORDER BY a.attnum) as columns
            FROM pg_class t
            JOIN pg_index ix ON t.oid = ix.indrelid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_am am ON i.relam = am.oid
            JOIN pg_namespace n ON t.relnamespace = n.oid
            JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
            WHERE t.relname = ? AND n.nspname = ?
            GROUP BY i.relname, t.relname, ix.indisunique, am.amname
            ORDER BY i.relname
            """;

        return jdbcTemplate.query(query,
            (rs, rowNum) -> {
                IndexInfo info = new IndexInfo();
                info.setIndexName(rs.getString("index_name"));
                info.setTableName(rs.getString("table_name"));
                info.setUnique(rs.getBoolean("is_unique"));
                info.setIndexType(rs.getString("index_type"));
                info.setSchema(schemaName);
                java.sql.Array columnsArray = rs.getArray("columns");
                String[] columnsArr = (String[]) columnsArray.getArray();
                info.setColumns(List.of(columnsArr));
                return info;
            },
            tableNameLower, schemaName
        );
    }

    @Override
    public List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";
        String tableNameLower = tableName.toLowerCase();

        String query = """
            SELECT
              tc.constraint_name,
              tc.table_name,
              kcu.column_name,
              ccu.table_name AS referenced_table,
              ccu.column_name AS referenced_column,
              rc.delete_rule,
              rc.update_rule
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
              AND tc.table_schema = kcu.table_schema
            JOIN information_schema.constraint_column_usage ccu
              ON ccu.constraint_name = tc.constraint_name
              AND ccu.table_schema = tc.table_schema
            JOIN information_schema.referential_constraints rc
              ON tc.constraint_name = rc.constraint_name
              AND tc.table_schema = rc.constraint_schema
            WHERE tc.constraint_type = 'FOREIGN KEY'
              AND tc.table_name = ?
              AND tc.table_schema = ?
            ORDER BY tc.constraint_name
            """;

        return jdbcTemplate.query(query,
            (rs, rowNum) -> {
                ForeignKeyInfo info = new ForeignKeyInfo();
                info.setConstraintName(rs.getString("constraint_name"));
                info.setTableName(rs.getString("table_name"));
                info.setColumnName(rs.getString("column_name"));
                info.setReferencedTable(rs.getString("referenced_table"));
                info.setReferencedColumn(rs.getString("referenced_column"));
                info.setOnDelete(rs.getString("delete_rule"));
                info.setOnUpdate(rs.getString("update_rule"));
                info.setSchema(schemaName);
                return info;
            },
            tableNameLower, schemaName
        );
    }

    @Override
    @SuppressWarnings("null")
    public List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";

        StringBuilder query = new StringBuilder(
            "SELECT " +
            "  p.proname as name, " +
            "  n.nspname as schema, " +
            "  CASE p.prokind " +
            "    WHEN 'f' THEN 'FUNCTION' " +
            "    WHEN 'p' THEN 'PROCEDURE' " +
            "    ELSE 'FUNCTION' " +
            "  END as type, " +
            "  pg_get_function_result(p.oid) as return_type " +
            "FROM pg_proc p " +
            "JOIN pg_namespace n ON p.pronamespace = n.oid " +
            "WHERE n.nspname = ?"
        );

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND p.proname LIKE ?");
            params.add(pattern.toLowerCase());
        }

        query.append(" ORDER BY p.proname");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr,
            (rs, rowNum) -> {
                StoredProcedureInfo info = new StoredProcedureInfo();
                info.setName(rs.getString("name"));
                info.setSchema(rs.getString("schema"));
                info.setType(rs.getString("type"));
                info.setReturnType(rs.getString("return_type"));
                return info;
            },
            params.toArray()
        );
    }

    @Override
    public TableStatistics getTableStatistics(String tableName, String schema) throws Exception {
        String schemaName = (schema != null && !schema.isEmpty()) ? schema : "public";
        String tableNameLower = tableName.toLowerCase();

        String query = """
            SELECT
              schemaname || '.' || tablename as full_name,
              n_live_tup as row_count,
              pg_total_relation_size(schemaname || '.' || tablename) / 1024.0 / 1024.0 as size_mb,
              pg_indexes_size(schemaname || '.' || tablename) / 1024.0 / 1024.0 as index_size_mb,
              last_analyze,
              last_autoanalyze
            FROM pg_stat_user_tables
            WHERE tablename = ? AND schemaname = ?
            """;

        List<TableStatistics> results = jdbcTemplate.query(query,
            (rs, rowNum) -> {
                TableStatistics stats = new TableStatistics();
                stats.setTableName(tableNameLower);
                stats.setSchema(schemaName);
                stats.setRowCount(rs.getObject("row_count") != null ? rs.getInt("row_count") : 0);
                stats.setSizeInMB(rs.getObject("size_mb") != null ? rs.getDouble("size_mb") : null);
                stats.setIndexSizeInMB(rs.getObject("index_size_mb") != null ? rs.getDouble("index_size_mb") : null);
                java.sql.Timestamp lastAnalyze = rs.getTimestamp("last_analyze");
                java.sql.Timestamp lastAutoAnalyze = rs.getTimestamp("last_autoanalyze");
                stats.setLastAnalyzed(lastAnalyze != null ? lastAnalyze : lastAutoAnalyze);
                return stats;
            },
            tableNameLower, schemaName
        );

        if (results.isEmpty()) {
            throw new Exception("Table " + schemaName + "." + tableNameLower + " not found");
        }

        return results.get(0);
    }

    @Override
    public ExplainPlan explainQuery(String sql) throws Exception {
        String explainSql = "EXPLAIN (FORMAT JSON, ANALYZE FALSE) " + sql;
        List<Map<String, Object>> result = jdbcTemplate.queryForList(explainSql);
        
        String plan = "";
        if (!result.isEmpty()) {
            Object queryPlan = result.get(0).get("QUERY PLAN");
            plan = queryPlan != null ? queryPlan.toString() : "";
        }

        ExplainPlan explainPlan = new ExplainPlan();
        explainPlan.setQuery(sql);
        explainPlan.setPlan(plan);
        return explainPlan;
    }

    @Override
    public QueryResult sampleTableData(String tableName, String schema, int limit, boolean random) throws Exception {
        String sql = "SELECT * FROM " + (schema != null ? schema + "." : "") + tableName;
        if (random) {
            sql += " ORDER BY RANDOM()";
        }
        sql += " LIMIT " + limit;
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
        TableInfo info1 = getTableInfo(table1, schema1);
        TableInfo info2 = getTableInfo(table2, schema2);
        
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
