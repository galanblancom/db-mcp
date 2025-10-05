# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2025-10-05

### Added

- **Multi-database support**: Added support for PostgreSQL and SQL Server in addition to Oracle
- **New Tools**:
  - `list-schemas`: Lists all schemas/databases accessible in the current database instance
  - `list-views`: Lists all views accessible by the current user with optional filtering
  - `get-view-definition`: Retrieves the SQL definition of a view
- **Database adapter pattern**: Implemented abstract `DatabaseAdapter` class with concrete implementations:
  - `OracleAdapter`: Oracle database support (11g+ with Thick mode, 12.1+ with Thin mode)
  - `PostgresAdapter`: PostgreSQL database support
  - `SQLServerAdapter`: SQL Server database support
- **New environment variables**:
  - `DB_TYPE`: Specify database type (oracle, postgres, sqlserver)
  - PostgreSQL variables: `PG_USER`, `PG_PASSWORD`, `PG_HOST`, `PG_PORT`, `PG_DATABASE`
  - SQL Server variables: `MSSQL_USER`, `MSSQL_PASSWORD`, `MSSQL_SERVER`, `MSSQL_PORT`, `MSSQL_DATABASE`, `MSSQL_ENCRYPT`, `MSSQL_TRUST_CERT`
- **New dependencies**:
  - `pg`: PostgreSQL driver
  - `mssql`: SQL Server driver
  - `@types/pg`: TypeScript definitions for pg
  - `@types/mssql`: TypeScript definitions for mssql
- **Documentation**:
  - `MIGRATION.md`: Guide for upgrading from 0.2.x to 0.3.0
  - `EXAMPLES.md`: Comprehensive usage examples for all three databases
  - Updated `README.md` with multi-database configuration
  - Updated `.env.example` with configurations for all databases

### Changed

- **Package name**: Changed from `oracle-mcp-server` to `multi-db-mcp-server`
- **Server name**: Changed from `ecl-mcp-server` to `multi-db-mcp-server`
- **Binary name**: Changed from `oracle-mcp` to `multi-db-mcp`
- **Version**: Bumped to 0.3.0 to reflect major feature addition
- **Tool parameter names**: Renamed `owner` to `schema` for consistency (backward compatible in Oracle mode)
- **Build configuration**: Updated esbuild to externalize all database drivers (oracledb, pg, mssql)
- **Connection handling**: Refactored to use adapter pattern instead of direct Oracle calls

### Fixed

- Connection cleanup now properly calls adapter's disconnect method
- Error handling is more consistent across different database types
- Row limiting is handled uniformly through the adapter interface

### Technical Details

**Architecture Changes:**
- Introduced `DatabaseAdapter` abstract class with methods:
  - `connect()`: Establish database connection
  - `disconnect()`: Close database connection
  - `executeQuery()`: Execute SQL query
  - `getTableInfo()`: Retrieve table metadata
  - `listTables()`: List available tables
  - `getRowCount()`: Count rows in a table
  - `testConnection()`: Verify database connectivity

**Database-Specific Implementations:**
- Each adapter handles database-specific connection configuration
- Metadata queries adapted to use appropriate system catalogs:
  - Oracle: `ALL_TAB_COLUMNS`, `ALL_TABLES`
  - PostgreSQL: `information_schema.columns`, `information_schema.tables`
  - SQL Server: `INFORMATION_SCHEMA.COLUMNS`, `INFORMATION_SCHEMA.TABLES`

**Bundle Size:**
- Increased from 245.5kb to 249.6kb (4.1kb increase, +1.7%)
- All database drivers remain externalized to avoid bundling native modules

## [0.2.0] - Previous Version

### Added
- Tool: `get-table-info` - Retrieve table metadata
- Tool: `list-tables` - List all accessible tables
- Tool: `get-row-count` - Count rows in a table
- esbuild bundler for minified distribution
- Proper error handling and validation

### Changed
- Improved connection management
- Enhanced security with query validation

## [0.1.0] - Initial Release

### Added
- Initial implementation of Oracle MCP Server
- Tool: `run-query` - Execute read-only SELECT queries
- Basic Oracle database connectivity
- Environment variable configuration
- TypeScript support
