# Custom MCP Application

A Spring Boot application that uses the `db-mcp` library as a dependency and adds custom MCP tools for enhanced database operations.

## Project Structure

```
custom-mcp-app/
├── src/main/java/com/example/custommcp/
│   ├── CustomMcpApplication.java       # Main application class
│   ├── controller/
│   │   └── CustomToolsController.java  # REST endpoints for custom tools
│   └── service/
│       └── CustomToolsService.java     # Custom MCP tools implementation
├── src/main/resources/
│   └── application.properties          # Configuration
└── pom.xml                             # Maven dependencies
```

## Prerequisites

1. **Java 17** or higher
2. **Maven 3.6+**
3. **Database** (PostgreSQL, Oracle, SQL Server, or MySQL)
4. **AI Provider** (optional):
   - OpenAI API key, OR
   - Ollama running locally

## Installation

### 1. Install db-mcp Library

First, install the `db-mcp` library to your local Maven repository:

```bash
cd ../spring-boot-server
mvn clean install
```

### 2. Configure Database

Edit `src/main/resources/application.properties` and configure your database:

```properties
# Choose database type
app.db.type=postgres

# PostgreSQL example
app.db.postgres.host=localhost
app.db.postgres.port=5432
app.db.postgres.database=yourdb
app.db.postgres.user=youruser
app.db.postgres.password=yourpassword
```

### 3. Configure AI Provider (Optional)

For AI chat functionality, configure either OpenAI or Ollama:

```properties
# Use Ollama (local)
ai.provider=ollama
ollama.base.url=http://localhost:11434
ollama.model=llama3.2

# OR use OpenAI
# ai.provider=openai
# openai.api.key=your-api-key-here
# openai.model=gpt-4o
```

## Build and Run

```bash
# Build the project
mvn clean package

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Custom Tools Available

### 1. Database Summary
Get comprehensive database statistics:

```bash
curl http://localhost:8080/api/custom/summary
```

Response:
```json
{
  "success": true,
  "totalTables": 275,
  "totalRows": 123456,
  "timestamp": "2025-12-05T10:30:00"
}
```

### 2. Find Large Tables
Find tables with row count above threshold:

```bash
curl "http://localhost:8080/api/custom/large-tables?minRows=1000"
```

### 3. Search Tables by Pattern
Search tables by name pattern:

```bash
curl "http://localhost:8080/api/custom/search-tables?pattern=GT_%"
```

### 4. Table Statistics
Get detailed statistics for a specific table:

```bash
curl http://localhost:8080/api/custom/table-stats/GT_OBRAS
```

Response:
```json
{
  "success": true,
  "tableName": "GT_OBRAS",
  "columnCount": 38,
  "rowCount": 3,
  "primaryKeyColumns": ["COD_OBRA"],
  "columns": [...]
}
```

### 5. Compare Tables
Compare row counts between two tables:

```bash
curl "http://localhost:8080/api/custom/compare-tables?table1=GT_OBRAS&table2=GT_TRABAJOS"
```

### 6. AI Chat
Query the database using natural language:

```bash
curl -X POST http://localhost:8080/api/custom/ai-chat \
  -H "Content-Type: application/json" \
  -d '{"message": "How many obras exist?"}'
```

Response:
```json
{
  "success": true,
  "response": "There are 3 obras in the GT_OBRAS table.",
  "threadId": "uuid-here"
}
```

## Base Library Tools (Inherited from db-mcp)

All tools from the `db-mcp` library are also available:

### Database Operations
- `POST /api/database/list-tables` - List all tables
- `POST /api/database/get-table-info` - Get table metadata
- `POST /api/database/run-query` - Execute SQL query
- `POST /api/database/get-row-count` - Get table row count
- `POST /api/database/list-schemas` - List database schemas
- `POST /api/database/list-views` - List database views

### AI Chat
- `POST /api/chat` - Start new AI conversation
- `POST /api/chat/{threadId}` - Continue conversation
- `GET /api/chat/{threadId}/history` - Get conversation history
- `DELETE /api/chat/{threadId}` - Clear conversation

### MCP Protocol
- `GET /mcp/sse` - MCP Server-Sent Events endpoint

## Adding More Custom Tools

To add more custom tools:

1. Add methods to `CustomToolsService.java`:
```java
public Map<String, Object> myCustomTool(String param) {
    // Implementation
    return result;
}
```

2. Expose via REST in `CustomToolsController.java`:
```java
@GetMapping("/my-custom-tool")
public ResponseEntity<Map<String, Object>> myCustomTool(
        @RequestParam String param) {
    return ResponseEntity.ok(customToolsService.myCustomTool(param));
}
```

## Example Usage

```bash
# Check health
curl http://localhost:8080/api/custom/health

# Get database summary
curl http://localhost:8080/api/custom/summary

# Find tables with more than 100 rows
curl "http://localhost:8080/api/custom/large-tables?minRows=100"

# Search for tables starting with "GT_"
curl "http://localhost:8080/api/custom/search-tables?pattern=GT_%"

# Get statistics for GT_OBRAS table
curl http://localhost:8080/api/custom/table-stats/GT_OBRAS

# Compare two tables
curl "http://localhost:8080/api/custom/compare-tables?table1=GT_OBRAS&table2=GT_TRABAJOS"

# Ask AI about the database
curl -X POST http://localhost:8080/api/custom/ai-chat \
  -H "Content-Type: application/json" \
  -d '{"message": "List all tables starting with GT_"}'
```

## Troubleshooting

### Database Connection Issues
- Verify database is running and accessible
- Check credentials in `application.properties`
- Ensure database driver is in the classpath (see `pom.xml`)

### AI Chat Not Working
- For Ollama: Ensure Ollama is running (`ollama serve`)
- For OpenAI: Verify API key is valid
- Check logs for detailed error messages

### Library Not Found
- Run `mvn clean install` in the `spring-boot-server` directory first
- Verify the library version matches in your `pom.xml`

## Development

```bash
# Run in development mode with auto-reload
mvn spring-boot:run

# Build without tests
mvn clean package -DskipTests

# Run tests
mvn test
```

## Production Deployment

1. Build the JAR:
```bash
mvn clean package
```

2. Run the JAR:
```bash
java -jar target/custom-mcp-app-0.0.1-SNAPSHOT.jar
```

3. Or use Docker (create Dockerfile):
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/custom-mcp-app-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

## License

Same as parent db-mcp library
