# DB-MCP Library Usage

This project can be used as a library dependency in other Spring Boot projects to add database MCP (Model Context Protocol) capabilities and AI-powered database chat functionality.

## Installation

### 1. Install to Local Maven Repository

First, build and install this library to your local Maven repository:

```bash
mvn clean install
```

### 2. Add Dependency to Your Project

Add the following dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.indra-rd</groupId>
    <artifactId>db-mcp</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 3. Add Required Database Driver

Since database drivers are optional in this library, add the specific driver(s) you need:

```xml
<!-- For PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- For Oracle -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
</dependency>

<!-- For SQL Server -->
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
</dependency>

<!-- For MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

## Configuration

### 1. Enable Component Scanning

In your main Spring Boot application class, add component scanning for the db-mcp package:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.indrard.dbmcp", "com.your.company.project"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 2. Configure Database Connection

Add database configuration to your `application.properties`:

```properties
# Database Type: postgres, oracle, mssql
app.db.type=postgres

# PostgreSQL Configuration
app.db.postgres.host=localhost
app.db.postgres.port=5432
app.db.postgres.database=yourdb
app.db.postgres.user=youruser
app.db.postgres.password=yourpassword

# Oracle Configuration (if using Oracle)
app.db.oracle.host=localhost
app.db.oracle.port=1521
app.db.oracle.serviceName=ORCL
app.db.oracle.user=youruser
app.db.oracle.password=yourpassword

# SQL Server Configuration (if using SQL Server)
app.db.mssql.host=localhost
app.db.mssql.port=1433
app.db.mssql.database=yourdb
app.db.mssql.user=sa
app.db.mssql.password=yourpassword
app.db.mssql.encrypt=false
app.db.mssql.trustServerCertificate=true
```

### 3. Configure AI Provider (Optional)

To enable AI chat functionality, configure either OpenAI or Ollama:

```properties
# AI Provider: openai or ollama
ai.provider=ollama

# OpenAI Configuration (when ai.provider=openai)
openai.api.key=your-api-key-here
openai.model=gpt-4o
openai.temperature=0.7

# Ollama Configuration (when ai.provider=ollama)
ollama.base.url=http://localhost:11434
ollama.model=llama3.2
ollama.temperature=0.7

# Conversation settings
openai.conversation.max-history=50
openai.conversation.timeout-minutes=30
```

## Usage

### Using Database Services

Inject the database services into your components:

```java
@RestController
@RequestMapping("/api/custom")
public class CustomController {
    
    @Autowired
    private McpToolService mcpToolService;
    
    @Autowired
    private DatabaseService databaseService;
    
    @GetMapping("/tables")
    public List<TableListItem> getTables() {
        return mcpToolService.listTables(null, null);
    }
    
    @PostMapping("/query")
    public QueryResult executeQuery(@RequestBody QueryRequest request) {
        return mcpToolService.runQuery(request);
    }
}
```

### Using AI Chat Service

```java
@RestController
@RequestMapping("/api/ai")
public class AiController {
    
    @Autowired
    private AIChatService aiChatService;
    
    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        AIChatService.ChatResult result = aiChatService.chat(message);
        return result.getResponse();
    }
}
```

### Extending with Custom Functions

Create custom function handlers by implementing similar patterns:

```java
@Service
public class CustomFunctionHandler {
    
    @Autowired
    private FunctionCallHandler functionCallHandler;
    
    public void addCustomFunction() {
        // Add your custom database functions
        // that will be available to the AI
    }
}
```

## Available Services

- **McpToolService**: Core database operations (queries, table info, etc.)
- **DatabaseService**: Lower-level database operations
- **AIChatService**: AI-powered chat with function calling
- **FunctionCallHandler**: Manages available functions for AI
- **McpService**: MCP protocol implementation

## MCP Endpoints

When used in your application, the following endpoints are available:

- `GET /mcp/sse` - MCP Server-Sent Events endpoint
- `POST /api/chat` - AI chat endpoint
- `POST /api/database/*` - Direct database operation endpoints

## Notes

- The library excludes `DataSourceAutoConfiguration` to allow manual database configuration
- Database drivers are optional dependencies - include only what you need
- AI functionality requires either OpenAI API key or Ollama running locally
- CORS is configured to allow all origins for development (configure appropriately for production)

## Example Project Structure

```
your-project/
├── src/main/java/
│   └── com/your/company/
│       ├── YourApplication.java  (with @ComponentScan)
│       ├── controller/
│       │   └── CustomController.java
│       └── service/
│           └── CustomService.java
├── src/main/resources/
│   └── application.properties
└── pom.xml (with db-mcp dependency)
```
