package com.javamcp.dbmcp.adapter.impl;

import com.javamcp.dbmcp.adapter.DatabaseAdapter;
import com.javamcp.dbmcp.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OracleAdapter implements DatabaseAdapter {

    private JdbcTemplate jdbcTemplate;
    private DataSource dataSource;

    public OracleAdapter(String host, int port, String serviceName, String user, String password) {
        // Oracle JDBC URL format: jdbc:oracle:thin:@//host:port/serviceName
        String url = String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, serviceName);

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
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
        // Oracle 12c+ supports FETCH NEXT, older versions need ROWNUM
        // Assuming modern Oracle for simplicity, or wrapping in subquery
        String limitedSql = "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + maxRows;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(limitedSql);

        List<String> columns = new ArrayList<>();
        if (!rows.isEmpty()) {
            columns.addAll(rows.get(0).keySet());
        }

        return new QueryResult(rows, rows.size(), columns);
    }

    @Override
    public TableInfo getTableInfo(String tableName, String schema) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );
        String tableNameUpper = tableName.toUpperCase();

        String columnQuery = """
            SELECT 
              c.COLUMN_NAME,
              c.DATA_TYPE,
              c.DATA_LENGTH,
              c.DATA_PRECISION,
              c.DATA_SCALE,
              c.NULLABLE,
              c.DATA_DEFAULT,
              CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'Y' ELSE 'N' END as IS_PRIMARY_KEY,
              CASE WHEN fk.COLUMN_NAME IS NOT NULL THEN 'Y' ELSE 'N' END as IS_FOREIGN_KEY
            FROM ALL_TAB_COLUMNS c
            LEFT JOIN (
              SELECT acc.COLUMN_NAME, acc.TABLE_NAME, acc.OWNER
              FROM ALL_CONSTRAINTS ac
              JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
              WHERE ac.CONSTRAINT_TYPE = 'P'
            ) pk ON c.COLUMN_NAME = pk.COLUMN_NAME AND c.TABLE_NAME = pk.TABLE_NAME AND c.OWNER = pk.OWNER
            LEFT JOIN (
              SELECT acc.COLUMN_NAME, acc.TABLE_NAME, acc.OWNER
              FROM ALL_CONSTRAINTS ac
              JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
              WHERE ac.CONSTRAINT_TYPE = 'R'
            ) fk ON c.COLUMN_NAME = fk.COLUMN_NAME AND c.TABLE_NAME = fk.TABLE_NAME AND c.OWNER = fk.OWNER
            WHERE c.TABLE_NAME = ? AND c.OWNER = ?
            ORDER BY c.COLUMN_ID
            """;

        List<ColumnInfo> columns = jdbcTemplate.query(columnQuery, 
            (rs, rowNum) -> {
                ColumnInfo col = new ColumnInfo();
                col.setName(rs.getString("COLUMN_NAME"));
                col.setType(rs.getString("DATA_TYPE"));
                col.setLength(rs.getInt("DATA_LENGTH"));
                col.setPrecision(rs.getObject("DATA_PRECISION") != null ? rs.getInt("DATA_PRECISION") : null);
                col.setScale(rs.getObject("DATA_SCALE") != null ? rs.getInt("DATA_SCALE") : null);
                col.setNullable("Y".equals(rs.getString("NULLABLE")));
                col.setDefaultValue(rs.getString("DATA_DEFAULT"));
                col.setPrimaryKey("Y".equals(rs.getString("IS_PRIMARY_KEY")));
                col.setForeignKey("Y".equals(rs.getString("IS_FOREIGN_KEY")));
                return col;
            },
            tableNameUpper, owner
        );

        String countQuery = "SELECT COUNT(*) FROM " + owner + "." + tableNameUpper;
        Integer rowCountObj = jdbcTemplate.queryForObject(countQuery, Integer.class);
        int rowCount = (rowCountObj != null) ? rowCountObj : 0;

        return new TableInfo(tableNameUpper, owner, owner, rowCount, columns);
    }

    @Override
    @SuppressWarnings("null")
    public List<TableListItem> listTables(String schema, String pattern) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );

        StringBuilder query = new StringBuilder(
            "SELECT TABLE_NAME, NUM_ROWS, LAST_ANALYZED FROM ALL_TABLES WHERE OWNER = ?"
        );
        
        List<Object> params = new ArrayList<>();
        params.add(owner);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND TABLE_NAME LIKE ?");
            params.add(pattern.toUpperCase());
        }

        query.append(" ORDER BY TABLE_NAME");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr, 
            (rs, rowNum) -> {
                TableListItem item = new TableListItem();
                item.setName(rs.getString("TABLE_NAME"));
                item.setRowCount(rs.getObject("NUM_ROWS") != null ? rs.getInt("NUM_ROWS") : null);
                item.setLastAnalyzed(rs.getTimestamp("LAST_ANALYZED"));
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
              username as SCHEMA_NAME,
              (SELECT COUNT(*) FROM all_tables WHERE owner = u.username) as TABLE_COUNT,
              (SELECT COUNT(*) FROM all_views WHERE owner = u.username) as VIEW_COUNT
            FROM all_users u
            ORDER BY username
            """;

        return jdbcTemplate.query(query, 
            (rs, rowNum) -> {
                SchemaInfo info = new SchemaInfo();
                info.setName(rs.getString("SCHEMA_NAME"));
                info.setOwner(rs.getString("SCHEMA_NAME"));
                info.setTableCount(rs.getInt("TABLE_COUNT"));
                info.setViewCount(rs.getInt("VIEW_COUNT"));
                return info;
            }
        );
    }

    @Override
    @SuppressWarnings("null")
    public List<ViewInfo> listViews(String schema, String pattern) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );

        StringBuilder query = new StringBuilder(
            "SELECT VIEW_NAME, OWNER FROM ALL_VIEWS WHERE OWNER = ?"
        );
        
        List<Object> params = new ArrayList<>();
        params.add(owner);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND VIEW_NAME LIKE ?");
            params.add(pattern.toUpperCase());
        }

        query.append(" ORDER BY VIEW_NAME");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr, 
            (rs, rowNum) -> {
                ViewInfo info = new ViewInfo();
                info.setName(rs.getString("VIEW_NAME"));
                info.setOwner(rs.getString("OWNER"));
                info.setSchema(rs.getString("OWNER"));
                return info;
            },
            params.toArray()
        );
    }

    @Override
    public ViewDefinition getViewDefinition(String viewName, String schema) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );
        String viewNameUpper = viewName.toUpperCase();

        String query = "SELECT VIEW_NAME, OWNER, TEXT FROM ALL_VIEWS WHERE VIEW_NAME = ? AND OWNER = ?";

        List<ViewDefinition> results = jdbcTemplate.query(query, 
            (rs, rowNum) -> {
                ViewDefinition def = new ViewDefinition();
                def.setName(rs.getString("VIEW_NAME"));
                def.setOwner(rs.getString("OWNER"));
                def.setSchema(rs.getString("OWNER"));
                def.setDefinition(rs.getString("TEXT"));
                return def;
            },
            viewNameUpper, owner
        );

        if (results.isEmpty()) {
            throw new Exception("View " + owner + "." + viewNameUpper + " not found");
        }

        return results.get(0);
    }

    @Override
    public boolean testConnection() {
        try {
            jdbcTemplate.execute("SELECT 1 FROM DUAL");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<IndexInfo> getIndexes(String tableName, String schema) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );
        String tableNameUpper = tableName.toUpperCase();

        String query = """
            SELECT 
              i.INDEX_NAME,
              i.TABLE_NAME,
              i.UNIQUENESS,
              i.INDEX_TYPE,
              LISTAGG(ic.COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY ic.COLUMN_POSITION) as COLUMNS
            FROM ALL_INDEXES i
            JOIN ALL_IND_COLUMNS ic ON i.INDEX_NAME = ic.INDEX_NAME AND i.TABLE_OWNER = ic.TABLE_OWNER
            WHERE i.TABLE_NAME = ? AND i.TABLE_OWNER = ?
            GROUP BY i.INDEX_NAME, i.TABLE_NAME, i.UNIQUENESS, i.INDEX_TYPE
            ORDER BY i.INDEX_NAME
            """;

        return jdbcTemplate.query(query, 
            (rs, rowNum) -> {
                IndexInfo info = new IndexInfo();
                info.setIndexName(rs.getString("INDEX_NAME"));
                info.setTableName(rs.getString("TABLE_NAME"));
                info.setUnique("UNIQUE".equals(rs.getString("UNIQUENESS")));
                info.setIndexType(rs.getString("INDEX_TYPE"));
                info.setSchema(owner);
                String columnsStr = rs.getString("COLUMNS");
                info.setColumns(columnsStr != null ? List.of(columnsStr.split(", ")) : new ArrayList<>());
                return info;
            },
            tableNameUpper, owner
        );
    }

    @Override
    public List<ForeignKeyInfo> getForeignKeys(String tableName, String schema) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );
        String tableNameUpper = tableName.toUpperCase();

        String query = """
            SELECT 
              ac.CONSTRAINT_NAME,
              ac.TABLE_NAME,
              acc.COLUMN_NAME,
              r_acc.TABLE_NAME as REFERENCED_TABLE,
              r_acc.COLUMN_NAME as REFERENCED_COLUMN,
              ac.DELETE_RULE
            FROM ALL_CONSTRAINTS ac
            JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
            JOIN ALL_CONSTRAINTS r_ac ON ac.R_CONSTRAINT_NAME = r_ac.CONSTRAINT_NAME AND ac.R_OWNER = r_ac.OWNER
            JOIN ALL_CONS_COLUMNS r_acc ON r_ac.CONSTRAINT_NAME = r_acc.CONSTRAINT_NAME AND r_ac.OWNER = r_acc.OWNER
            WHERE ac.CONSTRAINT_TYPE = 'R' AND ac.TABLE_NAME = ? AND ac.OWNER = ?
            ORDER BY ac.CONSTRAINT_NAME
            """;

        return jdbcTemplate.query(query, 
            (rs, rowNum) -> {
                ForeignKeyInfo info = new ForeignKeyInfo();
                info.setConstraintName(rs.getString("CONSTRAINT_NAME"));
                info.setTableName(rs.getString("TABLE_NAME"));
                info.setColumnName(rs.getString("COLUMN_NAME"));
                info.setReferencedTable(rs.getString("REFERENCED_TABLE"));
                info.setReferencedColumn(rs.getString("REFERENCED_COLUMN"));
                info.setOnDelete(rs.getString("DELETE_RULE"));
                info.setSchema(owner);
                return info;
            },
            tableNameUpper, owner
        );
    }

    @Override
    @SuppressWarnings("null")
    public List<StoredProcedureInfo> listStoredProcedures(String schema, String pattern) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );

        StringBuilder query = new StringBuilder(
            "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS WHERE OBJECT_TYPE IN ('PROCEDURE', 'FUNCTION') AND OWNER = ?"
        );
        
        List<Object> params = new ArrayList<>();
        params.add(owner);

        if (pattern != null && !pattern.isEmpty()) {
            query.append(" AND OBJECT_NAME LIKE ?");
            params.add(pattern.toUpperCase());
        }

        query.append(" ORDER BY OBJECT_NAME");

        String queryStr = query.toString();
        return jdbcTemplate.query(queryStr, 
            (rs, rowNum) -> {
                StoredProcedureInfo info = new StoredProcedureInfo();
                info.setName(rs.getString("OBJECT_NAME"));
                info.setSchema(owner);
                info.setType(rs.getString("OBJECT_TYPE"));
                return info;
            },
            params.toArray()
        );
    }

    @Override
    public TableStatistics getTableStatistics(String tableName, String schema) throws Exception {
        String owner = (schema != null && !schema.isEmpty()) ? schema.toUpperCase() : jdbcTemplate.queryForObject(
            "SELECT USER FROM DUAL", String.class
        );
        String tableNameUpper = tableName.toUpperCase();

        String query = """
            SELECT 
              t.TABLE_NAME,
              t.NUM_ROWS,
              t.LAST_ANALYZED,
              s.SIZE_MB
            FROM ALL_TABLES t
            LEFT JOIN (
              SELECT SEGMENT_NAME, SUM(BYTES) / 1024 / 1024 as SIZE_MB
              FROM DBA_SEGMENTS
              WHERE SEGMENT_TYPE = 'TABLE' AND OWNER = ?
              GROUP BY SEGMENT_NAME
            ) s ON t.TABLE_NAME = s.SEGMENT_NAME
            WHERE t.TABLE_NAME = ? AND t.OWNER = ?
            """;

        List<TableStatistics> results = jdbcTemplate.query(query, 
            (rs, rowNum) -> {
                TableStatistics stats = new TableStatistics();
                stats.setTableName(rs.getString("TABLE_NAME"));
                stats.setSchema(owner);
                stats.setRowCount(rs.getObject("NUM_ROWS") != null ? rs.getInt("NUM_ROWS") : 0);
                stats.setSizeInMB(rs.getObject("SIZE_MB") != null ? rs.getDouble("SIZE_MB") : null);
                stats.setLastAnalyzed(rs.getTimestamp("LAST_ANALYZED"));
                return stats;
            },
            owner, tableNameUpper, owner
        );

        if (results.isEmpty()) {
            throw new Exception("Table " + owner + "." + tableNameUpper + " not found");
        }

        return results.get(0);
    }

    @Override
    public ExplainPlan explainQuery(String sql) throws Exception {
        // Execute EXPLAIN PLAN
        jdbcTemplate.execute("EXPLAIN PLAN FOR " + sql);

        // Retrieve the plan
        String planQuery = "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY())";
        List<String> planLines = jdbcTemplate.queryForList(planQuery, String.class);

        String plan = String.join("\n", planLines);

        ExplainPlan explainPlan = new ExplainPlan();
        explainPlan.setQuery(sql);
        explainPlan.setPlan(plan);
        return explainPlan;
    }

    @Override
    public QueryResult sampleTableData(String tableName, String schema, int limit, boolean random) throws Exception {
        String sql = "SELECT * FROM " + (schema != null ? schema + "." : "") + tableName;
        if (random) {
            sql += " ORDER BY DBMS_RANDOM.VALUE";
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
