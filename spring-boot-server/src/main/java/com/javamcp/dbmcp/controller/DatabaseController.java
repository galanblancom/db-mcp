package com.javamcp.dbmcp.controller;

import com.javamcp.dbmcp.model.*;
import com.javamcp.dbmcp.model.request.QueryRequest;
import com.javamcp.dbmcp.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DatabaseController {

    private final DatabaseService databaseService;

    @Autowired
    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResult> executeQuery(@RequestBody QueryRequest request) {
        try {
            if (request.getDryRun() != null && request.getDryRun()) {
                // Implement dry run logic if needed, or just validate
                return ResponseEntity.ok(new QueryResult(null, 0, null));
            }
            
            int maxRows = request.getMaxRows() != null ? request.getMaxRows() : 1000;
            boolean excludeLarge = request.getExcludeLargeColumns() != null ? request.getExcludeLargeColumns() : false;
            
            QueryResult result = databaseService.executeQuery(request.getSql(), maxRows, excludeLarge);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build(); // Simplify error handling for now
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<List<TableListItem>> listTables(
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String pattern) {
        try {
            return ResponseEntity.ok(databaseService.listTables(schema, pattern));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/tables/{tableName}")
    public ResponseEntity<TableInfo> getTableInfo(
            @PathVariable String tableName,
            @RequestParam(required = false) String schema) {
        try {
            return ResponseEntity.ok(databaseService.getTableInfo(tableName, schema));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/schemas")
    public ResponseEntity<List<SchemaInfo>> listSchemas() {
        try {
            return ResponseEntity.ok(databaseService.listSchemas());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/views")
    public ResponseEntity<List<ViewInfo>> listViews(
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String pattern) {
        try {
            return ResponseEntity.ok(databaseService.listViews(schema, pattern));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/views/{viewName}")
    public ResponseEntity<ViewDefinition> getViewDefinition(
            @PathVariable String viewName,
            @RequestParam(required = false) String schema) {
        try {
            return ResponseEntity.ok(databaseService.getViewDefinition(viewName, schema));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        boolean connected = databaseService.testConnection();
        if (connected) {
            return ResponseEntity.ok("Healthy");
        } else {
            return ResponseEntity.status(503).body("Database connection failed");
        }
    }
}
