# Multi-Database MCP Server

Multi-Database MCP Server is a Model Context Protocol (MCP) server that exposes read-only access to Oracle, PostgreSQL, SQL Server, MySQL/MariaDB, and SQLite databases through a uniform toolset. The server focuses on safety and observability while offering advanced capabilities such as streaming, transactions, caching, and schema comparison.

## Highlights

- Read-only query enforcement with SQL injection protections
- Connection pooling, caching, retry logic, and detailed error mapping by adapter
- Supports 22 MCP tools spanning querying, metadata discovery, performance insight, and diagnostics
- Query result streaming, multi-statement execution, and read-only transactions for complex workflows
- Built-in query logging, uptime tracking, and health checks for operational visibility

## Requirements

- Node.js 20 or later (aligns with the esbuild target and latest `oracledb` requirements)
- Database client libraries reachable from the host where the MCP server runs
- Appropriate database network access and credentials

## Installation

```bash
npm install
```

For local development with live reload:

```bash
npm run dev
```

To build the TypeScript sources:

```bash
npm run build
```

To build an optimized bundle (tree-shaken, minified ESM):

```bash
npm run build:bundle
```

## Configuration

Set environment variables before starting the server. Only read-only operations are permitted regardless of configuration.

### Common Variables

| Variable | Description | Default |
| --- | --- | --- |
| `DB_TYPE` | `oracle`, `postgres`, `sqlserver`, `mysql`, or `sqlite` | `oracle` |
| `QUERY_TIMEOUT_MS` | Query timeout in milliseconds | `30000` |
| `LOG_QUERIES` | Enable in-memory query logging (`true` / `false`) | `false` |
| `ENABLE_CACHE` | Enable metadata caching | `true` |
| `CACHE_TTL_MS` | Cache TTL for metadata (ms) | `300000` |

### Oracle

| Variable | Required | Notes |
| --- | --- | --- |
| `ORACLE_USER` | ✅ | |
| `ORACLE_PASSWORD` | ✅ | |
| `ORACLE_HOST` | ✅ | |
| `ORACLE_PORT` | ✅ | e.g. `1521` |
| `ORACLE_SERVICE` | ✅ | Service/SID name |
| `ORACLE_CLIENT_LIB_DIR` | Optional | Required for Oracle 11g thick mode |

### PostgreSQL

| Variable | Required | Notes |
| --- | --- | --- |
| `PG_USER` | ✅ | |
| `PG_PASSWORD` | ✅ | |
| `PG_HOST` | ✅ | |
| `PG_PORT` | ✅ | e.g. `5432` |
| `PG_DATABASE` | ✅ | |

### SQL Server

| Variable | Required | Notes |
| --- | --- | --- |
| `MSSQL_USER` | ✅ | |
| `MSSQL_PASSWORD` | ✅ | |
| `MSSQL_SERVER` | ✅ | Host or IP |
| `MSSQL_DATABASE` | ✅ | |
| `MSSQL_PORT` | Optional | Defaults to `1433` |
| `MSSQL_ENCRYPT` | Optional | Defaults to `true` |
| `MSSQL_TRUST_CERT` | Optional | Defaults to `true` |

### MySQL / MariaDB

| Variable | Required | Notes |
| --- | --- | --- |
| `MYSQL_USER` | ✅ | |
| `MYSQL_PASSWORD` | ✅ | |
| `MYSQL_HOST` | ✅ | |
| `MYSQL_DATABASE` | ✅ | |
| `MYSQL_PORT` | Optional | Defaults to `3306` |

### SQLite

| Variable | Required | Notes |
| --- | --- | --- |
| `SQLITE_PATH` | ✅ | Absolute path to `.db` file |

Create a `.env` file (or export variables inline) with the values relevant to your target database before starting the server.

## Running

After configuration, start the server:

```bash
npm start
```

For direct execution without a build step:

```bash
npx tsx db-mcp.ts
```

The server communicates via stdio and registers the MCP tools automatically when launched by a compatible MCP client.

## Available Tools

| Category | Tool | Purpose |
| --- | --- | --- |
| Query Execution | `run-query` | Execute SELECT/WITH queries with pagination, formatting, and dry-run support |
|  | `run-multi-query` | Execute a list of read-only statements sequentially |
|  | `run-transaction` | Execute read-only statements inside a transaction |
|  | `stream-query` | Stream large result sets in batches |
| Metadata | `list-schemas` | List schemas with table/view counts |
|  | `list-tables` | Enumerate tables with row counts |
|  | `get-table-info` | Retrieve column metadata, PK/FK info, and row counts |
|  | `get-indexes` | List indexes and key details |
|  | `get-foreign-keys` | List foreign key relationships |
|  | `list-views` | Enumerate views |
|  | `get-view-definition` | Return definition SQL for a view |
|  | `list-stored-procedures` | List stored procedures and functions |
|  | `get-table-statistics` | Retrieve table size, index size, and fragmentation metadata |
| Counting & Sampling | `get-row-count` | Fast row counts with optional filter |
|  | `sample-table-data` | Retrieve top or random rows |
| Analysis | `explain-query` | Obtain database-specific execution plans |
| Templates | `list-query-templates` | Discover available parameterized templates |
|  | `execute-template` | Run a predefined template with parameters |
| Diagnostics | `test-connection` | Validate connectivity and response time |
|  | `health-check` | Aggregate health information for the server |
|  | `get-performance-metrics` | View query metrics, cache stats, and uptime |
| Schema Ops | `compare-schemas` | Compare table definitions across schemas |

Each tool validates inputs with `zod` schemas and returns structured JSON (with optional CSV/table formatting for queries).

## Security & Reliability

- Only `SELECT` and `WITH` statements are accepted; DDL/DML commands are rejected during validation.
- WHERE clause validation detects disallowed keywords to mitigate injection attempts.
- Connection pools and retry logic help recover from transient failures without restarting the server.
- Metadata caching honors configurable TTLs to reduce database load.

## Troubleshooting

- **Test connectivity**: Use the `test-connection` tool; review logs when `LOG_QUERIES=true`.
- **Check configuration**: Confirm environment variables match the chosen `DB_TYPE`.
- **Oracle thick mode**: Set `ORACLE_CLIENT_LIB_DIR` when using Instant Client 11g packages.
- **SQLite path**: Ensure the process has read permissions on the `.db` file.

## Development Notes

- TypeScript sources live at `db-mcp.ts`, `database-adapters.ts`, and `utils.ts`.
- Type definitions for the Oracle client extend the upstream declarations in `oracledb.d.ts`.
- Run `npm run build` before publishing to ensure the compiled artifacts in `dist/` are current.
- Use `npm run build:bundle` to produce a single-file bundle suitable for distribution without TypeScript sources.

## License

MIT
