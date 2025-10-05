#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { DatabaseAdapter, createDatabaseAdapter } from "./database-adapters.js";

// Type definitions
interface DatabaseConfig {
  type: 'oracle' | 'postgres' | 'sqlserver';
  config: any;
}

// Constants
const DEFAULT_MAX_ROWS = 1000;
const MAX_ALLOWED_ROWS = 10000;

// Validate environment variables and create database configuration
function validateEnvironment(): DatabaseConfig {
  const dbType = (process.env.DB_TYPE || 'oracle').toLowerCase();

  if (!['oracle', 'postgres', 'postgresql', 'sqlserver', 'mssql'].includes(dbType)) {
    throw new Error(`Unsupported database type: ${dbType}. Supported types: oracle, postgres, sqlserver`);
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

    default:
      throw new Error(`Unsupported database type: ${dbType}`);
  }
}

const dbConfig = validateEnvironment();
const dbAdapter: DatabaseAdapter = createDatabaseAdapter(dbConfig.type, dbConfig.config);

// Validate SQL query for security
function validateQuery(sql: string): { isValid: boolean; error?: string } {
  const trimmedSql = sql.trim();
  
  // Check if empty
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
  const dangerousKeywords = /\b(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE|TRUNCATE|GRANT|REVOKE)\b/i;
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
  version: "0.3.0",
});

// TOOLS...

// Tool 1: Run Query
server.tool(
  "run-query",
  "Executes a SELECT or WITH query against the database and returns the results. Supports limiting rows returned.",
  {
    sql: z.string().describe("The SQL query to execute (SELECT or WITH statements only)."),
    maxRows: z.number().optional().describe("Maximum number of rows to return (default: 1000, max: 10000)"),
  },
  async ({ sql, maxRows }) => {
    // Validate SQL
    const validation = validateQuery(sql);
    if (!validation.isValid) {
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: validation.error,
              },
              null,
              2
            ),
          },
        ],
      };
    }

    // Validate maxRows
    const rowLimit = Math.min(
      maxRows || DEFAULT_MAX_ROWS,
      MAX_ALLOWED_ROWS
    );

    const startTime = Date.now();

    try {
      const result = await dbAdapter.executeQuery(sql, rowLimit);
      const executionTime = Date.now() - startTime;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                rowCount: result.rowCount,
                columns: result.columns,
                rows: result.rows,
                metadata: {
                  executionTimeMs: executionTime,
                  maxRowsLimit: rowLimit,
                  truncated: result.rowCount === rowLimit,
                },
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("Query execution error:", errorMessage);
      
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
                query: sql.substring(0, 100) + (sql.length > 100 ? "..." : ""),
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 2: Get Table Info
server.tool(
  "get-table-info",
  "Retrieves detailed information about a table including columns, data types, and constraints.",
  {
    tableName: z.string().describe("The name of the table to inspect"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
  },
  async ({ tableName, schema }) => {
    try {
      const tableInfo = await dbAdapter.getTableInfo(tableName, schema);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                tableName: tableInfo.tableName,
                schema: tableInfo.schema || tableInfo.owner,
                rowCount: tableInfo.rowCount,
                columns: tableInfo.columns,
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("Table info error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
                tableName: tableName,
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 3: List Tables
server.tool(
  "list-tables",
  "Lists all tables accessible by the current user with row counts.",
  {
    schema: z.string().optional().describe("Filter by schema/owner (default: current user or default schema)"),
    pattern: z.string().optional().describe("Filter tables by name pattern (use % as wildcard)"),
  },
  async ({ schema, pattern }) => {
    try {
      const tables = await dbAdapter.listTables(schema, pattern);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                schema: schema || 'default',
                tableCount: tables.length,
                tables: tables,
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("List tables error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 4: Get Row Count
server.tool(
  "get-row-count",
  "Gets the total number of rows in a table quickly without retrieving data. Supports optional WHERE clause for filtered counts.",
  {
    tableName: z.string().describe("The name of the table to count rows from"),
    schema: z.string().optional().describe("The schema/owner (default: current user or default schema)"),
    whereClause: z.string().optional().describe("Optional WHERE clause to filter the count (e.g., 'voltage_level > 20')"),
  },
  async ({ tableName, schema, whereClause }) => {
    const startTime = Date.now();

    try {
      // Basic validation for WHERE clause
      if (whereClause) {
        const dangerousKeywords = /\b(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE|TRUNCATE|GRANT|REVOKE)\b/i;
        if (dangerousKeywords.test(whereClause)) {
          throw new Error("WHERE clause contains disallowed SQL operations");
        }
      }

      const rowCount = await dbAdapter.getRowCount(tableName, schema, whereClause);
      const executionTime = Date.now() - startTime;

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                tableName: tableName,
                schema: schema || 'default',
                rowCount: rowCount,
                whereClause: whereClause || null,
                metadata: {
                  executionTimeMs: executionTime,
                },
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("Row count error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
                tableName: tableName,
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 5: List Schemas
server.tool(
  "list-schemas",
  "Lists all schemas/databases accessible in the current database instance.",
  {},
  async () => {
    try {
      const schemas = await dbAdapter.listSchemas();

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                schemaCount: schemas.length,
                schemas: schemas,
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("List schemas error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 6: List Views
server.tool(
  "list-views",
  "Lists all views accessible by the current user.",
  {
    schema: z.string().optional().describe("Filter by schema/owner (default: current user or default schema)"),
    pattern: z.string().optional().describe("Filter views by name pattern (use % as wildcard)"),
  },
  async ({ schema, pattern }) => {
    try {
      const views = await dbAdapter.listViews(schema, pattern);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                schema: schema || 'default',
                viewCount: views.length,
                views: views,
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("List views error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
              },
              null,
              2
            ),
          },
        ],
      };
    }
  }
);

// Tool 7: Get View Definition
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
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: true,
                viewName: viewDef.name,
                schema: viewDef.schema || viewDef.owner,
                definition: viewDef.definition,
              },
              null,
              2
            ),
          },
        ],
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown database error";
      console.error("Get view definition error:", errorMessage);

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(
              {
                success: false,
                error: errorMessage,
                viewName: viewName,
              },
              null,
              2
            ),
          },
        ],
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
    console.error(`Multi-Database MCP Server starting...`);
    console.error(`Database type: ${dbConfig.type}`);

    // Test database connection
    const connectionOk = await dbAdapter.testConnection();
    if (!connectionOk) {
      throw new Error("Database connection test failed");
    }
    console.error("Database connection successful");

    // Setup graceful shutdown
    setupShutdownHandlers();

    // Start server
    const transport = new StdioServerTransport();
    await server.connect(transport);
    console.error(`Multi-Database MCP Server started successfully (${dbConfig.type})`);
    console.error("Available tools: run-query, get-table-info, list-tables, get-row-count, list-schemas, list-views, get-view-definition");
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
