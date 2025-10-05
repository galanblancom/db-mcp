# Oracle MCP Server - Quick Reference

## 🚀 Available Tools

### 1. get-row-count (NEW!)
Quickly count rows in a table without retrieving data.

**Parameters:**
- `tableName` (required): Name of the table
- `owner` (optional): Schema owner (default: ECL)
- `whereClause` (optional): Filter condition (e.g., 'voltage_level > 20')

**Example:**
```typescript
{
  "tableName": "ecl_point"
}
```

**With filter:**
```typescript
{
  "tableName": "ecl_point",
  "whereClause": "voltage_level > 20 AND point_type = 1"
}
```

**Response:**
```json
{
  "success": true,
  "tableName": "ECL_POINT",
  "owner": "ECL",
  "rowCount": 1000,
  "whereClause": null,
  "metadata": {
    "executionTimeMs": 15,
    "query": "SELECT COUNT(*) as ROW_COUNT FROM ECL.ECL_POINT"
  }
}
```

---

### 2. run-query
Execute SELECT or WITH queries against the Oracle database.

**Parameters:**
- `sql` (required): The SQL query to execute
- `maxRows` (optional): Maximum rows to return (default: 1000, max: 10000)

**Example:**
```typescript
{
  "sql": "SELECT * FROM ecl_point WHERE voltage_level > 20",
  "maxRows": 500
}
```

**Response:**
```json
{
  "success": true,
  "rowCount": 450,
  "columns": ["POINT_ID", "POINT_NAME", "VOLTAGE_LEVEL"],
  "rows": [...],
  "metadata": {
    "executionTimeMs": 125,
    "maxRowsLimit": 500,
    "truncated": false
  }
}
```

---

### 2. get-table-info
Get detailed information about a specific table.

**Parameters:**
- `tableName` (required): Name of the table (uppercase recommended)
- `owner` (optional): Schema owner (default: ECL)

**Example:**
```typescript
{
  "tableName": "ECL_POINT",
  "owner": "ECL"
}
```

**Response:**
```json
{
  "success": true,
  "tableName": "ECL_POINT",
  "owner": "ECL",
  "rowCount": 12543,
  "columns": [
    {
      "COLUMN_NAME": "POINT_ID",
      "DATA_TYPE": "NUMBER",
      "DATA_LENGTH": 22,
      "NULLABLE": "N",
      "DATA_DEFAULT": null
    }
  ]
}
```

---

### 3. list-tables
List all accessible tables with optional filtering.

**Parameters:**
- `owner` (optional): Filter by schema owner (default: ECL)
- `pattern` (optional): Filter by name pattern (use % as wildcard)

**Example:**
```typescript
{
  "owner": "ECL",
  "pattern": "ECL_%"
}
```

**Response:**
```json
{
  "success": true,
  "owner": "ECL",
  "tableCount": 15,
  "tables": [
    {
      "TABLE_NAME": "ECL_POINT",
      "NUM_ROWS": 12543,
      "LAST_ANALYZED": "2025-09-15T10:30:00.000Z"
    }
  ]
}
```

---

## 🔒 Security Rules

### Allowed Queries
✅ SELECT statements
✅ WITH (Common Table Expressions)

### Blocked Operations
❌ DROP, DELETE, INSERT, UPDATE
❌ ALTER, CREATE, TRUNCATE
❌ GRANT, REVOKE

### Row Limits
- Default: 1,000 rows
- Maximum: 10,000 rows

---

## 💡 Common Use Cases

### Count all records (FAST)
Use `get-row-count` tool:
```typescript
{ "tableName": "ecl_point" }
```

### Count with filter (FAST)
Use `get-row-count` tool:
```typescript
{ 
  "tableName": "ecl_point",
  "whereClause": "voltage_level > 20"
}
```

### Get table summary
```sql
SELECT * FROM ecl_point WHERE ROWNUM <= 10
```

### Count records (alternative with run-query)
```sql
SELECT COUNT(*) as total FROM ecl_point
```

### Get distinct values
```sql
SELECT DISTINCT voltage_level FROM ecl_point ORDER BY voltage_level
```

### Filter by date
```sql
SELECT * FROM ecl_point 
WHERE installation_date >= TO_DATE('2020-01-01', 'YYYY-MM-DD')
```

### Aggregate data
```sql
SELECT voltage_level, COUNT(*) as count 
FROM ecl_point 
GROUP BY voltage_level 
ORDER BY voltage_level
```

### Join tables
```sql
SELECT p.point_name, p.voltage_level 
FROM ecl_point p
WHERE p.point_type = 1
```

### Use CTE (WITH clause)
```sql
WITH high_voltage AS (
  SELECT * FROM ecl_point WHERE voltage_level > 20
)
SELECT voltage_level, COUNT(*) 
FROM high_voltage 
GROUP BY voltage_level
```

---

## 🛠️ Troubleshooting

### Error: "Query contains disallowed SQL operations"
**Cause:** Query contains blocked keywords (DROP, DELETE, etc.)
**Solution:** Use only SELECT or WITH statements

### Error: "Failed to connect to Oracle database"
**Cause:** Connection parameters incorrect or database unavailable
**Solution:** Verify environment variables and database status

### Warning: "truncated: true"
**Cause:** Query returned more rows than the limit
**Solution:** Increase `maxRows` parameter or add WHERE clause

### Slow queries
**Check:** `executionTimeMs` in response metadata
**Solution:** Add indexes, optimize WHERE clauses, or limit results

---

## 📝 Tips & Best Practices

1. **Use UPPERCASE** for table names in Oracle
2. **Add WHERE clauses** to limit results
3. **Use ROWNUM** for quick testing: `WHERE ROWNUM <= 10`
4. **Check metadata** for performance insights
5. **Start with table info** before querying unknown tables
6. **Use patterns** when listing tables: `ECL_%`
7. **Monitor execution time** to optimize queries
8. **Test with small limits** first: `maxRows: 10`

---

## 🔧 Configuration

### Environment Variables
```bash
ORACLE_USER=ECL
ORACLE_PASSWORD=ECL
ORACLE_HOST=10.240.19.26
ORACLE_PORT=1539
ORACLE_SERVICE=ECL
```

### VS Code MCP Settings
```json
{
  "mcp.servers": {
    "oracle-mcp": {
      "command": "node",
      "args": ["d:\\Development\\Unversioned\\ecl-mcp\\dist\\ecl-mcp.js"],
      "env": {
        "ORACLE_USER": "ECL",
        "ORACLE_PASSWORD": "ECL",
        "ORACLE_HOST": "10.240.19.26",
        "ORACLE_PORT": "1539",
        "ORACLE_SERVICE": "ECL"
      }
    }
  }
}
```

---

## 📊 Response Format

All tools return consistent format:

**Success:**
```json
{
  "success": true,
  ...data fields...
}
```

**Error:**
```json
{
  "success": false,
  "error": "Detailed error message"
}
```

---

**Version:** 0.2.0
**Documentation:** See IMPROVEMENTS.md for detailed changes
