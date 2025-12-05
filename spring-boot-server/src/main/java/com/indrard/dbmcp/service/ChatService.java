package com.indrard.dbmcp.service;

import com.indrard.dbmcp.model.QueryResult;
import com.indrard.dbmcp.model.TableListItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private final McpService mcpService;
    private final DatabaseService databaseService;
    private final Optional<AIChatService> openAIChatService;

    @Autowired
    public ChatService(McpService mcpService, DatabaseService databaseService, Optional<AIChatService> openAIChatService) {
        this.mcpService = mcpService;
        this.databaseService = databaseService;
        this.openAIChatService = openAIChatService;
    }

    public String processMessage(String userMessage) {
        // Try OpenAI first if configured
        if (openAIChatService.isPresent()) {
            try {
                AIChatService.ChatResult result = openAIChatService.get().chat(userMessage);
                String aiResponse = result.getResponse();
                // If OpenAI returns a non-configuration error response, use it
                if (!aiResponse.contains("OpenAI is not configured")) {
                    return aiResponse;
                }
            } catch (Exception e) {
                // Fall through to simple responses if OpenAI fails
                System.err.println("OpenAI error, falling back to simple responses: " + e.getMessage());
            }
        }

        // Fall back to simple pattern-based responses
        return getSimpleResponse(userMessage);
    }

    private String getSimpleResponse(String userMessage) {
        String lowerMessage = userMessage.toLowerCase().trim();

        // Check for SQL query execution patterns first
        String sqlQuery = extractSqlQuery(userMessage);
        if (sqlQuery != null) {
            try {
                QueryResult result = databaseService.executeQuery(sqlQuery, 100, false);
                return formatQueryResult(result);
            } catch (Exception e) {
                return "❌ Error executing query: " + e.getMessage() + "\n\n" +
                       "💡 Tip: Check your SQL syntax and ensure you have the necessary permissions.";
            }
        }

        // List tables - enhanced pattern matching
        if (matches(lowerMessage, "list tables", "show tables", "what tables", "all tables", "get tables",
                "tables list", "display tables", "show all tables", "give me tables", "list all tables")) {
            try {
                List<TableListItem> tables = databaseService.listTables(null, null);
                return formatTableList(tables);
            } catch (Exception e) {
                return "❌ Error listing tables: " + e.getMessage();
            }
        }

        // List schemas
        if (matches(lowerMessage, "list schemas", "show schemas", "what schemas", "all schemas", "get schemas",
                "schemas list", "display schemas", "available schemas", "database list")) {
            try {
                return "📁 Use the 'list_schemas' tool to see all available schemas/databases.\n\n" +
                       "This will show you all accessible database schemas in your current connection.";
            } catch (Exception e) {
                return "❌ Error: " + e.getMessage();
            }
        }

        // List views
        if (matches(lowerMessage, "list views", "show views", "what views", "all views", "get views",
                "views list", "display views", "available views")) {
            return "👁️ Use the 'list_views' tool to see all available views.\n\n" +
                   "You can filter by schema or pattern if needed.";
        }

        // Table information - extract table name
        if (matches(lowerMessage, "describe", "table info", "table structure", "columns of", "structure of",
                "info about", "information about", "details about", "schema of")) {
            String tableName = extractTableName(lowerMessage);
            if (tableName != null) {
                return "🔍 Use the 'get_table_info' tool with table name: '" + tableName + "'\n\n" +
                       "This will show:\n" +
                       "• Column names and data types\n" +
                       "• Primary and foreign keys\n" +
                       "• Nullable fields\n" +
                       "• Default values and constraints";
            }
            return "🔍 To get table information, use the 'get_table_info' tool.\n\n" +
                   "Specify the table name to see columns, keys, and constraints.";
        }

        // Row count - extract table name
        if (matches(lowerMessage, "count", "how many rows", "row count", "rows in", "number of rows")) {
            String tableName = extractTableName(lowerMessage);
            if (tableName != null) {
                return "🔢 Use the 'get_row_count' tool with table name: '" + tableName + "'\n\n" +
                       "You can also add a WHERE clause to filter the count.";
            }
            return "🔢 To count rows, use the 'get_row_count' tool.\n\n" +
                   "Specify the table name, and optionally add a WHERE clause for filtered counts.";
        }

        // Sample data
        if (matches(lowerMessage, "sample", "preview", "show data", "view data", "peek at", "look at data")) {
            String tableName = extractTableName(lowerMessage);
            if (tableName != null) {
                return "👀 Use the 'sample_table_data' tool with table name: '" + tableName + "'\n\n" +
                       "This will show a quick preview of the data without writing SQL.";
            }
            return "👀 To preview table data, use the 'sample_table_data' tool.\n\n" +
                   "You can specify how many rows to sample and whether to use random sampling.";
        }

        // Health check
        if (matches(lowerMessage, "health", "status", "connection status", "database status", "is it working")) {
            return "🏥 Use the 'health_check' tool to see:\n" +
                   "• Database connection status\n" +
                   "• Server uptime\n" +
                   "• Query performance metrics\n" +
                   "• Overall system health";
        }

        // Performance metrics
        if (matches(lowerMessage, "performance", "metrics", "statistics", "stats", "query performance")) {
            return "📊 Use the 'get_performance_metrics' tool to see:\n" +
                   "• Average query execution time\n" +
                   "• Success rate\n" +
                   "• Slowest queries\n" +
                   "• Total queries executed";
        }

        // Greetings
        if (matches(lowerMessage, "hello", "hi", "hey", "greetings", "good morning", "good afternoon")) {
            return "👋 Hello! I'm your database assistant powered by AI.\n\n" +
                    "💬 **Natural Language Support**: Just ask me questions in plain English!\n" +
                    "   • \"Show me all tables\"\n" +
                    "   • \"What's in the users table?\"\n" +
                    "   • \"How many rows are in orders?\"\n\n" +
                    "⚡ **Direct SQL**: Run queries directly\n" +
                    "   • SELECT * FROM users LIMIT 10\n" +
                    "   • Execute: SELECT COUNT(*) FROM orders\n\n" +
                    "🛠️ **Tools Panel**: Use specialized tools from the right panel\n\n" +
                    "What would you like to know about your database?";
        }

        // Help
        if (matches(lowerMessage, "help", "what can you do", "capabilities", "features", "commands")) {
            return "🤖 **Database Assistant Capabilities**\n\n" +
                    "💬 **Ask in Natural Language**:\n" +
                    "   • \"List all tables\" - See all tables with row counts\n" +
                    "   • \"Describe users table\" - Get table structure\n" +
                    "   • \"Count rows in orders\" - Get row counts\n" +
                    "   • \"Show me data from products\" - Preview table data\n\n" +
                    "⚡ **Execute SQL Queries**:\n" +
                    "   • Type any SELECT query directly\n" +
                    "   • Or prefix with 'Execute:' or 'Run:'\n\n" +
                    "🛠️ **Available Tools** (use from right panel):\n" +
                    "   • list_tables, list_schemas, list_views\n" +
                    "   • get_table_info, get_row_count, sample_table_data\n" +
                    "   • run_query, explain_query, run_transaction\n" +
                    "   • health_check, get_performance_metrics\n" +
                    "   • And more!\n\n" +
                    "💡 **Tips**:\n" +
                    "   • I can execute functions automatically when configured with OpenAI\n" +
                    "   • Use specific tool names for precise operations\n" +
                    "   • Ask follow-up questions naturally";
        }

        // Query help
        if (lowerMessage.contains("how to query") || lowerMessage.contains("how to execute") || 
            lowerMessage.contains("run query") || lowerMessage.contains("execute query")) {
            return "📝 **How to Execute SQL Queries**\n\n" +
                    "**Option 1: Direct SQL**\n" +
                    "Just type your query:\n" +
                    "   SELECT * FROM users LIMIT 10\n" +
                    "   SELECT COUNT(*) FROM orders WHERE status = 'completed'\n\n" +
                    "**Option 2: With Prefix**\n" +
                    "   Execute: SELECT name, email FROM customers\n" +
                    "   Run: SELECT * FROM products WHERE price > 100\n\n" +
                    "**Option 3: Using Tools**\n" +
                    "Use the 'run_query' tool from the tools panel for advanced options:\n" +
                    "   • Set max rows limit\n" +
                    "   • Exclude large columns\n" +
                    "   • Get detailed query metadata\n\n" +
                    "💡 Supported: SELECT, WITH queries\n" +
                    "❌ Not supported: INSERT, UPDATE, DELETE (read-only)";
        }

        // Thank you
        if (matches(lowerMessage, "thank", "thanks", "thx", "appreciate")) {
            return "😊 You're welcome! Let me know if you need anything else with your database.";
        }

        // Goodbye
        if (matches(lowerMessage, "bye", "goodbye", "see you", "exit", "quit")) {
            return "👋 Goodbye! Come back anytime you need help with your database.";
        }

        // Default response with smart suggestions
        return "🤔 I'm not sure how to help with that specific request.\n\n" +
                "💡 **Try asking**:\n" +
                "   • \"Show me all tables\"\n" +
                "   • \"List schemas\"\n" +
                "   • \"Describe [table name]\"\n" +
                "   • \"Count rows in [table name]\"\n" +
                "   • \"Preview data from [table name]\"\n" +
                "   • \"Health check\"\n\n" +
                "⚡ **Or run SQL directly**:\n" +
                "   SELECT * FROM your_table LIMIT 10\n\n" +
                "❓ Type 'help' to see all capabilities!";
    }

    /**
     * Extract table name from natural language query
     */
    private String extractTableName(String message) {
        // Patterns like "describe users table", "count rows in orders", "info about customers"
        Pattern pattern = Pattern.compile("(?:table|from|in|about|of)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean matches(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String extractSqlQuery(String message) {
        // Pattern 1: "Execute: SELECT ..." or "Run: SELECT ..."
        Pattern prefixPattern = Pattern.compile("(?:execute|run)\\s*:\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = prefixPattern.matcher(message.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Pattern 2: Direct SQL query (starts with SELECT, INSERT, UPDATE, DELETE, WITH)
        String trimmed = message.trim();
        Pattern sqlPattern = Pattern.compile("^(SELECT|INSERT|UPDATE|DELETE|WITH)\\s+.+", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        matcher = sqlPattern.matcher(trimmed);
        if (matcher.find()) {
            return trimmed;
        }

        return null;
    }

    private String formatQueryResult(QueryResult result) {
        if (result.getRows() == null || result.getRows().isEmpty()) {
            return "✅ Query executed successfully.\n📊 No rows returned.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✅ Query executed successfully.\n");
        sb.append("📊 Found ").append(result.getRowCount()).append(" row(s)\n\n");

        // Get columns
        List<String> columns = result.getColumns();
        if (columns == null || columns.isEmpty()) {
            sb.append(result.getRows().toString());
            return sb.toString();
        }

        // Format as table
        sb.append("```\n");
        
        // Header
        for (String col : columns) {
            sb.append(String.format("%-20s", col)).append(" | ");
        }
        sb.append("\n");
        sb.append("-".repeat(columns.size() * 23)).append("\n");

        // Rows (limit to first 50 for display)
        int displayLimit = Math.min(50, result.getRows().size());
        for (int i = 0; i < displayLimit; i++) {
            Map<String, Object> row = result.getRows().get(i);
            for (String col : columns) {
                Object val = row.get(col);
                String value = val == null ? "NULL" : val.toString();
                if (value.length() > 20) {
                    value = value.substring(0, 17) + "...";
                }
                sb.append(String.format("%-20s", value)).append(" | ");
            }
            sb.append("\n");
        }

        if (result.getRows().size() > displayLimit) {
            sb.append("... and ").append(result.getRows().size() - displayLimit).append(" more row(s)\n");
        }

        sb.append("```");
        return sb.toString();
    }

    private String formatTableList(List<TableListItem> tables) {
        if (tables == null || tables.isEmpty()) {
            return "📋 No tables found in the database.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Found ").append(tables.size()).append(" table(s):\n\n");

        for (TableListItem table : tables) {
            sb.append("• ").append(table.getName());
            if (table.getRowCount() != null) {
                sb.append(" - ").append(table.getRowCount()).append(" rows");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
