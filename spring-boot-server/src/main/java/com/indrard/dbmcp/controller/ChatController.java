package com.indrard.dbmcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indrard.dbmcp.model.request.ChatRequest;
import com.indrard.dbmcp.model.request.FolderContextRequest;
import com.indrard.dbmcp.service.ChromaDBService;
import com.indrard.dbmcp.service.FileProcessingService;
import com.indrard.dbmcp.service.FolderContextService;
import com.indrard.dbmcp.service.AIChatService;
import com.indrard.dbmcp.service.ai.AIChatProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for chat functionality with conversation thread support
 * Only available when OpenAI is configured
 */
@RestController
@RequestMapping("/api/chat")
@ConditionalOnBean(AIChatService.class)
public class ChatController {

    private final AIChatService chatService;
    private final FolderContextService folderContextService;
    private final ChromaDBService chromaDBService;
    private final FileProcessingService fileProcessingService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(AIChatService chatService, FolderContextService folderContextService,
            ChromaDBService chromaDBService, FileProcessingService fileProcessingService,
            MessageSource messageSource) {
        this.chatService = chatService;
        this.folderContextService = folderContextService;
        this.chromaDBService = chromaDBService;
        this.fileProcessingService = fileProcessingService;
        this.messageSource = messageSource;
    }

    /**
     * Send a message without thread ID (creates new conversation)
     * POST /api/chat
     * Body: { "message": "Your message here" }
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {

        String userMessage = request.getMessage();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(getMessage("error.message.required")));
        }

        try {
            // Query ChromaDB for relevant context if requested
            String chromaContext = Boolean.TRUE.equals(request.getUseChromaDB()) 
                ? buildChromaDBContext(userMessage) 
                : "";

            // Build final message with context
            String enhancedMessage = !chromaContext.isEmpty()
                ? chromaContext + "\n" + getMessage("prompt.user.question", userMessage)
                : userMessage;

            AIChatService.ChatResult result = chatService.chat(enhancedMessage);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Send a message with files (multipart form data)
     * POST /api/chat/upload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> chatWithFiles(
            @RequestParam String message,
            @RequestParam(required = false) Boolean useChromaDB,
            @RequestParam(required = false) String collectionName,
            @RequestParam(required = false) List<MultipartFile> files) {

        String userMessage = message;
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(getMessage("error.message.required")));
        }

        try {
            StringBuilder contextBuilder = new StringBuilder();

            // Process uploaded files
            if (files != null && !files.isEmpty()) {
                List<FileProcessingService.FileContent> fileContents = fileProcessingService.extractContents(files);
                
                if (!fileContents.isEmpty()) {
                    contextBuilder.append("📎 ").append(getMessage("success.files.processed", fileContents.size())).append("\n");
                    
                    // Store in ChromaDB if requested
                    if (Boolean.TRUE.equals(useChromaDB)) {
                        List<String> ids = new ArrayList<>();
                        List<String> documents = new ArrayList<>();
                        List<Map<String, String>> metadatas = new ArrayList<>();
                        
                        for (FileProcessingService.FileContent fileContent : fileContents) {
                            // Smart chunking based on file type
                            List<String> chunks;
                            if (fileContent.getExtension().equals("csv")) {
                                chunks = splitCsvIntoChunks(fileContent.getContent(), 100);
                            } else if (fileContent.isDocument()) {
                                // Larger chunks for documents (PDF/Word) - better semantic context
                                chunks = splitIntoChunks(fileContent.getContent(), 2000);
                            } else {
                                // Default for text files
                                chunks = splitIntoChunks(fileContent.getContent(), 1500);
                            }

                            for (int i = 0; i < chunks.size(); i++) {
                                ids.add(UUID.randomUUID().toString());
                                documents.add(chunks.get(i));
                                
                                Map<String, String> metadata = fileContent.toMetadata();
                                metadata.put("chunk", String.valueOf(i + 1));
                                metadata.put("total_chunks", String.valueOf(chunks.size()));
                                
                                // Debug log for first chunk
                                if (i == 0) {
                                    System.out.println("🔍 First chunk metadata for " + fileContent.getFilename() + ": " + metadata);
                                }
                                
                                metadatas.add(metadata);
                            }
                            
                            contextBuilder.append("  • ").append(fileContent.getFilename())
                                    .append(" (").append(chunks.size()).append(" chunks)\n");
                        }
                        
                        // Add to ChromaDB
                        chromaDBService.addDocuments(ids, documents, metadatas);
                        contextBuilder.append("✅ ").append(getMessage("success.indexed.chromadb")).append("\n\n");
                    } else {
                        // Direct content injection
                        contextBuilder.append("\n=== ").append(getMessage("context.file.contents")).append(" ===\n\n");
                        for (FileProcessingService.FileContent fileContent : fileContents) {
                            contextBuilder.append("File: ").append(fileContent.getFilename()).append("\n");
                            contextBuilder.append("Type: ").append(fileContent.getExtension()).append("\n");
                            contextBuilder.append("Content:\n```\n");
                            contextBuilder.append(fileContent.getSummary());
                            contextBuilder.append("\n```\n\n");
                        }
                    }
                }
            }

            // Query ChromaDB for relevant context based on user message
            if (Boolean.TRUE.equals(useChromaDB)) {
                contextBuilder.append(buildChromaDBContext(userMessage));
            }

            // Build final message with context
            String enhancedMessage = contextBuilder.toString() + "\n" + getMessage("prompt.user.question", userMessage);

            AIChatService.ChatResult result = chatService.chat(enhancedMessage);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Send a message with thread ID and files (multipart form data)
     * POST /api/chat/{threadId}/upload
     */
    @PostMapping(value = "/{threadId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> chatWithThreadAndFiles(
            @PathVariable String threadId,
            @RequestParam String message,
            @RequestParam(required = false) Boolean useChromaDB,
            @RequestParam(required = false) String collectionName,
            @RequestParam(required = false) List<MultipartFile> files) {

        String userMessage = message;
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(getMessage("error.message.required")));
        }

        try {
            StringBuilder contextBuilder = new StringBuilder();

            // Process uploaded files (same as above)
            if (files != null && !files.isEmpty()) {
                List<FileProcessingService.FileContent> fileContents = fileProcessingService.extractContents(files);

                if (!fileContents.isEmpty()) {
                    contextBuilder.append("📎 ").append(getMessage("success.files.processed", fileContents.size())).append("\n");

                    if (Boolean.TRUE.equals(useChromaDB)) {
                        List<String> ids = new ArrayList<>();
                        List<String> documents = new ArrayList<>();
                        List<Map<String, String>> metadatas = new ArrayList<>();

                        for (FileProcessingService.FileContent fileContent : fileContents) {
                            // Smart chunking based on file type
                            List<String> chunks;
                            if (fileContent.getExtension().equals("csv")) {
                                chunks = splitCsvIntoChunks(fileContent.getContent(), 100);
                            } else if (fileContent.isDocument()) {
                                // Larger chunks for documents (PDF/Word) - better semantic context
                                chunks = splitIntoChunks(fileContent.getContent(), 2000);
                            } else {
                                // Default for text files
                                chunks = splitIntoChunks(fileContent.getContent(), 1500);
                            }

                            for (int i = 0; i < chunks.size(); i++) {
                                String id = UUID.randomUUID().toString();
                                ids.add(id);
                                documents.add(chunks.get(i));

                                Map<String, String> metadata = fileContent.toMetadata();
                                metadata.put("chunk", String.valueOf(i + 1));
                                metadata.put("total_chunks", String.valueOf(chunks.size()));
                                
                                // Ensure total_rows is preserved in all chunks for CSV files
                                if (fileContent.getTotalRows() != null) {
                                    metadata.put("total_rows", String.valueOf(fileContent.getTotalRows()));
                                    metadata.put("data_type", "csv");
                                }
                                
                                metadatas.add(metadata);
                            }

                            contextBuilder.append("  • ").append(fileContent.getFilename())
                                    .append(" (").append(chunks.size()).append(" chunks)\n");
                        }

                        chromaDBService.addDocuments(ids, documents, metadatas);
                        contextBuilder.append("✅ ").append(getMessage("success.indexed.chromadb")).append("\n\n");
                    } else {
                        contextBuilder.append("\n=== ").append(getMessage("context.file.contents")).append(" ===\n\n");
                        for (FileProcessingService.FileContent fileContent : fileContents) {
                            contextBuilder.append("File: ").append(fileContent.getFilename()).append("\n");
                            contextBuilder.append("Type: ").append(fileContent.getExtension()).append("\n");
                            contextBuilder.append("Content:\n```\n");
                            contextBuilder.append(fileContent.getSummary());
                            contextBuilder.append("\n```\n\n");
                        }
                    }
                }
            }

            // Query ChromaDB for relevant context
            if (Boolean.TRUE.equals(useChromaDB)) {
                contextBuilder.append(buildChromaDBContext(userMessage));
            }

            // Build final message with context
            String enhancedMessage = contextBuilder.toString() + "\n" + getMessage("prompt.user.question", userMessage);

            AIChatService.ChatResult result = chatService.chat(threadId, enhancedMessage);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Build context from ChromaDB search results
     */
    private String buildChromaDBContext(String userMessage) {
        if (!chromaDBService.isInitialized()) {
            return "";
        }

        StringBuilder contextBuilder = new StringBuilder();
        try {
            // Retrieve relevant fragments - keep moderate to avoid overwhelming the AI
            // For quantitative questions (counts), the total_rows metadata is shown at top
            boolean isAggregateQuery = userMessage.toLowerCase().matches(".*(resumen|summary|resume|todo|all|cuant|how many|count|total).*");
            int numResults = isAggregateQuery ? 15 : 8;
            
            List<ChromaDBService.SearchResult> results = chromaDBService.search(userMessage, numResults);

            if (!results.isEmpty()) {
                contextBuilder.append("\n=== ").append(getMessage("context.relevant.content")).append(" ===\n\n");
                
                // Add metadata summary at the beginning - adapt based on file type
                Map<String, String> firstMetadata = results.get(0).getMetadata();
                if (firstMetadata != null) {
                    String filename = firstMetadata.get("filename");
                    String fileType = firstMetadata.get("type");
                    
                    contextBuilder.append("\n\n");
                    contextBuilder.append("═══════════════════════════════════════════\n");
                    contextBuilder.append("📄 ").append(getMessage("context.file.summary")).append(": ").append(filename).append("\n");
                    contextBuilder.append("═══════════════════════════════════════════\n");
                    
                    // For CSV files - show total rows
                    if (firstMetadata.containsKey("total_rows")) {
                        String totalRows = firstMetadata.get("total_rows");
                        contextBuilder.append(getMessage("context.total.rows", totalRows)).append("\n");
                        contextBuilder.append("\n");
                        contextBuilder.append(getMessage("context.instructions")).append(":\n");
                        contextBuilder.append("- ").append(getMessage("context.instruction.exact.rows", totalRows)).append("\n");
                        contextBuilder.append("- ").append(getMessage("context.instruction.no.count")).append("\n");
                        contextBuilder.append("- ").append(getMessage("context.instruction.no.sum")).append("\n");
                        contextBuilder.append("- ").append(getMessage("context.instruction.use.metadata", totalRows)).append("\n");
                        contextBuilder.append("- ").append(getMessage("context.instruction.fragments")).append("\n");
                    }
                    // For documents (PDF/Word) - show document type
                    else if ("document".equals(fileType)) {
                        contextBuilder.append(getMessage("context.document.type")).append(": ");
                        String extension = firstMetadata.get("extension");
                        if ("pdf".equals(extension)) {
                            contextBuilder.append("PDF");
                        } else if ("docx".equals(extension) || "doc".equals(extension)) {
                            contextBuilder.append("Word");
                        } else {
                            contextBuilder.append(extension.toUpperCase());
                        }
                        contextBuilder.append("\n");
                        
                        // Show total chunks as indication of document size
                        if (firstMetadata.containsKey("total_chunks")) {
                            contextBuilder.append("\n");
                            contextBuilder.append(getMessage("context.instructions")).append(":\n");
                            contextBuilder.append("- ").append(getMessage("context.instruction.fragments")).append("\n");
                        }
                    }
                    
                    contextBuilder.append("═══════════════════════════════════════════\n\n");
                }

                for (int i = 0; i < results.size(); i++) {
                    ChromaDBService.SearchResult result = results.get(i);
                    contextBuilder.append("--- ").append(getMessage("context.match")).append(" ").append(i + 1);

                    if (result.getSimilarity() != null) {
                        contextBuilder.append(" (").append(getMessage("context.relevant.percent", String.format("%.1f%%", result.getSimilarity() * 100)))
                                .append(")");
                    }
                    contextBuilder.append(" ---\n");

                    if (result.getMetadata() != null && result.getMetadata().containsKey("filename")) {
                        contextBuilder.append(getMessage("context.source")).append(" ").append(result.getMetadata().get("filename"));
                        
                        if (result.getMetadata().containsKey("chunk")) {
                            contextBuilder.append(" (")
                                    .append(getMessage("context.chunk"))
                                    .append(" ")
                                    .append(result.getMetadata().get("chunk"))
                                    .append("/")
                                    .append(result.getMetadata().get("total_chunks"));
                            
                            // Show data type if available
                            if (result.getMetadata().containsKey("data_type")) {
                                contextBuilder.append(" - ").append(result.getMetadata().get("data_type").toUpperCase());
                            }
                            contextBuilder.append(")");
                        }
                        contextBuilder.append("\n\n");
                    }
                    contextBuilder.append(result.getDocument()).append("\n\n");
                }

                contextBuilder.append("=== ").append(getMessage("context.end")).append(" ===\n\n");
            }
        } catch (Exception e) {
            contextBuilder.append("⚠️ ").append(getMessage("error.chromadb.search", e.getMessage())).append("\n\n");
            contextBuilder.append("⚠️ ChromaDB search error: ").append(e.getMessage()).append("\n\n");
        }

        return contextBuilder.toString();
    }

    // Helper method to split text into chunks with overlap for better context preservation
    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        // Adaptive overlap: larger for bigger chunks (documents), smaller for text
        int overlap = Math.min(200, chunkSize / 10); // 10% overlap, max 200 chars
        int start = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // Try to break at a sentence boundary (., !, ?) first
            if (end < text.length()) {
                int sentenceEnd = findSentenceBoundary(text, start, end);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                } else {
                    // Fall back to newline or space
                    int newlineIndex = text.lastIndexOf('\n', end);
                    int spaceIndex = text.lastIndexOf(' ', end);
                    int breakIndex = Math.max(newlineIndex, spaceIndex);
                    
                    if (breakIndex > start) {
                        end = breakIndex;
                    }
                }
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // Move start with overlap to preserve context between chunks
            start = Math.max(end - overlap, start + 1);
            if (start >= end) start = end;
        }
        
        return chunks;
    }

    // Find sentence boundary for better chunking
    private int findSentenceBoundary(String text, int start, int end) {
        // Look for sentence ending punctuation followed by space or newline
        for (int i = end - 1; i > start + 100; i--) { // Don't go too far back
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && 
                i + 1 < text.length() && 
                (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
                return i + 1;
            }
        }
        return -1;
    }

    // CSV-aware chunking: splits by rows while preserving headers
    private List<String> splitCsvIntoChunks(String csvContent, int rowsPerChunk) {
        List<String> chunks = new ArrayList<>();
        String[] lines = csvContent.split("\n");
        
        if (lines.length == 0) {
            return chunks;
        }
        
        // First line is typically the header
        String header = lines.length > 0 ? lines[0] : "";
        
        // Process rows in batches
        for (int i = 1; i < lines.length; i += rowsPerChunk) {
            StringBuilder chunk = new StringBuilder();
            chunk.append(header).append("\n"); // Include header in each chunk
            
            int end = Math.min(i + rowsPerChunk, lines.length);
            for (int j = i; j < end; j++) {
                if (!lines[j].trim().isEmpty()) {
                    chunk.append(lines[j]).append("\n");
                }
            }
            
            String chunkStr = chunk.toString().trim();
            if (!chunkStr.isEmpty() && !chunkStr.equals(header)) {
                chunks.add(chunkStr);
            }
        }
        
        // If no rows (only header), add the header as a single chunk
        if (chunks.isEmpty() && !header.isEmpty()) {
            chunks.add(header);
        }
        
        return chunks;
    }

    /**
     * Send a message with thread ID (maintains conversation)
     * POST /api/chat/{threadId}
     * Body: { "message": "Your message here" }
     */
    @PostMapping(value = "/{threadId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chatWithThread(
            @PathVariable String threadId,
            @RequestBody ChatRequest request) {

        String userMessage = request.getMessage();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(getMessage("error.message.required")));
        }

        try {
            // Query ChromaDB for relevant context if requested
            String chromaContext = Boolean.TRUE.equals(request.getUseChromaDB()) 
                ? buildChromaDBContext(userMessage) 
                : "";

            // Build final message with context
            String enhancedMessage = !chromaContext.isEmpty()
                ? chromaContext + "\n" + getMessage("prompt.user.question", userMessage)
                : userMessage;

            AIChatService.ChatResult result = chatService.chat(threadId, enhancedMessage);
            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }



    /**
     * Get conversation history for a thread
     * GET /api/chat/{threadId}/history
     */
    @GetMapping("/{threadId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String threadId) {
        try {
            List<AIChatProvider.ChatMessage> history = chatService.getConversationHistory(threadId);

            Map<String, Object> response = new HashMap<>();
            response.put("threadId", threadId);
            response.put("messageCount", history.size());
            response.put("messages", history);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Clear conversation history for a thread
     * DELETE /api/chat/{threadId}
     */
    @DeleteMapping("/{threadId}")
    public ResponseEntity<Map<String, Object>> clearThread(@PathVariable String threadId) {
        try {
            chatService.clearConversation(threadId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", getMessage("success.conversation.cleared"));
            response.put("threadId", threadId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * List all active conversation threads
     * GET /api/chat/threads
     */
    @GetMapping("/threads")
    public ResponseEntity<Map<String, Object>> listThreads() {
        try {
            List<String> threads = chatService.getActiveConversations();

            Map<String, Object> response = new HashMap<>();
            response.put("count", threads.size());
            response.put("threads", threads);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Send a message with folder context
     * POST /api/chat/with-context
     * Body: { "message": "Your message", "contextFolders": ["/path/to/folder1",
     * "/path/to/folder2"], "threadId": "optional-thread-id" }
     */
    @PostMapping("/with-context")
    public ResponseEntity<Map<String, Object>> chatWithFolderContext(@RequestBody FolderContextRequest request) {
        String message = request.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(getMessage("error.message.required")));
        }

        try {
            // Generate context from both folders and files
            String folderContext = folderContextService.generateContext(
                    request.getContextFolders(),
                    request.getContextFiles());

            // Combine context with user message
            String enhancedMessage = folderContext + "\n\n" + getMessage("prompt.user.question", message);

            // Use thread ID if provided, otherwise create new conversation
            AIChatService.ChatResult result;
            if (request.getThreadId() != null && !request.getThreadId().trim().isEmpty()) {
                result = chatService.chat(request.getThreadId(), enhancedMessage);
            } else {
                result = chatService.chat(enhancedMessage);
            }

            return ResponseEntity.ok(createSuccessResponse(result.getResponse(), result.getThreadId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse(getMessage("error.internal", e.getMessage())));
        }
    }

    /**
     * Get localized message with parameters
     */
    private String getMessage(String key, Object... params) {
        return messageSource.getMessage(key, params, LocaleContextHolder.getLocale());
    }

    private Map<String, Object> createSuccessResponse(String message, String threadId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("response", message);
        if (threadId != null) {
            response.put("threadId", threadId);
        }
        return response;
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }

}
