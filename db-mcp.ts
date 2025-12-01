#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { DatabaseAdapter, createDatabaseAdapter } from "./database-adapters.js";
import { 
  QueryLogger, 
  MetadataCache, 
  mapDatabaseError, 
  validateWhereClause,
  formatAsCSV,
  formatAsTable,
  UptimeTracker,
  QueryTemplates,
  compareTableSchemas 
} from "./utils.js";

// Type definitions
interface DatabaseConfig {
  type: 'oracle' | 'postgres' | 'sqlserver' | 'mysql' | 'sqlite';
  config: any;
}

// Constants
const DEFAULT_MAX_ROWS = 1000;
const MAX_ALLOWED_ROWS = 10000;

// Validate environment variables and create database configuration
function validateEnvironment(): DatabaseConfig {
  const dbType = (process.env.DB_TYPE || 'oracle').toLowerCase();

  if (!['oracle', 'postgres', 'postgresql', 'sqlserver', 'mssql', 'mysql', 'mariadb', 'sqlite', 'sqlite3'].includes(dbType)) {
    throw new Error(`Unsupported database type: ${dbType}. Supported types: oracle, postgres, sqlserver, mysql, sqlite`);
  }

  switch (dbType) {
    case 'oracle': {
      const requiredEnvVars = ['ORACLE_USER', 'ORACLE_PASSWORD', 'ORACLE_HOST', 'ORACLE_PORT', 'ORACLE_SERVICE'];
      for (const envVar of requiredEnvVars) {
        if (!process.env[envVar]) {
          throw new Error(`Missing required environment variable for Oracle: ${envVar}`);
        }
      }
      return {
        type: 'oracle',
        config: {
          user: process.env.ORACLE_USER!,
          password: process.env.ORACLE_PASSWORD!,
          host: process.env.ORACLE_HOST!,
          port: process.env.ORACLE_PORT!,
          service: process.env.ORACLE_SERVICE!,
          thickMode: !!process.env.ORACLE_CLIENT_LIB_DIR,
          libDir: process.env.ORACLE_CLIENT_LIB_DIR,
        },
      };
    }

    case 'postgres':
    case 'postgresql': {
      const requiredEnvVars = ['PG_USER', 'PG_PASSWORD', 'PG_HOST', 'PG_PORT', 'PG_DATABASE'];
      for (const envVar of requiredEnvVars) {
        if (!process.env[envVar]) {
          throw new Error(`Missing required environment variable for PostgreSQL: ${envVar}`);
        }
      }
      return {
        type: 'postgres',
        config: {
          user: process.env.PG_USER!,
          password: process.env.PG_PASSWORD!,
          host: process.env.PG_HOST!,
          port: parseInt(process.env.PG_PORT!, 10),
          database: process.env.PG_DATABASE!,
        },
      };
    }

    case 'sqlserver':
    case 'mssql': {
      const requiredEnvVars = ['MSSQL_USER', 'MSSQL_PASSWORD', 'MSSQL_SERVER', 'MSSQL_DATABASE'];
      for (const envVar of requiredEnvVars) {
        if (!process.env[envVar]) {
          throw new Error(`Missing required environment variable for SQL Server: ${envVar}`);
        }
      }
      return {
        type: 'sqlserver',
        config: {
          user: process.env.MSSQL_USER!,
          password: process.env.MSSQL_PASSWORD!,
          server: process.env.MSSQL_SERVER!,
          port: process.env.MSSQL_PORT ? parseInt(process.env.MSSQL_PORT, 10) : 1433,
          database: process.env.MSSQL_DATABASE!,
          encrypt: process.env.MSSQL_ENCRYPT !== 'false',
          trustServerCertificate: process.env.MSSQL_TRUST_CERT !== 'false',
        },
      };
    }

    case 'mysql':
    case 'mariadb': {
      const requiredEnvVars = ['MYSQL_USER', 'MYSQL_PASSWORD', 'MYSQL_HOST', 'MYSQL_DATABASE'];
      for (const envVar of requiredEnvVars) {
        if (!process.env[envVar]) {
          throw new Error(`Missing required environment variable for MySQL: ${envVar}`);
        }
      }
      return {
        type: 'mysql',
        config: {
          user: process.env.MYSQL_USER!,
          password: process.env.MYSQL_PASSWORD!,
          host: process.env.MYSQL_HOST!,
          port: process.env.MYSQL_PORT ? parseInt(process.env.MYSQL_PORT, 10) : 3306,
          database: process.env.MYSQL_DATABASE!,
        },
      };
    }

    case 'sqlite':
    case 'sqlite3': {
      const requiredEnvVars = ['SQLITE_PATH'];
      for (const envVar of requiredEnvVars) {
        if (!process.env[envVar]) {
          throw new Error(`Missing required environment variable for SQLite: ${envVar}`);
        }
      }
      return {
        type: 'sqlite',
        config: {
          path: process.env.SQLITE_PATH!,
        },
      };
    }

    default:
      throw new Error(`Unsupported database type: ${dbType}`);
  }
}

const dbConfig = validateEnvironment();
const dbAdapter: DatabaseAdapter = createDatabaseAdapter(dbConfig.type, dbConfig.config);

// Validate SQL query for security
function validateQuery(sql: string): { isValid: boolean; error?: string } {
  const trimmedSql = sql.trim();
  
  if (!trimmedSql) {
    return { isValid: false, error: "SQL query cannot be empty" };
  }

  // Only allow SELECT and WITH queries
  if (!/^\s*(SELECT|WITH)/i.test(trimmedSql)) {
    return {
      isValid: false,
      error: "Only SELECT and WITH statements are allowed for security reasons",
    };
  }

  // Check for dangerous keywords
  const dangerousKeywords = /\b(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE)\b/i;
  if (dangerousKeywords.test(trimmedSql)) {
    return {
      isValid: false,
      error: "Query contains disallowed SQL operations",
    };
  }

  return { isValid: true };
}

// Create and configure server
const server = new McpServer({
  name: "multi-db-mcp-server",
  version: "1.0.0",
});

// Tool counter to avoid hardcoding
let toolCount = 0;
const originalToolMethod = server.tool.bind(server);
server.tool = function(...args: Parameters<typeof originalToolMethod>) {
  toolCount++;
  return originalToolMethod(...args);
} as typeof server.tool;

// =============================================================================
// TOOL 1: Run Query (Enhanced)
// =============================================================================
server.tool(
  "run-query",
  "Executes a SELECT or WITH query against the database and returns the results. Supports limiting rows, pagination, format options, dry-run mode, and excluding large columns.",
  {
    sql: z.string().describe("The SQL query to execute (SELECT or WITH statements only)."),
    maxRows: z.number().optional().describe("Maximum number of rows to return (default: 1000, max: 10000)"),
    excludeLargeColumns: z.boolean().optional().describe("Exclude BLOB/CLOB/TEXT columns from results (default: false)"),
    format: z.enum(['json', 'csv', 'table']).optional().describe("Output format: json (default), csv, or table"),
    dryRun: z.boolean().optional().describe("Validate query without executing (default: false)"),
    page: z.number().optional().describe("Page number for pagination (starts at 1)"),
    pageSize: z.number().optional().describe("Number of rows per page (used with page parameter)"),
  },
  async ({ sql, maxRows, excludeLargeColumns, format, dryRun, page, pageSize }) => {
    const startTime = Date.now();

    try {
      // Validate SQL
      const validation = validateQuery(sql);
      if (!validation.isValid) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ success: false, error: validation.error }, null, 2),
          }],
        };
      }

      // Dry run mode - just validate
      if (dryRun) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({
              success: true,
              dryRun: true,
              message: "Query syntax is valid",
              query: sql.substring(0, 200),
            }, null, 2),
          }],
        };
      }

      // Handle pagination
      let effectiveMaxRows = maxRows || DEFAULT_MAX_ROWS;
      if (page && pageSize) {
        const offset = (page - 1) * pageSize;
        sql = `${sql} LIMIT ${pageSize} OFFSET ${offset}`;
        effectiveMaxRows = pageSize;
      }

      // Validate maxRows
      const rowLimit = Math.min(effectiveMaxRows, MAX_ALLOWED_ROWS);

      const result = await dbAdapter.executeQuery(sql, rowLimit, excludeLargeColumns || false);
      const executionTime = Date.now() - startTime;

      // Log query
      QueryLogger.log(sql, executionTime, true);

      // Format output
      let formattedOutput: string;
      if (format === 'csv') {
        formattedOutput = formatAsCSV(result.columns, result.rows);
      } else if (format === 'table') {
        formattedOutput = formatAsTable(result.columns, result.rows);
      } else {
        formattedOutput = JSON.stringify({
          success: true,
          rowCount: result.rowCount,
          columns: result.columns,
          rows: result.rows,
          metadata: {
            executionTimeMs: executionTime,
            maxRowsLimit: rowLimit,
            truncated: result.rowCount === rowLimit,
            format: format || 'json',
            excludedLargeColumns: excludeLargeColumns || false,
          },
        }, null, 2);
      }

      return {
        content: [{
          type: "text",
          text: formattedOutput,
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      
      QueryLogger.log(sql, executionTime, false, errorMessage);
      console.error("Query execution error:", errorMessage);
      
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            query: sql.substring(0, 100) + (sql.length > 100 ? "..." : ""),
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 2: Get Table Info (Enhanced)
// =============================================================================
server.tool(
  "get-table-info",
  "Retrieves detailed information about a table including columns, data types, constraints, primary keys, and foreign keys.",
  {
    tableName: z.string().describe("The name of the table to inspect"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ tableName, schema }) => {
    try {
      // Check cache first
      const cacheKey = `table-info:${schema || 'default'}:${tableName}`;
      const cached = MetadataCache.get(cacheKey);
      if (cached) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ ...cached, fromCache: true }, null, 2),
          }],
        };
      }

      const tableInfo = await dbAdapter.getTableInfo(tableName, schema);

      const response = {
        success: true,
        tableName: tableInfo.tableName,
        schema: tableInfo.schema || tableInfo.owner,
        rowCount: tableInfo.rowCount,
        columns: tableInfo.columns,
      };

      // Cache the result
      MetadataCache.set(cacheKey, response);

      return {
        content: [{
          type: "text",
          text: JSON.stringify(response, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Table info error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 3: List Tables
// =============================================================================
server.tool(
  "list-tables",
  "Lists all tables accessible by the current user with row counts.",
  {
    schema: z.string().optional().describe("Filter by schema/owner (default: current user or default schema)"),
    pattern: z.string().optional().describe("Filter tables by name pattern (use % as wildcard)"),
  },
  async ({ schema, pattern }) => {
    try {
      const cacheKey = `tables:${schema || 'default'}:${pattern || 'all'}`;
      const cached = MetadataCache.get(cacheKey);
      if (cached) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ ...cached, fromCache: true }, null, 2),
          }],
        };
      }

      const tables = await dbAdapter.listTables(schema, pattern);

      const response = {
        success: true,
        schema: schema || 'default',
        tableCount: tables.length,
        tables: tables,
      };

      MetadataCache.set(cacheKey, response);

      return {
        content: [{
          type: "text",
          text: JSON.stringify(response, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("List tables error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 4: Get Row Count (Enhanced)
// =============================================================================
server.tool(
  "get-row-count",
  "Gets the total number of rows in a table quickly without retrieving data. Supports optional WHERE clause for filtered counts with SQL injection protection.",
  {
    tableName: z.string().describe("The name of the table to count rows from"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
    whereClause: z.string().optional().describe("Optional WHERE clause to filter the count (e.g., 'status = ''active''')"),
  },
  async ({ tableName, schema, whereClause }) => {
    const startTime = Date.now();

    try {
      // Validate WHERE clause
      if (whereClause) {
        const validation = validateWhereClause(whereClause);
        if (!validation.isValid) {
          return {
            content: [{
              type: "text",
              text: JSON.stringify({
                success: false,
                error: validation.error,
              }, null, 2),
            }],
          };
        }
      }

      const rowCount = await dbAdapter.getRowCount(tableName, schema, whereClause);
      const executionTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            tableName: tableName,
            schema: schema || 'default',
            rowCount: rowCount,
            whereClause: whereClause || null,
            metadata: {
              executionTimeMs: executionTime,
            },
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Row count error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 5: List Schemas
// =============================================================================
server.tool(
  "list-schemas",
  "Lists all schemas/databases accessible in the current database instance with table and view counts.",
  {},
  async () => {
    try {
      const cacheKey = 'schemas:all';
      const cached = MetadataCache.get(cacheKey);
      if (cached) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ ...cached, fromCache: true }, null, 2),
          }],
        };
      }

      const schemas = await dbAdapter.listSchemas();

      const response = {
        success: true,
        schemaCount: schemas.length,
        schemas: schemas,
      };

      MetadataCache.set(cacheKey, response);

      return {
        content: [{
          type: "text",
          text: JSON.stringify(response, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("List schemas error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 6: List Views
// =============================================================================
server.tool(
  "list-views",
  "Lists all views accessible by the current user.",
  {
    schema: z.string().optional().describe("Filter by schema/owner (default: current user or default schema)"),
    pattern: z.string().optional().describe("Filter views by name pattern (use % as wildcard)"),
  },
  async ({ schema, pattern }) => {
    try {
      const cacheKey = `views:${schema || 'default'}:${pattern || 'all'}`;
      const cached = MetadataCache.get(cacheKey);
      if (cached) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ ...cached, fromCache: true }, null, 2),
          }],
        };
      }

      const views = await dbAdapter.listViews(schema, pattern);

      const response = {
        success: true,
        schema: schema || 'default',
        viewCount: views.length,
        views: views,
      };

      MetadataCache.set(cacheKey, response);

      return {
        content: [{
          type: "text",
          text: JSON.stringify(response, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("List views error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 7: Get View Definition
// =============================================================================
server.tool(
  "get-view-definition",
  "Retrieves the SQL definition of a view.",
  {
    viewName: z.string().describe("The name of the view to inspect"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ viewName, schema }) => {
    try {
      const viewDef = await dbAdapter.getViewDefinition(viewName, schema);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            viewName: viewDef.name,
            schema: viewDef.schema || viewDef.owner,
            definition: viewDef.definition,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Get view definition error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            viewName: viewName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 8: Get Indexes (NEW)
// =============================================================================
server.tool(
  "get-indexes",
  "Retrieves index information for a table including column names, uniqueness, and index type.",
  {
    tableName: z.string().describe("The name of the table to get indexes for"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ tableName, schema }) => {
    try {
      const indexes = await dbAdapter.getIndexes(tableName, schema);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            tableName: tableName,
            schema: schema || 'default',
            indexCount: indexes.length,
            indexes: indexes,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Get indexes error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 9: Get Foreign Keys (NEW)
// =============================================================================
server.tool(
  "get-foreign-keys",
  "Retrieves foreign key relationships for a table including referenced tables, columns, and cascade rules.",
  {
    tableName: z.string().describe("The name of the table to get foreign keys for"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ tableName, schema }) => {
    try {
      const foreignKeys = await dbAdapter.getForeignKeys(tableName, schema);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            tableName: tableName,
            schema: schema || 'default',
            foreignKeyCount: foreignKeys.length,
            foreignKeys: foreignKeys,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Get foreign keys error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 10: List Stored Procedures (NEW)
// =============================================================================
server.tool(
  "list-stored-procedures",
  "Lists all stored procedures and functions accessible in the database.",
  {
    schema: z.string().optional().describe("Filter by schema/owner (default: current user or default schema)"),
    pattern: z.string().optional().describe("Filter by name pattern (use % as wildcard)"),
  },
  async ({ schema, pattern }) => {
    try {
      const procedures = await dbAdapter.listStoredProcedures(schema, pattern);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            schema: schema || 'default',
            procedureCount: procedures.length,
            procedures: procedures,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("List stored procedures error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 11: Get Table Statistics (NEW)
// =============================================================================
server.tool(
  "get-table-statistics",
  "Retrieves advanced table statistics including size in MB, index size, row count, and last analyzed date.",
  {
    tableName: z.string().describe("The name of the table to get statistics for"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ tableName, schema }) => {
    try {
      const stats = await dbAdapter.getTableStatistics(tableName, schema);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            statistics: stats,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Get table statistics error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 12: Explain Query (NEW)
// =============================================================================
server.tool(
  "explain-query",
  "Analyzes a query's execution plan without actually executing it. Shows estimated costs and rows.",
  {
    sql: z.string().describe("The SQL query to analyze"),
  },
  async ({ sql }) => {
    try {
      // Validate SQL first
      const validation = validateQuery(sql);
      if (!validation.isValid) {
        return {
          content: [{
            type: "text",
            text: JSON.stringify({ success: false, error: validation.error }, null, 2),
          }],
        };
      }

      const plan = await dbAdapter.explainQuery(sql);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            query: plan.query,
            executionPlan: plan.plan,
            estimatedCost: plan.estimatedCost,
            estimatedRows: plan.estimatedRows,
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Explain query error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 13: Sample Table Data (NEW)
// =============================================================================
server.tool(
  "sample-table-data",
  "Quickly preview table data without writing SQL. Supports random sampling.",
  {
    tableName: z.string().describe("The name of the table to sample"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
    limit: z.number().optional().describe("Number of rows to return (default: 10, max: 100)"),
    random: z.boolean().optional().describe("Use random sampling instead of top rows (default: false)"),
  },
  async ({ tableName, schema, limit, random }) => {
    const startTime = Date.now();

    try {
      const rowLimit = Math.min(limit || 10, 100);
      const result = await dbAdapter.sampleTableData(tableName, schema, rowLimit, random || false);
      const executionTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            tableName: tableName,
            schema: schema || 'default',
            rowCount: result.rowCount,
            columns: result.columns,
            rows: result.rows,
            metadata: {
              executionTimeMs: executionTime,
              samplingMethod: random ? 'random' : 'top',
              limit: rowLimit,
            },
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? mapDatabaseError(error, dbConfig.type) : "Unknown database error";
      console.error("Sample table data error:", errorMessage);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            tableName: tableName,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 14: Test Connection (NEW)
// =============================================================================
server.tool(
  "test-connection",
  "Tests the database connection and returns connection details and status.",
  {},
  async () => {
    const startTime = Date.now();

    try {
      const isConnected = await dbAdapter.testConnection();
      const responseTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: isConnected,
            databaseType: dbConfig.type,
            responseTimeMs: responseTime,
            status: isConnected ? 'connected' : 'connection failed',
            timestamp: new Date().toISOString(),
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Unknown error";
      const responseTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            databaseType: dbConfig.type,
            responseTimeMs: responseTime,
            status: 'connection failed',
            error: errorMessage,
            timestamp: new Date().toISOString(),
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 15: Performance Metrics (NEW)
// =============================================================================
server.tool(
  "get-performance-metrics",
  "Returns query performance statistics including average execution time, success rate, and slowest queries.",
  {},
  async () => {
    try {
      const stats = QueryLogger.getStats();
      const logs = QueryLogger.getLogs();
      const cacheStats = MetadataCache.getStats();

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            queryStats: stats,
            recentQueries: logs.slice(-10),
            cacheStats: cacheStats,
            uptime: UptimeTracker.getUptimeFormatted(),
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Unknown error";

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 16: Health Check (NEW)
// =============================================================================
server.tool(
  "health-check",
  "Returns comprehensive server health status including uptime, connection status, cache status, and diagnostics.",
  {},
  async () => {
    try {
      const connectionTest = await dbAdapter.testConnection();
      const cacheStats = MetadataCache.getStats();
      const queryStats = QueryLogger.getStats();

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            status: connectionTest ? 'healthy' : 'degraded',
            server: {
              version: "1.0.0",
              uptime: UptimeTracker.getUptimeFormatted(),
              uptimeMs: UptimeTracker.getUptime(),
            },
            database: {
              type: dbConfig.type,
              connected: connectionTest,
            },
            performance: {
              totalQueries: queryStats.totalQueries,
              successRate: queryStats.successRate,
              avgExecutionTimeMs: queryStats.avgExecutionTime,
            },
            cache: cacheStats,
            timestamp: new Date().toISOString(),
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Unknown error";

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            status: 'unhealthy',
            error: errorMessage,
            timestamp: new Date().toISOString(),
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 17: Stream Query Results (NEW)
// =============================================================================
server.tool(
  "stream-query",
  "Executes a SELECT query and streams results in batches to handle very large datasets efficiently. Returns results in chunks to avoid memory issues.",
  {
    sql: z.string().describe("The SQL query to execute (SELECT only)."),
    batchSize: z.number().optional().describe("Number of rows per batch (default: 100)"),
  },
  async ({ sql, batchSize }) => {
    const startTime = Date.now();

    try {
      // Validate SQL query
      const validation = validateQuery(sql);
      if (!validation.isValid) {
        QueryLogger.log(sql, 0, false);
        return {
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              error: validation.error,
            }, null, 2),
          }],
        };
      }

      const batch_size = Math.min(batchSize || 100, 1000);
      const allBatches: any[] = [];
      let totalRows = 0;

      // Stream results in batches
      for await (const batch of dbAdapter.executeQueryStream(sql, batch_size)) {
        allBatches.push(...batch);
        totalRows += batch.length;
      }

      const executionTime = Date.now() - startTime;
      QueryLogger.log(sql, executionTime, true);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            totalRows,
            executionTimeMs: executionTime,
            batchSize: batch_size,
            data: allBatches,
            note: "Results streamed in batches for efficiency",
          }, null, 2),
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";
      QueryLogger.log(sql, executionTime, false, errorMessage);
      
      const friendlyError = mapDatabaseError(error as Error, dbConfig.type);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: friendlyError,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 18: Execute Multiple Queries (NEW)
// =============================================================================
server.tool(
  "run-multi-query",
  "Executes multiple SELECT statements in sequence and returns an array of results. Each query runs independently.",
  {
    queries: z.array(z.string()).describe("Array of SQL queries to execute (SELECT statements only)."),
    maxRowsPerQuery: z.number().optional().describe("Maximum rows per query (default: 1000)"),
  },
  async ({ queries, maxRowsPerQuery }) => {
    const startTime = Date.now();
    const results: any[] = [];
    const maxRows = Math.min(maxRowsPerQuery || DEFAULT_MAX_ROWS, MAX_ALLOWED_ROWS);

    try {
      // Validate all queries first
      for (let i = 0; i < queries.length; i++) {
        const validation = validateQuery(queries[i]);
        if (!validation.isValid) {
          return {
            content: [{
              type: "text",
              text: JSON.stringify({
                success: false,
                error: `Query ${i + 1} validation failed: ${validation.error}`,
              }, null, 2),
            }],
          };
        }
      }

      // Execute all queries
      for (let i = 0; i < queries.length; i++) {
        const queryStartTime = Date.now();
        const sql = queries[i];
        
        try {
          const result = await dbAdapter.executeQuery(sql, maxRows);
          const queryExecutionTime = Date.now() - queryStartTime;
          
          QueryLogger.log(sql, queryExecutionTime, true);
          
          results.push({
            queryIndex: i + 1,
            success: true,
            rowCount: result.rowCount,
            executionTimeMs: queryExecutionTime,
            columns: result.columns,
            rows: result.rows,
          });
        } catch (error) {
          const queryExecutionTime = Date.now() - queryStartTime;
          const errorMessage = error instanceof Error ? error.message : "Unknown error";
          QueryLogger.log(sql, queryExecutionTime, false, errorMessage);
          
          results.push({
            queryIndex: i + 1,
            success: false,
            error: mapDatabaseError(error as Error, dbConfig.type),
            executionTimeMs: queryExecutionTime,
          });
        }
      }

      const totalExecutionTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            totalQueries: queries.length,
            successfulQueries: results.filter(r => r.success).length,
            failedQueries: results.filter(r => !r.success).length,
            totalExecutionTimeMs: totalExecutionTime,
            results,
          }, null, 2),
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: mapDatabaseError(error as Error, dbConfig.type),
            partialResults: results,
            executionTimeMs: executionTime,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 19: Execute Read-Only Transaction (NEW)
// =============================================================================
server.tool(
  "run-transaction",
  "Executes multiple SELECT queries within a single read-only transaction to ensure data consistency across all queries.",
  {
    queries: z.array(z.string()).describe("Array of SQL queries to execute in transaction (SELECT statements only)."),
  },
  async ({ queries }) => {
    const startTime = Date.now();

    try {
      // Validate all queries first
      for (let i = 0; i < queries.length; i++) {
        const validation = validateQuery(queries[i]);
        if (!validation.isValid) {
          return {
            content: [{
              type: "text",
              text: JSON.stringify({
                success: false,
                error: `Query ${i + 1} validation failed: ${validation.error}`,
              }, null, 2),
            }],
          };
        }
      }

      // Execute in transaction
      const results = await dbAdapter.executeTransaction(queries);
      const executionTime = Date.now() - startTime;

      // Log all queries
      queries.forEach(sql => QueryLogger.log(sql, executionTime / queries.length, true));

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            transactionMode: 'READ ONLY',
            totalQueries: queries.length,
            executionTimeMs: executionTime,
            results: results.map((r, i) => ({
              queryIndex: i + 1,
              rowCount: r.rowCount,
              columns: r.columns,
              rows: r.rows,
            })),
          }, null, 2),
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";
      queries.forEach(sql => QueryLogger.log(sql, executionTime / queries.length, false, errorMessage));

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: mapDatabaseError(error as Error, dbConfig.type),
            executionTimeMs: executionTime,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 20: List Query Templates (NEW)
// =============================================================================
server.tool(
  "list-query-templates",
  "Lists all available pre-defined query templates with their parameters and descriptions.",
  {},
  async () => {
    try {
      const templates = QueryTemplates.list();

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            totalTemplates: templates.length,
            templates: templates.map(t => ({
              id: t.id,
              name: t.name,
              description: t.description,
              parameters: t.parameters,
              example: t.sql,
            })),
          }, null, 2),
        }],
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Unknown error";

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 21: Execute Query Template (NEW)
// =============================================================================
server.tool(
  "execute-template",
  "Executes a pre-defined query template with parameter substitution. Use list-query-templates to see available templates.",
  {
    templateId: z.string().describe("The ID of the template to execute"),
    parameters: z.record(z.string()).describe("Key-value pairs for template parameters"),
    maxRows: z.number().optional().describe("Maximum number of rows to return (default: 1000)"),
  },
  async ({ templateId, parameters, maxRows }) => {
    const startTime = Date.now();

    try {
      // Get and execute template
      const sql = QueryTemplates.execute(templateId, parameters);
      
      // Validate the generated SQL
      const validation = validateQuery(sql);
      if (!validation.isValid) {
        QueryLogger.log(sql, 0, false);
        return {
          content: [{
            type: "text",
            text: JSON.stringify({
              success: false,
              error: validation.error,
              generatedSQL: sql,
            }, null, 2),
          }],
        };
      }

      const max_rows = Math.min(maxRows || DEFAULT_MAX_ROWS, MAX_ALLOWED_ROWS);
      
      // Execute the generated query
      const result = await dbAdapter.executeQuery(sql, max_rows);
      const executionTime = Date.now() - startTime;
      
      QueryLogger.log(sql, executionTime, true);

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            templateId,
            generatedSQL: sql,
            rowCount: result.rowCount,
            executionTimeMs: executionTime,
            columns: result.columns,
            rows: result.rows,
          }, null, 2),
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";
      
      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: errorMessage,
            executionTimeMs: executionTime,
          }, null, 2),
        }],
      };
    }
  }
);

// =============================================================================
// TOOL 22: Compare Table Schemas (NEW)
// =============================================================================
server.tool(
  "compare-schemas",
  "Compares the structure of two tables (same table in different schemas or different tables). Returns differences in columns, types, and constraints.",
  {
    table1: z.string().describe("First table name"),
    schema1: z.string().optional().describe("Schema for first table"),
    table2: z.string().describe("Second table name"),
    schema2: z.string().optional().describe("Schema for second table"),
  },
  async ({ table1, schema1, table2, schema2 }) => {
    const startTime = Date.now();

    try {
      // Get table info for both tables
      const tableInfo1 = await dbAdapter.getTableInfo(table1, schema1);
      const tableInfo2 = await dbAdapter.getTableInfo(table2, schema2);
      
      // Compare schemas
      const comparison = compareTableSchemas(tableInfo1, tableInfo2);
      
      const executionTime = Date.now() - startTime;

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: true,
            comparison: {
              table1: {
                name: table1,
                schema: schema1 || 'default',
                columnCount: tableInfo1.columns.length,
              },
              table2: {
                name: table2,
                schema: schema2 || 'default',
                columnCount: tableInfo2.columns.length,
              },
              status: comparison.status,
              summary: {
                addedColumns: comparison.addedColumns?.length || 0,
                removedColumns: comparison.removedColumns?.length || 0,
                modifiedColumns: comparison.modifiedColumns?.length || 0,
                identicalColumns: comparison.columnDifferences?.filter(c => c.status === 'identical').length || 0,
              },
              differences: comparison.columnDifferences,
            },
            executionTimeMs: executionTime,
          }, null, 2),
        }],
      };
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : "Unknown error";

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            success: false,
            error: mapDatabaseError(error as Error, dbConfig.type),
            executionTimeMs: executionTime,
          }, null, 2),
        }],
      };
    }
  }
);

// Graceful shutdown handler
function setupShutdownHandlers() {
  const shutdown = async (signal: string) => {
    console.error(`\nReceived ${signal}, shutting down gracefully...`);
    try {
      await dbAdapter.disconnect();
      console.error("Multi-Database MCP Server stopped");
      process.exit(0);
    } catch (error) {
      console.error("Error during shutdown:", error);
      process.exit(1);
    }
  };

  process.on("SIGINT", () => shutdown("SIGINT"));
  process.on("SIGTERM", () => shutdown("SIGTERM"));
}

// Test connection on startup
async function main() {
  try {
    console.error(`Multi-Database MCP Server v1.0.0 starting...`);
    console.error(`Database type: ${dbConfig.type}`);
    console.error(`Query timeout: ${process.env.QUERY_TIMEOUT_MS || '30000'}ms`);
    console.error(`Query logging: ${process.env.LOG_QUERIES === 'true' ? 'enabled' : 'disabled'}`);
    console.error(`Cache: ${process.env.ENABLE_CACHE !== 'false' ? 'enabled' : 'disabled'}`);

    // Test database connection
    const connectionOk = await dbAdapter.testConnection();
    if (!connectionOk) {
      throw new Error("Database connection test failed");
    }
    console.error("✓ Database connection successful");

    // Setup graceful shutdown
    setupShutdownHandlers();

    // Start server
    const transport = new StdioServerTransport();
    await server.connect(transport);
    
    console.error(`✓ Multi-Database MCP Server started successfully`);
    console.error(`\nAvailable tools (${toolCount}):`);
    console.error(`  Core: run-query, get-table-info, list-tables, get-row-count`);
    console.error(`  Schema: list-schemas, list-views, get-view-definition`);
    console.error(`  Advanced: get-indexes, get-foreign-keys, list-stored-procedures`);
    console.error(`  Analysis: get-table-statistics, explain-query, sample-table-data`);
    console.error(`  System: test-connection, get-performance-metrics, health-check`);
  } catch (error) {
    console.error(`Failed to start Multi-Database MCP Server:`, error);
    if (error instanceof Error) {
      console.error("Error details:", error.message);
      console.error("Stack:", error.stack);
    }
    process.exit(1);
  }
}

main().catch((error) => {
  console.error("Unexpected error:", error);
  process.exit(1);
});
