# Multi-Database MCP Server

A Model Context Protocol (MCP) server that provides read-only access to multiple database systems: **Oracle**, **PostgreSQL**, and **SQL Server**.

## Features

- **Multi-database support**: Connect to Oracle, PostgreSQL, or SQL Server
- Secure read-only database queries (SELECT and WITH statements only)
- Unified interface across different database systems
- Proper error handling and connection management
- Environment variable based configuration
- TypeScript support with proper type definitions
- Database-specific metadata queries using information_schema and system catalogs

## Installation

Install dependencies:

```bash
npm install
```

## Configuration

The server supports three database types: **Oracle**, **PostgreSQL**, and **SQL Server**. Configure using environment variables.

### Oracle Configuration

```bash
DB_TYPE=oracle
ORACLE_USER=your_username
ORACLE_PASSWORD=your_password
ORACLE_HOST=your_host
ORACLE_PORT=1521
ORACLE_SERVICE=your_service_name
# Optional: For Oracle 11g support (Thick mode)
ORACLE_CLIENT_LIB_DIR=C:\oracle\instantclient_19_11_64bits
```

### PostgreSQL Configuration

```bash
DB_TYPE=postgres
PG_USER=your_username
PG_PASSWORD=your_password
PG_HOST=your_host
PG_PORT=5432
PG_DATABASE=your_database
```

### SQL Server Configuration

```bash
DB_TYPE=sqlserver
MSSQL_USER=your_username
MSSQL_PASSWORD=your_password
MSSQL_SERVER=your_server
MSSQL_PORT=1433
MSSQL_DATABASE=your_database
# Optional: Connection encryption settings
MSSQL_ENCRYPT=true
MSSQL_TRUST_CERT=true
```

## Usage

### Development

Run in development mode with hot reload:

```bash
npm run dev
```

### Production

Build and run:

```bash
npm run build
npm start
```

### Direct execution

You can also run the TypeScript file directly:

```bash
npx tsx db-mcp.ts
```

## Available Tools

### run-query

Executes a read-only SELECT or WITH query against the database.

**Parameters:**
- `sql` (required): The SQL query to execute (SELECT or WITH statements only)
- `maxRows` (optional): Maximum number of rows to return (default: 1000, max: 10000)

**Security Note:** Only SELECT and WITH statements are allowed for security reasons. INSERT, UPDATE, DELETE, and DDL statements will be rejected.

### get-table-info

Retrieves detailed information about a table including columns, data types, and constraints.

**Parameters:**
- `tableName` (required): The name of the table to inspect
- `schema` (optional): The schema/owner (default: current user or default schema)

### list-tables

Lists all tables accessible by the current user with row counts.

**Parameters:**
- `schema` (optional): Filter by schema/owner (default: current user or default schema)
- `pattern` (optional): Filter tables by name pattern (use % as wildcard)

### get-row-count

Gets the total number of rows in a table quickly without retrieving data.

**Parameters:**
- `tableName` (required): The name of the table to count rows from
- `schema` (optional): The schema/owner (default: current user or default schema)
- `whereClause` (optional): Optional WHERE clause to filter the count

### list-schemas

Lists all schemas/databases accessible in the current database instance.

**Parameters:** None

**Returns:** List of schemas with table and view counts

### list-views

Lists all views accessible by the current user.

**Parameters:**
- `schema` (optional): Filter by schema/owner (default: current user or default schema)
- `pattern` (optional): Filter views by name pattern (use % as wildcard)

### get-view-definition

Retrieves the SQL definition of a view.

**Parameters:**
- `viewName` (required): The name of the view to inspect
- `schema` (optional): The schema/owner (default: current user or default schema)

## Environment Variables Reference

### Common Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `DB_TYPE` | Database type: `oracle`, `postgres`, or `sqlserver` | No | `oracle` |

### Oracle Variables

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `ORACLE_USER` | Oracle database username | Yes | `myuser` |
| `ORACLE_PASSWORD` | Oracle database password | Yes | `mypassword` |
| `ORACLE_HOST` | Oracle database host | Yes | `localhost` |
| `ORACLE_PORT` | Oracle database port | Yes | `1521` |
| `ORACLE_SERVICE` | Oracle service name | Yes | `XE` |
| `ORACLE_CLIENT_LIB_DIR` | Path to Oracle Instant Client (for 11g support) | No | `C:\oracle\instantclient` |

### PostgreSQL Variables

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `PG_USER` | PostgreSQL username | Yes | `postgres` |
| `PG_PASSWORD` | PostgreSQL password | Yes | `mypassword` |
| `PG_HOST` | PostgreSQL host | Yes | `localhost` |
| `PG_PORT` | PostgreSQL port | Yes | `5432` |
| `PG_DATABASE` | PostgreSQL database name | Yes | `mydb` |

### SQL Server Variables

| Variable | Description | Required | Default | Example |
|----------|-------------|----------|---------|---------|
| `MSSQL_USER` | SQL Server username | Yes | - | `sa` |
| `MSSQL_PASSWORD` | SQL Server password | Yes | - | `MyPass123!` |
| `MSSQL_SERVER` | SQL Server hostname | Yes | - | `localhost` |
| `MSSQL_PORT` | SQL Server port | No | `1433` | `1433` |
| `MSSQL_DATABASE` | SQL Server database name | Yes | - | `master` |
| `MSSQL_ENCRYPT` | Enable connection encryption | No | `true` | `true` or `false` |
| `MSSQL_TRUST_CERT` | Trust server certificate | No | `true` | `true` or `false` |

## Error Handling

The server includes comprehensive error handling:
- Connection validation on startup
- Proper cleanup of database connections
- Detailed error messages for debugging
- Graceful handling of invalid queries

## License

MIT