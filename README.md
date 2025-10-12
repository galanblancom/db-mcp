# Multi-Database MCP Server v1.0.0# Multi-Database MCP Server v1.0.0# Multi-Database MCP Server



A comprehensive Model Context Protocol (MCP) server providing **read-only** access to multiple database systems with **22 powerful tools**, advanced features including connection pooling, caching, performance monitoring, streaming, transactions, and schema comparison.



## 🚀 **What's New in Version 1.0.0**A comprehensive Model Context Protocol (MCP) server providing **read-only** access to multiple database systems with advanced features including connection pooling, caching, performance monitoring, and 16 powerful tools.A Model Context Protocol (MCP) server that provides read-only access to multiple database systems: **Oracle**, **PostgreSQL**, and **SQL Server**.



### All 29 Requested Features Implemented!



**New Database Support (2)**## 🚀 **NEW in Version 1.0.0**## Features

- ✅ MySQL/MariaDB support

- ✅ SQLite support



**Advanced Query Features (5)**### Major Enhancements- **Multi-database support**: Connect to Oracle, PostgreSQL, or SQL Server

- ✅ Query result streaming for large datasets

- ✅ Multi-statement query execution- ✅ **Connection Pooling** - Persistent database connections for better performance- Secure read-only database queries (SELECT and WITH statements only)

- ✅ Read-only transaction support

- ✅ Query templates with parameter substitution  - ✅ **Query Caching** - TTL-based metadata caching (5-min default)- Unified interface across different database systems

- ✅ Schema comparison tool

- ✅ **Query Logging** - Track query history and performance metrics- Proper error handling and connection management

**Performance & Infrastructure (9)**

- ✅ Connection pooling- ✅ **Retry Logic** - Automatic retry with exponential backoff for transient failures- Environment variable based configuration

- ✅ Metadata caching (5-min TTL)

- ✅ Query logging & performance tracking- ✅ **Better Error Messages** - Database-specific error mapping- TypeScript support with proper type definitions

- ✅ Retry logic with exponential backoff

- ✅ Enhanced error messages- ✅ **Query Timeouts** - Configurable timeouts (30s default)- Database-specific metadata queries using information_schema and system catalogs

- ✅ Query timeouts

- ✅ SQL injection protection- ✅ **SQL Injection Protection** - Enhanced WHERE clause validation

- ✅ Dry-run mode

- ✅ Smart column filtering## Installation



**New Tools (13)**### New Tools (9 additional tools)

1. get-indexes

2. get-foreign-keys1. **get-indexes** - Retrieve index informationInstall dependencies:

3. list-stored-procedures

4. get-table-statistics2. **get-foreign-keys** - Discover table relationships

5. explain-query

6. sample-table-data3. **list-stored-procedures** - List procedures and functions```bash

7. test-connection

8. get-performance-metrics4. **get-table-statistics** - Advanced table metadata (size, fragmentation)npm install

9. health-check

10. stream-query ⭐5. **explain-query** - Analyze query execution plans```

11. run-multi-query ⭐

12. run-transaction ⭐6. **sample-table-data** - Quick data preview without SQL

13. list-query-templates ⭐

14. execute-template ⭐7. **test-connection** - Explicit connection testing## Configuration

15. compare-schemas ⭐

8. **get-performance-metrics** - Query statistics and cache status

⭐ = Brand new in this update!

9. **health-check** - Comprehensive server diagnosticsThe server supports three database types: **Oracle**, **PostgreSQL**, and **SQL Server**. Configure using environment variables.

## Features



- **5 Database Systems**: Oracle, PostgreSQL, SQL Server, MySQL/MariaDB, SQLite

- **22 Comprehensive Tools**: Complete database exploration and analysis### Enhanced Existing Tools### Oracle Configuration

- **Secure Read-Only**: SELECT and WITH statements only

- **Connection Pooling**: Persistent connections for all databases- **run-query**: Added pagination, dry-run mode, CSV/table formats, large column filtering

- **Query Streaming**: Handle massive datasets without memory issues

- **Transactions**: Multi-query consistency with read-only transactions- **get-table-info**: Added primary key and foreign key detection, caching```bash

- **Query Templates**: 10 pre-defined templates for common operations

- **Schema Comparison**: Compare table structures across schemas- **list-tables**: Added caching supportDB_TYPE=oracle

- **Metadata Caching**: TTL-based caching (5-min default)

- **Performance Monitoring**: Query stats, execution times, cache metrics- **get-row-count**: Enhanced WHERE clause securityORACLE_USER=your_username

- **Automatic Retries**: Exponential backoff for transient failures

- **Enhanced Security**: SQL injection protection, WHERE clause validationORACLE_PASSWORD=your_password

- **Multiple Output Formats**: JSON, CSV, table

- **TypeScript**: Full type definitions## FeaturesORACLE_HOST=your_host



## Quick StartORACLE_PORT=1521



### Installation- **Multi-database support**: Oracle, PostgreSQL, and SQL ServerORACLE_SERVICE=your_service_name

```bash

npm install- **16 comprehensive tools** for database exploration and analysis# Optional: For Oracle 11g support (Thick mode)

```

- **Secure read-only** operations (SELECT and WITH statements only)ORACLE_CLIENT_LIB_DIR=C:\oracle\instantclient_19_11_64bits

### Configuration

- **Connection pooling** for optimal performance```

Create a `.env` file:

- **Metadata caching** with configurable TTL

#### Oracle

```bash- **Query performance tracking** and metrics### PostgreSQL Configuration

DB_TYPE=oracle

ORACLE_USER=username- **Automatic retry logic** for connection failures

ORACLE_PASSWORD=password

ORACLE_HOST=localhost- **Comprehensive error handling** with database-specific messages```bash

ORACLE_PORT=1521

ORACLE_SERVICE=XE- **TypeScript** support with proper type definitionsDB_TYPE=postgres

```

PG_USER=your_username

#### PostgreSQL

```bash## Quick StartPG_PASSWORD=your_password

DB_TYPE=postgres

PG_USER=usernamePG_HOST=your_host

PG_PASSWORD=password

PG_HOST=localhost### InstallationPG_PORT=5432

PG_PORT=5432

PG_DATABASE=mydb```bashPG_DATABASE=your_database

```

npm install```

#### SQL Server

```bash```

DB_TYPE=sqlserver

MSSQL_USER=username### SQL Server Configuration

MSSQL_PASSWORD=password

MSSQL_SERVER=localhost### Configuration

MSSQL_PORT=1433

MSSQL_DATABASE=mydbCreate a `.env` file with your database credentials:```bash

```

DB_TYPE=sqlserver

#### MySQL/MariaDB

```bash```bashMSSQL_USER=your_username

DB_TYPE=mysql

MYSQL_USER=username# Database TypeMSSQL_PASSWORD=your_password

MYSQL_PASSWORD=password

MYSQL_HOST=localhostDB_TYPE=oracle  # or postgres, sqlserverMSSQL_SERVER=your_server

MYSQL_PORT=3306

MYSQL_DATABASE=mydbMSSQL_PORT=1433

```

# OracleMSSQL_DATABASE=your_database

#### SQLite

```bashORACLE_USER=myuser# Optional: Connection encryption settings

DB_TYPE=sqlite

SQLITE_PATH=/path/to/database.dbORACLE_PASSWORD=mypassMSSQL_ENCRYPT=true

```

ORACLE_HOST=localhostMSSQL_TRUST_CERT=true

#### Optional Settings

```bashORACLE_PORT=1521```

QUERY_TIMEOUT_MS=30000

LOG_QUERIES=trueORACLE_SERVICE=XE

ENABLE_CACHE=true

CACHE_TTL_MS=300000## Usage

```

# Optional Performance Settings

### Run

```bashQUERY_TIMEOUT_MS=30000### Development

npm run dev  # Development

npm run build && npm start  # ProductionLOG_QUERIES=true

```

ENABLE_CACHE=trueRun in development mode with hot reload:

## All 22 Tools

CACHE_TTL_MS=300000

### Core Query Tools (4)

1. **run-query** - Execute SELECT/WITH with pagination, formats, dry-run``````bash

2. **get-table-info** - Table metadata with PK/FK detection

3. **list-tables** - All tables with row countsnpm run dev

4. **get-row-count** - Fast row counting with WHERE support

### Run```

### Schema Discovery (3)

5. **list-schemas** - All schemas with counts```bash

6. **list-views** - All views with filtering

7. **get-view-definition** - View SQL definitionsnpm run dev  # Development### Production



### Advanced Metadata (4)npm run build && npm start  # Production

8. **get-indexes** - Index information

9. **get-foreign-keys** - FK relationships```Build and run:

10. **list-stored-procedures** - Procedures/functions

11. **get-table-statistics** - Table/index sizes, fragmentation



### Query Analysis (2)## All 16 Tools```bash

12. **explain-query** - Execution plans

13. **sample-table-data** - Quick data previewnpm run build



### System Monitoring (3)### Core Query Toolsnpm start

14. **test-connection** - Connection testing

15. **get-performance-metrics** - Server metrics1. **run-query** - Execute SELECT/WITH queries with pagination, formats (CSV/table/JSON), dry-run```

16. **health-check** - Comprehensive diagnostics

2. **get-table-info** - Table metadata with columns, types, PK/FK detection

### Advanced Query Features (6)

17. **stream-query** ⭐ - Stream large results in batches3. **list-tables** - All tables with row counts and filtering### Direct execution

18. **run-multi-query** ⭐ - Execute multiple queries

19. **run-transaction** ⭐ - Read-only transactions4. **get-row-count** - Fast row counting with WHERE clause support

20. **list-query-templates** ⭐ - List available templates

21. **execute-template** ⭐ - Execute query templateYou can also run the TypeScript file directly:

22. **compare-schemas** ⭐ - Compare table structures

### Schema Discovery

## Usage Examples

5. **list-schemas** - All schemas with table/view counts```bash

### Stream Large Query Results

```json6. **list-views** - All views with filteringnpx tsx db-mcp.ts

{

  "tool": "stream-query",7. **get-view-definition** - View SQL definitions```

  "parameters": {

    "sql": "SELECT * FROM large_table",

    "batchSize": 100

  }### Advanced Metadata## Available Tools

}

```8. **get-indexes** ⭐ - Index information (columns, uniqueness, type)



### Execute Multiple Queries9. **get-foreign-keys** ⭐ - FK relationships and cascade rules### run-query

```json

{10. **list-stored-procedures** ⭐ - Procedures and functions

  "tool": "run-multi-query",

  "parameters": {11. **get-table-statistics** ⭐ - Table size, index size, fragmentationExecutes a read-only SELECT or WITH query against the database.

    "queries": [

      "SELECT COUNT(*) FROM customers",

      "SELECT COUNT(*) FROM orders",

      "SELECT COUNT(*) FROM products"### Query Analysis**Parameters:**

    ]

  }12. **explain-query** ⭐ - Execution plans and cost estimation- `sql` (required): The SQL query to execute (SELECT or WITH statements only)

}

```13. **sample-table-data** ⭐ - Quick data preview (top N or random)- `maxRows` (optional): Maximum number of rows to return (default: 1000, max: 10000)



### Run Transaction

```json

{### System Monitoring**Security Note:** Only SELECT and WITH statements are allowed for security reasons. INSERT, UPDATE, DELETE, and DDL statements will be rejected.

  "tool": "run-transaction",

  "parameters": {14. **test-connection** ⭐ - Connection testing with response time

    "queries": [

      "SELECT * FROM accounts WHERE id = 1",15. **get-performance-metrics** ⭐ - Query stats, cache stats, uptime### get-table-info

      "SELECT * FROM transactions WHERE account_id = 1"

    ]16. **health-check** ⭐ - Comprehensive server diagnostics

  }

}Retrieves detailed information about a table including columns, data types, and constraints.

```

⭐ = New in v1.0.0

### List Query Templates

```json**Parameters:**

{

  "tool": "list-query-templates"## Configuration Reference- `tableName` (required): The name of the table to inspect

}

```- `schema` (optional): The schema/owner (default: current user or default schema)



### Execute Template### Environment Variables

```json

{### list-tables

  "tool": "execute-template",

  "parameters": {| Variable | Description | Default | Example |

    "templateId": "top-rows",

    "parameters": {|----------|-------------|---------|---------|Lists all tables accessible by the current user with row counts.

      "table": "customers",

      "limit": "10"| `DB_TYPE` | Database type | `oracle` | `oracle`, `postgres`, `sqlserver` |

    }

  }| `QUERY_TIMEOUT_MS` | Query timeout | `30000` | `60000` |**Parameters:**

}

```| `LOG_QUERIES` | Enable logging | `false` | `true` |- `schema` (optional): Filter by schema/owner (default: current user or default schema)



### Compare Schemas| `ENABLE_CACHE` | Enable caching | `true` | `false` |- `pattern` (optional): Filter tables by name pattern (use % as wildcard)

```json

{| `CACHE_TTL_MS` | Cache lifetime | `300000` | `600000` |

  "tool": "compare-schemas",

  "parameters": {### get-row-count

    "table1": "users",

    "schema1": "dev",### Oracle Configuration

    "table2": "users",

    "schema2": "prod"```bashGets the total number of rows in a table quickly without retrieving data.

  }

}DB_TYPE=oracle

```

ORACLE_USER=username**Parameters:**

### Export to CSV

```jsonORACLE_PASSWORD=password- `tableName` (required): The name of the table to count rows from

{

  "tool": "run-query",ORACLE_HOST=host- `schema` (optional): The schema/owner (default: current user or default schema)

  "parameters": {

    "sql": "SELECT * FROM sales",ORACLE_PORT=1521- `whereClause` (optional): Optional WHERE clause to filter the count

    "format": "csv"

  }ORACLE_SERVICE=service_name

}

```ORACLE_CLIENT_LIB_DIR=/path/to/instant/client  # Optional for 11g### list-schemas



## Query Templates```



10 pre-defined templates for common operations:Lists all schemas/databases accessible in the current database instance.



1. **top-rows** - Get first N rows### PostgreSQL Configuration

2. **filter-equals** - Exact match filter

3. **filter-like** - Pattern matching```bash**Parameters:** None

4. **date-range** - Date range filter

5. **aggregate-count** - Count by groupDB_TYPE=postgres

6. **aggregate-sum** - Sum by group

7. **join-tables** - Inner joinPG_USER=username**Returns:** List of schemas with table and view counts

8. **distinct-values** - Unique values

9. **null-check** - NULL filteringPG_PASSWORD=password

10. **recent-records** - Most recent N records

PG_HOST=host### list-views

## Performance Features

PG_PORT=5432

### Connection Pooling

- Persistent connections (1-10 per adapter)PG_DATABASE=database_nameLists all views accessible by the current user.

- Automatic management

- Reduced query latency```



### Query Streaming**Parameters:**

- Process large datasets without memory issues

- Configurable batch sizes (default: 100)### SQL Server Configuration- `schema` (optional): Filter by schema/owner (default: current user or default schema)

- Async generator pattern

```bash- `pattern` (optional): Filter views by name pattern (use % as wildcard)

### Metadata Caching

- 5-minute default TTLDB_TYPE=sqlserver

- Caches: tables, schemas, views, table-info

- Automatic invalidationMSSQL_USER=username### get-view-definition



### Transaction SupportMSSQL_PASSWORD=password

- Read-only transactions

- Consistency across multiple queriesMSSQL_SERVER=serverRetrieves the SQL definition of a view.

- Automatic rollback on errors

MSSQL_PORT=1433

## Security

MSSQL_DATABASE=database_name**Parameters:**

### Read-Only Enforcement

- Only SELECT and WITH allowedMSSQL_ENCRYPT=true- `viewName` (required): The name of the view to inspect

- Blocks: DROP, DELETE, INSERT, UPDATE, ALTER, CREATE, etc.

MSSQL_TRUST_CERT=true- `schema` (optional): The schema/owner (default: current user or default schema)

### SQL Injection Protection

- WHERE clause validation```

- Pattern detection

- Keyword blocking## Environment Variables Reference



## Architecture## Usage Examples



### Database Adapters### Common Variables

- Abstract base class with 16 methods

- 5 concrete implementations (Oracle, PostgreSQL, SQL Server, MySQL, SQLite)### Execute Query with Pagination

- Factory pattern for adapter creation

- Connection pooling per adapter```json| Variable | Description | Required | Default |

- Streaming support

- Transaction support{|----------|-------------|----------|---------|



### Utilities  "tool": "run-query",| `DB_TYPE` | Database type: `oracle`, `postgres`, or `sqlserver` | No | `oracle` |

- QueryLogger (last 100 queries)

- MetadataCache (TTL-based)  "parameters": {

- QueryTemplates (10 pre-defined)

- Error mapping    "sql": "SELECT * FROM customers ORDER BY created_date DESC",### Oracle Variables

- Retry logic

- Schema comparison    "page": 2,



## Troubleshooting    "pageSize": 50| Variable | Description | Required | Example |



### Test Connection  }|----------|-------------|----------|---------|

```json

{"tool": "test-connection"}}| `ORACLE_USER` | Oracle database username | Yes | `myuser` |

```

```| `ORACLE_PASSWORD` | Oracle database password | Yes | `mypassword` |

### Check Health

```json| `ORACLE_HOST` | Oracle database host | Yes | `localhost` |

{"tool": "health-check"}

```### Export to CSV| `ORACLE_PORT` | Oracle database port | Yes | `1521` |



### View Performance```json| `ORACLE_SERVICE` | Oracle service name | Yes | `XE` |

```json

{"tool": "get-performance-metrics"}{| `ORACLE_CLIENT_LIB_DIR` | Path to Oracle Instant Client (for 11g support) | No | `C:\oracle\instantclient` |

```

  "tool": "run-query",

### Enable Logging

```bash  "parameters": {### PostgreSQL Variables

LOG_QUERIES=true

```    "sql": "SELECT * FROM sales WHERE date > '2025-01-01'",



## Migration from v0.3.0    "format": "csv"| Variable | Description | Required | Example |



All v0.3.0 tools work unchanged! New features:  }|----------|-------------|----------|---------|

- Add `DB_TYPE=mysql` or `DB_TYPE=sqlite` for new database support

- Use 6 new advanced tools (streaming, transactions, templates, comparison)}| `PG_USER` | PostgreSQL username | Yes | `postgres` |

- Enable `LOG_QUERIES=true` to monitor performance

- All 16 original tools enhanced with pooling and caching```| `PG_PASSWORD` | PostgreSQL password | Yes | `mypassword` |



## Changelog| `PG_HOST` | PostgreSQL host | Yes | `localhost` |



### v1.0.0 (2025-10-12)### Get Table Indexes| `PG_PORT` | PostgreSQL port | Yes | `5432` |

- ✨ **ALL 29 FEATURES IMPLEMENTED**

- 🗄️ MySQL/MariaDB support```json| `PG_DATABASE` | PostgreSQL database name | Yes | `mydb` |

- 🗄️ SQLite support

- 🌊 Query streaming for large datasets{

- 📦 Multi-statement query execution

- 🔄 Read-only transaction support  "tool": "get-indexes",### SQL Server Variables

- 📋 Query templates system (10 templates)

- 🔍 Schema comparison tool  "parameters": {

- ⚡ Connection pooling for all databases

- 🚀 Metadata caching with TTL    "tableName": "ORDERS",| Variable | Description | Required | Default | Example |

- 📊 Query logging & metrics

- 🔄 Retry logic with exponential backoff    "schema": "SALES"|----------|-------------|----------|---------|---------|

- 🛡️ Enhanced security & error messages

- 🎨 Multiple output formats  }| `MSSQL_USER` | SQL Server username | Yes | - | `sa` |

- 📄 Pagination support

- And much more!}| `MSSQL_PASSWORD` | SQL Server password | Yes | - | `MyPass123!` |



## License```| `MSSQL_SERVER` | SQL Server hostname | Yes | - | `localhost` |



MIT| `MSSQL_PORT` | SQL Server port | No | `1433` | `1433` |



## Support### Quick Data Sample| `MSSQL_DATABASE` | SQL Server database name | Yes | - | `master` |



Issues and questions: Open an issue on the repository```json| `MSSQL_ENCRYPT` | Enable connection encryption | No | `true` | `true` or `false` |


{| `MSSQL_TRUST_CERT` | Trust server certificate | No | `true` | `true` or `false` |

  "tool": "sample-table-data",

  "parameters": {## Error Handling

    "tableName": "PRODUCTS",

    "limit": 20,The server includes comprehensive error handling:

    "random": true- Connection validation on startup

  }- Proper cleanup of database connections

}- Detailed error messages for debugging

```- Graceful handling of invalid queries



### Analyze Query Performance## License

```json

{MIT
  "tool": "explain-query",
  "parameters": {
    "sql": "SELECT o.* FROM orders o JOIN customers c ON o.customer_id = c.id WHERE c.region = 'US'"
  }
}
```

### Check Server Health
```json
{
  "tool": "health-check",
  "parameters": {}
}
```

## Performance Features

### Connection Pooling
- Persistent connections (1-10 pool size)
- Automatic management
- Reduced query latency

### Metadata Caching
- 5-minute default TTL
- Caches: tables, schemas, views, table-info
- Automatic invalidation

### Query Logging
- Last 100 queries tracked
- Execution time recording
- Success/failure tracking

### Retry Logic
- 3 retries with exponential backoff
- Handles transient connection errors

## Security

### Read-Only Enforcement
- Only SELECT and WITH allowed
- Blocks: DROP, DELETE, INSERT, UPDATE, ALTER, CREATE, etc.

### SQL Injection Protection
- WHERE clause validation
- Pattern detection
- Keyword blocking

## Troubleshooting

### Test Connection
```json
{"tool": "test-connection"}
```

### Check Performance
```json
{"tool": "get-performance-metrics"}
```

### View Logs
Enable with: `LOG_QUERIES=true`

## Migration from v0.3.0

All v0.3.0 tools work unchanged! New features:
- Add `LOG_QUERIES=true` to monitor queries
- Try 9 new tools for enhanced functionality
- Benefit from automatic connection pooling and caching

## Changelog

### v1.0.0 (2025-10-12)
- ✨ 9 new tools (16 total)
- ⚡ Connection pooling
- 🚀 Metadata caching
- 📊 Query logging & metrics
- 🔄 Retry logic
- 🛡️ Enhanced security
- 🎨 Multiple output formats
- 📄 Pagination support
- And much more!

## License

MIT

## Support

Issues and questions: Open an issue on the repository
