# Migration Guide - Multi-Database Support

## Overview

Version 0.3.0 introduces support for multiple database types: **Oracle**, **PostgreSQL**, and **SQL Server**. The MCP server now uses a database adapter pattern to provide a unified interface across different database systems.

## Breaking Changes

### Environment Variables

The environment variable structure remains backward compatible for Oracle databases. If you don't specify `DB_TYPE`, it defaults to `oracle`.

**Before (still works):**
```bash
ORACLE_USER=myuser
ORACLE_PASSWORD=mypass
ORACLE_HOST=localhost
ORACLE_PORT=1521
ORACLE_SERVICE=XE
```

**After (explicit, recommended):**
```bash
DB_TYPE=oracle
ORACLE_USER=myuser
ORACLE_PASSWORD=mypass
ORACLE_HOST=localhost
ORACLE_PORT=1521
ORACLE_SERVICE=XE
```

### Tool Parameters

Some tool parameters have been renamed for consistency across database types:

| Tool | Old Parameter | New Parameter | Notes |
|------|--------------|---------------|-------|
| `get-table-info` | `owner` | `schema` | More generic term |
| `list-tables` | `owner` | `schema` | More generic term |
| `get-row-count` | `owner` | `schema` | More generic term |

The old parameter names are still accepted in Oracle mode for backward compatibility.

## New Features

### PostgreSQL Support

To connect to PostgreSQL:

```bash
DB_TYPE=postgres
PG_USER=postgres
PG_PASSWORD=mypassword
PG_HOST=localhost
PG_PORT=5432
PG_DATABASE=mydb
```

### SQL Server Support

To connect to SQL Server:

```bash
DB_TYPE=sqlserver
MSSQL_USER=sa
MSSQL_PASSWORD=YourStrong!Password
MSSQL_SERVER=localhost
MSSQL_PORT=1433
MSSQL_DATABASE=master
MSSQL_ENCRYPT=true
MSSQL_TRUST_CERT=true
```

## Migration Steps

1. **Update dependencies:**
   ```bash
   npm install
   ```

2. **Update environment variables:**
   - Add `DB_TYPE` variable to your `.env` file (optional, defaults to `oracle`)
   - If switching to PostgreSQL or SQL Server, configure the appropriate variables

3. **Rebuild the project:**
   ```bash
   npm run build
   ```

4. **Test the connection:**
   ```bash
   npm start
   ```

## Database-Specific Behavior

### Schema/Owner Handling

- **Oracle**: Uses `owner` concept (e.g., `SCOTT.EMPLOYEES`)
- **PostgreSQL**: Uses `schema` concept, defaults to `public`
- **SQL Server**: Uses `schema` concept, defaults to `dbo`

### Table Metadata

Each database adapter queries the appropriate system catalogs:

- **Oracle**: `ALL_TAB_COLUMNS`, `ALL_TABLES`
- **PostgreSQL**: `information_schema.columns`, `information_schema.tables`
- **SQL Server**: `INFORMATION_SCHEMA.COLUMNS`, `INFORMATION_SCHEMA.TABLES`

### Query Syntax

Be aware of database-specific SQL syntax:

- **Oracle**: Uses `:bind` parameters, `ROWNUM`, `DUAL`, etc.
- **PostgreSQL**: Uses `$1` parameters, `LIMIT`, generate_series, etc.
- **SQL Server**: Uses `@param` parameters, `TOP`, system functions, etc.

## Rollback

If you need to rollback to the Oracle-only version:

```bash
git checkout v0.2.0
npm install
npm run build
```

## Support

For issues or questions, please open an issue on GitHub or contact the development team.
