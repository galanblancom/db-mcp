# Oracle MCP Server Usage Examples

## Basic Queries

### 1. Simple SELECT
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "SELECT 'Hello World' as message FROM DUAL"
  }
}
```

### 2. Current Date/Time
```json
{
  "name": "run_query", 
  "arguments": {
    "sql": "SELECT SYSDATE as current_date, USER as current_user FROM DUAL"
  }
}
```

### 3. Table Information
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "SELECT table_name, num_rows FROM user_tables ORDER BY table_name"
  }
}
```

### 4. Query with Parameters
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "SELECT * FROM employees WHERE department_id = :dept_id AND salary > :min_salary",
    "binds": {
      "dept_id": 10,
      "min_salary": 5000
    }
  }
}
```

### 5. Complex Query with JOIN
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "SELECT e.employee_id, e.first_name, e.last_name, d.department_name FROM employees e JOIN departments d ON e.department_id = d.department_id WHERE e.salary > 10000"
  }
}
```

### 6. Aggregate Query
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "SELECT department_id, COUNT(*) as employee_count, AVG(salary) as avg_salary FROM employees GROUP BY department_id ORDER BY department_id"
  }
}
```

### 7. CTE (Common Table Expression)
```json
{
  "name": "run_query",
  "arguments": {
    "sql": "WITH dept_stats AS (SELECT department_id, AVG(salary) as avg_sal FROM employees GROUP BY department_id) SELECT d.department_name, ds.avg_sal FROM departments d JOIN dept_stats ds ON d.department_id = ds.department_id"
  }
}
```

## Security Notes

- Only SELECT and WITH statements are allowed
- INSERT, UPDATE, DELETE, CREATE, ALTER, DROP statements are blocked
- Use bind parameters to prevent SQL injection
- Connection limits and query timeouts are enforced (max 1000 rows per query)