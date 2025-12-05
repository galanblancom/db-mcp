package com.example.custommcp.controller;

import com.example.custommcp.service.CustomToolsService;
import com.example.custommcp.service.CustomAIChatService;
import com.indrard.dbmcp.service.AIChatService;
import com.indrard.dbmcp.service.McpToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Custom Tools Controller
 * Exposes custom MCP tools via REST endpoints
 */
@RestController
@RequestMapping("/api/custom")
@Slf4j
public class CustomToolsController {

    private final CustomToolsService customToolsService;
    private final McpToolService mcpToolService;
    private final Optional<CustomAIChatService> customAIChatService;

    @Autowired
    public CustomToolsController(
            CustomToolsService customToolsService,
            McpToolService mcpToolService,
            Optional<CustomAIChatService> customAIChatService) {
        this.customToolsService = customToolsService;
        this.mcpToolService = mcpToolService;
        this.customAIChatService = customAIChatService;
    }

    /**
     * Get comprehensive database summary
     * GET /api/custom/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDatabaseSummary() {
        log.info("REST: Getting database summary");
        return ResponseEntity.ok(customToolsService.getDatabaseSummary());
    }

    /**
     * Find large tables above a threshold
     * GET /api/custom/large-tables?minRows=1000
     */
    @GetMapping("/large-tables")
    public ResponseEntity<Map<String, Object>> findLargeTables(
            @RequestParam(required = false, defaultValue = "1000") Long minRows) {
        log.info("REST: Finding large tables with minRows={}", minRows);
        return ResponseEntity.ok(customToolsService.findLargeTables(minRows));
    }

    /**
     * Search tables by name pattern
     * GET /api/custom/search-tables?pattern=GT_%
     */
    @GetMapping("/search-tables")
    public ResponseEntity<Map<String, Object>> searchTables(
            @RequestParam String pattern) {
        log.info("REST: Searching tables with pattern={}", pattern);
        return ResponseEntity.ok(customToolsService.searchTablesByPattern(pattern));
    }

    /**
     * Get detailed table statistics
     * GET /api/custom/table-stats/{tableName}
     */
    @GetMapping("/table-stats/{tableName}")
    public ResponseEntity<Map<String, Object>> getTableStats(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema) {
        log.info("REST: Getting table stats for table={}, schema={}", tableName, schema);
        return ResponseEntity.ok(customToolsService.getTableStatisticsSummary(tableName, schema));
    }

    /**
     * Compare row counts between two tables
     * GET /api/custom/compare-tables?table1=GT_OBRAS&table2=GT_TRABAJOS
     */
    @GetMapping("/compare-tables")
    public ResponseEntity<Map<String, Object>> compareTables(
            @RequestParam String table1,
            @RequestParam String table2,
            @RequestParam(required = false) String schema) {
        log.info("REST: Comparing tables {} and {}", table1, table2);
        return ResponseEntity.ok(customToolsService.compareTableRowCounts(table1, table2, schema));
    }

    /**
     * Execute custom AI chat query with enhanced tools
     * POST /api/custom/ai-chat
     * Body: { "message": "how many tables are there?" }
     * 
     * This endpoint uses CustomAIChatService which has access to:
     * - All 22 base database tools from db-mcp library
     * - All 5 custom analysis tools
     * Total: 27 tools available to AI
     */
    @PostMapping("/ai-chat")
    public ResponseEntity<Map<String, Object>> aiChat(@RequestBody Map<String, String> request) {
        if (!customAIChatService.isPresent()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "success", false,
                    "error", "AI Chat service is not available. Configure AI provider (OpenAI or Ollama) to enable this feature."
                ));
        }
        
        String message = request.get("message");
        String threadId = request.get("threadId"); // Optional: continue conversation
        
        log.info("REST: AI Chat request: {}", message);
        
        try {
            AIChatService.ChatResult result = threadId != null 
                    ? customAIChatService.get().chat(threadId, message)
                    : customAIChatService.get().chat(message);
                    
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "response", result.getResponse(),
                    "threadId", result.getThreadId()
            ));
        } catch (Exception e) {
            log.error("Error in AI chat", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Get conversation history
     * GET /api/custom/ai-chat/{threadId}/history
     */
    @GetMapping("/ai-chat/{threadId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String threadId) {
        if (!customAIChatService.isPresent()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("success", false, "error", "AI Chat service not available"));
        }
        log.info("REST: Getting conversation history for thread: {}", threadId);
        List<?> history = customAIChatService.get().getConversationHistory(threadId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "threadId", threadId,
                "messageCount", history.size(),
                "messages", history
        ));
    }
    
    /**
     * Clear conversation
     * DELETE /api/custom/ai-chat/{threadId}
     */
    @DeleteMapping("/ai-chat/{threadId}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String threadId) {
        if (!customAIChatService.isPresent()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("success", false, "error", "AI Chat service not available"));
        }
        log.info("REST: Clearing conversation thread: {}", threadId);
        customAIChatService.get().clearConversation(threadId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Conversation cleared"
        ));
    }
    
    /**
     * List active conversations
     * GET /api/custom/ai-chat/threads
     */
    @GetMapping("/ai-chat/threads")
    public ResponseEntity<Map<String, Object>> listThreads() {
        if (!customAIChatService.isPresent()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("success", false, "error", "AI Chat service not available"));
        }
        log.info("REST: Listing active conversation threads");
        List<String> threads = customAIChatService.get().getActiveConversations();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", threads.size(),
                "threadIds", threads
        ));
    }

    /**
     * Health check endpoint for custom tools
     * GET /api/custom/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "custom-mcp-tools",
                "timestamp", System.currentTimeMillis()
        ));
    }

}
