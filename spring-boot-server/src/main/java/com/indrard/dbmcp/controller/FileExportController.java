package com.indrard.dbmcp.controller;

import com.indrard.dbmcp.model.QueryResult;
import com.indrard.dbmcp.service.AIChatService;
import com.indrard.dbmcp.service.DatabaseService;
import com.indrard.dbmcp.service.ExportCacheService;
import com.indrard.dbmcp.service.FileGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for file export functionality
 * Allows exporting chat conversations and query results to various formats
 */
@RestController
@RequestMapping("/api/export")
public class FileExportController {

    private final FileGenerationService fileGenerationService;
    private final AIChatService chatService;
    private final DatabaseService databaseService;
    private final ExportCacheService exportCacheService;
    private final MessageSource messageSource;
    private final SimpleDateFormat filenameDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public FileExportController(FileGenerationService fileGenerationService, 
                               @Autowired(required = false) AIChatService chatService,
                               @Autowired(required = false) DatabaseService databaseService,
                               ExportCacheService exportCacheService,
                               MessageSource messageSource) {
        this.fileGenerationService = fileGenerationService;
        this.chatService = chatService;
        this.databaseService = databaseService;
        this.exportCacheService = exportCacheService;
        this.messageSource = messageSource;
    }

    /**
     * Export chat conversation to PDF
     * GET /api/export/chat/pdf?threadId={threadId}
     */
    @GetMapping("/chat/pdf")
    public ResponseEntity<byte[]> exportChatToPdf(
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) String title) {
        
        try {
            // Get conversation history
            if (threadId == null || chatService == null) {
                return ResponseEntity.badRequest().build();
            }

            List<com.indrard.dbmcp.service.ai.AIChatProvider.ChatMessage> history = 
                chatService.getConversationHistory(threadId);

            // Convert to simple format
            List<Map<String, String>> messages = convertChatMessagesToSimpleFormat(history);
            
            if (messages.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Generate PDF
            String reportTitle = title != null ? title : getMessage("export.chat.report.title");
            byte[] pdfBytes = fileGenerationService.generatePdfReport(reportTitle, messages);

            // Prepare response
            String filename = "chat_export_" + filenameDateFormat.format(new Date()) + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export chat conversation to Word
     * GET /api/export/chat/word?threadId={threadId}
     */
    @GetMapping("/chat/word")
    public ResponseEntity<byte[]> exportChatToWord(
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) String title) {
        
        try {
            // Get conversation history
            if (threadId == null || chatService == null) {
                return ResponseEntity.badRequest().build();
            }

            List<com.indrard.dbmcp.service.ai.AIChatProvider.ChatMessage> history = 
                chatService.getConversationHistory(threadId);

            // Convert to simple format
            List<Map<String, String>> messages = convertChatMessagesToSimpleFormat(history);
            
            if (messages.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Generate Word document
            String documentTitle = title != null ? title : getMessage("export.chat.report.title");
            byte[] docxBytes = fileGenerationService.generateWordDocument(documentTitle, messages);

            // Prepare response
            String filename = "chat_export_" + filenameDateFormat.format(new Date()) + ".docx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(docxBytes.length);

            return new ResponseEntity<>(docxBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export data to CSV
     * POST /api/export/csv
     * Body: { "headers": [...], "rows": [[...], [...]], "title": "optional" }
     */
    @PostMapping("/csv")
    public ResponseEntity<byte[]> exportToCsv(@RequestBody Map<String, Object> data) {
        try {
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) data.get("headers");
            @SuppressWarnings("unchecked")
            List<List<Object>> rows = (List<List<Object>>) data.get("rows");
            
            if (headers == null || rows == null) {
                return ResponseEntity.badRequest().build();
            }

            // Generate Excel-compatible CSV
            byte[] csvBytes = fileGenerationService.generateExcelCsv(headers, rows);

            // Prepare response
            String title = data.get("title") != null ? data.get("title").toString() : "export";
            String filename = sanitizeFilename(title) + "_" + filenameDateFormat.format(new Date()) + ".csv";
            
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.parseMediaType("text/csv"));
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, httpHeaders, HttpStatus.OK);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export query results to PDF
     * POST /api/export/query/pdf
     * Body: { "title": "...", "headers": [...], "rows": [[...], [...]] }
     */
    @PostMapping("/query/pdf")
    public ResponseEntity<byte[]> exportQueryResultToPdf(@RequestBody Map<String, Object> data) {
        try {
            String title = data.get("title") != null ? data.get("title").toString() : getMessage("export.query.results.title");
            
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) data.get("headers");
            @SuppressWarnings("unchecked")
            List<List<Object>> rows = (List<List<Object>>) data.get("rows");
            
            if (headers == null || rows == null) {
                return ResponseEntity.badRequest().build();
            }

            // Generate PDF
            byte[] pdfBytes = fileGenerationService.generateQueryResultPdf(title, headers, rows);

            // Prepare response
            String filename = sanitizeFilename(title) + "_" + filenameDateFormat.format(new Date()) + ".pdf";
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_PDF);
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, httpHeaders, HttpStatus.OK);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Execute SQL query and export to CSV/Excel
     * POST /api/export/query-to-csv
     * Body: { "sql": "SELECT * FROM users", "title": "users_export", "maxRows": 10000 }
     */
    @PostMapping("/query-to-csv")
    public ResponseEntity<byte[]> exportQueryToCsv(@RequestBody Map<String, Object> request) {
        try {
            if (databaseService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            String sql = (String) request.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Validate it's a SELECT query
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return ResponseEntity.badRequest().build();
            }

            Integer maxRows = request.get("maxRows") != null ? 
                (Integer) request.get("maxRows") : 10000;
            String title = request.get("title") != null ? 
                request.get("title").toString() : "query_export";

            // Execute query
            QueryResult result = databaseService.executeQuery(sql, maxRows, false);
            
            if (result.getRows() == null || result.getRows().isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Convert to format needed for CSV generation
            List<String> headers = result.getColumns();
            List<List<Object>> rows = result.getRows().stream()
                .map(row -> headers.stream()
                    .map(row::get)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());

            // Generate CSV
            byte[] csvBytes = fileGenerationService.generateExcelCsv(headers, rows);

            // Prepare response
            String filename = sanitizeFilename(title) + "_" + filenameDateFormat.format(new Date()) + ".csv";
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.parseMediaType("text/csv"));
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, httpHeaders, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Execute SQL query and export to PDF
     * POST /api/export/query-to-pdf
     * Body: { "sql": "SELECT * FROM users", "title": "users_report", "maxRows": 1000 }
     */
    @PostMapping("/query-to-pdf")
    public ResponseEntity<byte[]> exportQueryDirectlyToPdf(@RequestBody Map<String, Object> request) {
        try {
            if (databaseService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            String sql = (String) request.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Validate it's a SELECT query
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return ResponseEntity.badRequest().build();
            }

            Integer maxRows = request.get("maxRows") != null ? 
                (Integer) request.get("maxRows") : 1000;
            String title = request.get("title") != null ? 
                request.get("title").toString() : getMessage("export.query.results.title");

            // Execute query
            QueryResult result = databaseService.executeQuery(sql, maxRows, false);
            
            if (result.getRows() == null || result.getRows().isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Convert to format needed for PDF generation
            List<String> headers = result.getColumns();
            List<List<Object>> rows = result.getRows().stream()
                .map(row -> headers.stream()
                    .map(row::get)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());

            // Generate PDF
            byte[] pdfBytes = fileGenerationService.generateQueryResultPdf(title, headers, rows);

            // Prepare response
            String filename = sanitizeFilename(title) + "_" + filenameDateFormat.format(new Date()) + ".pdf";
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_PDF);
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, httpHeaders, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export entire table to CSV/Excel
     * GET /api/export/table-to-csv?table={tableName}&schema={schema}&maxRows={maxRows}
     */
    @GetMapping("/table-to-csv")
    public ResponseEntity<byte[]> exportTableToCsv(
            @RequestParam String table,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false, defaultValue = "10000") Integer maxRows) {
        
        try {
            if (databaseService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            // Build SQL query
            String tableName = schema != null ? schema + "." + table : table;
            String sql = "SELECT * FROM " + tableName + " LIMIT " + maxRows;

            // Execute query
            QueryResult result = databaseService.executeQuery(sql, maxRows, false);
            
            if (result.getRows() == null || result.getRows().isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Convert to format needed for CSV generation
            List<String> headers = result.getColumns();
            List<List<Object>> rows = result.getRows().stream()
                .map(row -> headers.stream()
                    .map(row::get)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());

            // Generate CSV
            byte[] csvBytes = fileGenerationService.generateExcelCsv(headers, rows);

            // Prepare response
            String filename = sanitizeFilename(table) + "_" + filenameDateFormat.format(new Date()) + ".csv";
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.parseMediaType("text/csv"));
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, httpHeaders, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert ChatMessage list to simple format for export
     */
    private List<Map<String, String>> convertChatMessagesToSimpleFormat(
            List<com.indrard.dbmcp.service.ai.AIChatProvider.ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        
        for (com.indrard.dbmcp.service.ai.AIChatProvider.ChatMessage chatMsg : history) {
            Map<String, String> message = new HashMap<>();
            message.put("role", chatMsg.getRole() != null ? chatMsg.getRole() : "unknown");
            message.put("content", chatMsg.getContent() != null ? chatMsg.getContent() : "");
            messages.add(message);
        }
        
        return messages;
    }

    /**
     * Download cached CSV export by ID
     * GET /api/export/download-csv/{exportId}
     * 
     * This endpoint allows simple URL-based downloads without exposing SQL
     * The exportId is generated by exportLastQueryToCsv and cached for 30 minutes
     */
    @GetMapping("/download-csv/{exportId}")
    public ResponseEntity<byte[]> downloadCachedCsv(@PathVariable String exportId) {
        try {
            // Retrieve cached export
            ExportCacheService.CachedExport export = exportCacheService.getExport(exportId);
            
            if (export == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Export not found or expired. Please generate a new export.".getBytes());
            }
            
            QueryResult result = export.getResult();
            
            if (result.getRows() == null || result.getRows().isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            // Convert to format needed for CSV generation
            List<String> headers = result.getColumns();
            List<List<Object>> rows = result.getRows().stream()
                .map(row -> headers.stream()
                    .map(row::get)
                    .collect(Collectors.toList()))
                .collect(Collectors.toList());

            // Generate CSV
            byte[] csvBytes = fileGenerationService.generateExcelCsv(headers, rows);

            // Prepare response
            String filename = sanitizeFilename(export.getTitle()) + "_" + 
                            filenameDateFormat.format(new Date()) + ".csv";
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.parseMediaType("text/csv"));
            httpHeaders.setContentDispositionFormData("attachment", filename);
            httpHeaders.setContentLength(csvBytes.length);

            return new ResponseEntity<>(csvBytes, httpHeaders, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error generating CSV: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Sanitize filename by removing invalid characters
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /**
     * Get localized message
     */
    private String getMessage(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
