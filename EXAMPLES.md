# Usage Examples for Multi-Database MCP Server

This document provides practical examples for using the Multi-Database MCP Server with Oracle, PostgreSQL, and SQL Server.

## Oracle Database Examples

### Configuration

```bash
DB_TYPE=oracle
ORACLE_USER=hr
ORACLE_PASSWORD=hr
ORACLE_HOST=localhost
ORACLE_PORT=1521
ORACLE_SERVICE=XE
```

### Example Queries

**Query employees:**
```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "SELECT employee_id, first_name, last_name, salary FROM employees WHERE department_id = 10",
    "maxRows": 100
  }
}
```

**Get table information:**
```json
{
  "tool": "get-table-info",
  "parameters": {
    "tableName": "EMPLOYEES",
    "schema": "HR"
  }
}
```

**List all tables:**
```json
{
  "tool": "list-tables",
  "parameters": {
    "schema": "HR",
    "pattern": "%EMP%"
  }
}
```

**Count rows with filter:**
```json
{
  "tool": "get-row-count",
  "parameters": {
    "tableName": "EMPLOYEES",
    "schema": "HR",
    "whereClause": "salary > 5000"
  }
}
```

**List all schemas:**
```json
{
  "tool": "list-schemas",
  "parameters": {}
}
```

**List views in a schema:**
```json
{
  "tool": "list-views",
  "parameters": {
    "schema": "HR",
    "pattern": "%EMP%"
  }
}
```

**Get view definition:**
```json
{
  "tool": "get-view-definition",
  "parameters": {
    "viewName": "EMPLOYEE_DETAILS",
    "schema": "HR"
  }
}
```

---

## PostgreSQL Examples

### Configuration

```bash
DB_TYPE=postgres
PG_USER=postgres
PG_PASSWORD=mypassword
PG_HOST=localhost
PG_PORT=5432
PG_DATABASE=company
```

### Example Queries

**Query employees:**
```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "SELECT employee_id, first_name, last_name, salary FROM employees WHERE department_id = 10 ORDER BY salary DESC",
    "maxRows": 100
  }
}
```

**Get table information:**
```json
{
  "tool": "get-table-info",
  "parameters": {
    "tableName": "employees",
    "schema": "public"
  }
}
```

**List all tables:**
```json
{
  "tool": "list-tables",
  "parameters": {
    "schema": "public",
    "pattern": "%emp%"
  }
}
```

**Count rows with filter:**
```json
{
  "tool": "get-row-count",
  "parameters": {
    "tableName": "employees",
    "schema": "public",
    "whereClause": "salary > 5000 AND hire_date > '2020-01-01'"
  }
}
```

**Advanced PostgreSQL-specific query:**
```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "SELECT department_id, COUNT(*) as employee_count, AVG(salary) as avg_salary FROM employees GROUP BY department_id HAVING COUNT(*) > 5",
    "maxRows": 50
  }
}
```

**List all schemas:**
```json
{
  "tool": "list-schemas",
  "parameters": {}
}
```

**List views in a schema:**
```json
{
  "tool": "list-views",
  "parameters": {
    "schema": "public",
    "pattern": "%emp%"
  }
}
```

**Get view definition:**
```json
{
  "tool": "get-view-definition",
  "parameters": {
    "viewName": "employee_summary",
    "schema": "public"
  }
}
```

---

## SQL Server Examples

### Configuration

```bash
DB_TYPE=sqlserver
MSSQL_USER=sa
MSSQL_PASSWORD=YourStrong!Password
MSSQL_SERVER=localhost
MSSQL_PORT=1433
MSSQL_DATABASE=CompanyDB
MSSQL_ENCRYPT=true
MSSQL_TRUST_CERT=true
```

### Example Queries

**Query employees:**
```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "SELECT TOP 100 EmployeeID, FirstName, LastName, Salary FROM Employees WHERE DepartmentID = 10 ORDER BY Salary DESC",
    "maxRows": 100
  }
}
```

**Get table information:**
```json
{
  "tool": "get-table-info",
  "parameters": {
    "tableName": "Employees",
    "schema": "dbo"
  }
}
```

**List all tables:**
```json
{
  "tool": "list-tables",
  "parameters": {
    "schema": "dbo",
    "pattern": "%Emp%"
  }
}
```

**Count rows with filter:**
```json
{
  "tool": "get-row-count",
  "parameters": {
    "tableName": "Employees",
    "schema": "dbo",
    "whereClause": "Salary > 5000 AND HireDate > '2020-01-01'"
  }
}
```

**Advanced SQL Server-specific query:**
```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "SELECT d.DepartmentName, COUNT(e.EmployeeID) as EmployeeCount, AVG(e.Salary) as AvgSalary FROM Employees e INNER JOIN Departments d ON e.DepartmentID = d.DepartmentID GROUP BY d.DepartmentName HAVING COUNT(e.EmployeeID) > 5",
    "maxRows": 50
  }
}
```

**List all schemas:**
```json
{
  "tool": "list-schemas",
  "parameters": {}
}
```

**List views in a schema:**
```json
{
  "tool": "list-views",
  "parameters": {
    "schema": "dbo",
    "pattern": "%Emp%"
  }
}
```

**Get view definition:**
```json
{
  "tool": "get-view-definition",
  "parameters": {
    "viewName": "EmployeeSummary",
    "schema": "dbo"
  }
}
```

---

## Cross-Database Compatibility Tips

### Case Sensitivity

- **Oracle**: Identifiers are case-insensitive by default (converted to uppercase)
- **PostgreSQL**: Identifiers are case-insensitive (converted to lowercase) unless quoted
- **SQL Server**: Case sensitivity depends on collation, typically case-insensitive

### Schema Defaults

- **Oracle**: Uses the username as the default schema
- **PostgreSQL**: Uses `public` as the default schema
- **SQL Server**: Uses `dbo` as the default schema

### LIMIT vs TOP

- **Oracle**: Use `WHERE ROWNUM <= n` or `FETCH FIRST n ROWS ONLY`
- **PostgreSQL**: Use `LIMIT n`
- **SQL Server**: Use `TOP n`

**Note:** The MCP server automatically handles row limiting via the `maxRows` parameter, so you don't need to use database-specific syntax for basic queries.

### Date Formats

- **Oracle**: `TO_DATE('2020-01-01', 'YYYY-MM-DD')`
- **PostgreSQL**: `'2020-01-01'::DATE` or `CAST('2020-01-01' AS DATE)`
- **SQL Server**: `CAST('2020-01-01' AS DATE)` or `'2020-01-01'`

### String Concatenation

- **Oracle**: `'Hello' || ' ' || 'World'` or `CONCAT('Hello', ' ', 'World')`
- **PostgreSQL**: `'Hello' || ' ' || 'World'` or `CONCAT('Hello', ' ', 'World')`
- **SQL Server**: `'Hello' + ' ' + 'World'` or `CONCAT('Hello', ' ', 'World')`

---

## Common Use Cases

### Discovering Database Structure

```json
{
  "tool": "list-tables",
  "parameters": {}
}
```

### Analyzing Table Size

```json
{
  "tool": "get-table-info",
  "parameters": {
    "tableName": "your_large_table"
  }
}
```

### Quick Data Validation

```json
{
  "tool": "get-row-count",
  "parameters": {
    "tableName": "transactions",
    "whereClause": "status = 'pending' AND created_date > CURRENT_DATE - 7"
  }
}
```

### Complex Reporting Query

```json
{
  "tool": "run-query",
  "parameters": {
    "sql": "WITH monthly_sales AS (SELECT date_part('month', order_date) as month, SUM(total) as total_sales FROM orders WHERE order_date >= '2024-01-01' GROUP BY date_part('month', order_date)) SELECT month, total_sales, LAG(total_sales) OVER (ORDER BY month) as prev_month FROM monthly_sales",
    "maxRows": 12
  }
}
```

---

## Error Handling

All tools return a consistent JSON response format:

**Success:**
```json
{
  "success": true,
  "rowCount": 10,
  "columns": ["id", "name"],
  "rows": [...],
  "metadata": {
    "executionTimeMs": 123
  }
}
```

**Error:**
```json
{
  "success": false,
  "error": "Table not found"
}
```

---

## Best Practices

1. **Always use parameterized queries when possible** (bind variables in Oracle, parameters in PostgreSQL/SQL Server)
2. **Set appropriate maxRows limits** to avoid overwhelming the system
3. **Use schema/owner parameter** explicitly for clarity
4. **Test queries in a development environment** first
5. **Be aware of database-specific syntax** when writing complex queries
6. **Use WHERE clauses in get-row-count** for filtered counts instead of querying all rows

---

## Security Notes

- Only SELECT and WITH queries are allowed
- INSERT, UPDATE, DELETE, and DDL statements are blocked
- WHERE clauses in row-count operations are validated for dangerous keywords
- All database connections use environment-based authentication
- Connection credentials should never be hardcoded in queries
