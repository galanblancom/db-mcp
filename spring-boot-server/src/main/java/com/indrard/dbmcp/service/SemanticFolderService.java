package com.indrard.dbmcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
public class SemanticFolderService {

    @Autowired
    private ChromaDBService chromaDBService;

    @Autowired(required = false)
    private FolderContextService folderContextService;

    /**
     * Index all files in a folder into ChromaDB for semantic search
     */
    public String indexFolder(String folderPath) {
        try {
            Path path = Paths.get(folderPath);
            
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return "❌ Invalid folder path: " + folderPath;
            }

            List<String> ids = new ArrayList<>();
            List<String> documents = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (shouldIndexFile(file) && attrs.size() < 100_000) { // Max 100KB per file
                        try {
                            String content = Files.readString(file);
                            String relativePath = path.relativize(file).toString();
                            
                            ids.add(UUID.randomUUID().toString());
                            documents.add(content);
                            
                            Map<String, String> metadata = new HashMap<>();
                            metadata.put("filepath", relativePath);
                            metadata.put("filename", file.getFileName().toString());
                            metadata.put("extension", getFileExtension(file));
                            metadata.put("size", String.valueOf(attrs.size()));
                            metadata.put("folder", folderPath);
                            metadatas.add(metadata);
                            
                        } catch (IOException e) {
                            System.err.println("Failed to read file: " + file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldIgnoreDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (ids.isEmpty()) {
                return "⚠️ No files found to index in: " + folderPath;
            }

            chromaDBService.addDocuments(ids, documents, metadatas);

            return String.format("✅ Indexed %d files from folder: %s", ids.size(), folderPath);

        } catch (Exception e) {
            return "❌ Error indexing folder: " + e.getMessage();
        }
    }

    /**
     * Search for files semantically similar to a query
     */
    public String searchFiles(String query, int limit) {
        try {
            List<ChromaDBService.SearchResult> results = chromaDBService.search(query, limit);

            if (results.isEmpty()) {
                return "No results found for: " + query;
            }

            StringBuilder response = new StringBuilder();
            response.append("=== SEMANTIC SEARCH RESULTS ===\n\n");
            response.append(String.format("Query: \"%s\"\n", query));
            response.append(String.format("Found %d relevant files:\n\n", results.size()));

            for (int i = 0; i < results.size(); i++) {
                ChromaDBService.SearchResult result = results.get(i);
                Map<String, String> metadata = result.getMetadata();
                String content = result.getDocument();

                response.append(String.format("%d. 📄 %s (similarity: %.1f%%)\n",
                    i + 1,
                    metadata.get("filepath"),
                    result.getSimilarity() * 100));
                
                response.append(String.format("   Folder: %s\n", metadata.get("folder")));
                response.append(String.format("   Size: %s bytes\n", metadata.get("size")));
                
                // Show preview
                String preview = content.length() > 300
                    ? content.substring(0, 300) + "..."
                    : content;
                response.append(String.format("   Preview:\n   %s\n\n",
                    preview.replace("\n", "\n   ")));
            }

            response.append("=== END RESULTS ===\n");
            return response.toString();

        } catch (Exception e) {
            return "❌ Search error: " + e.getMessage();
        }
    }

    /**
     * Get collection statistics
     */
    public String getIndexStats() {
        Map<String, Object> stats = chromaDBService.getStats();
        
        StringBuilder response = new StringBuilder();
        response.append("=== VECTOR STORE STATISTICS ===\n\n");
        response.append(String.format("Collection: %s\n", stats.get("collection_name")));
        response.append(String.format("Documents indexed: %s\n", stats.get("document_count")));
        response.append(String.format("Status: %s\n", stats.get("status")));
        
        return response.toString();
    }

    /**
     * Clear the vector store
     */
    public String clearIndex() {
        try {
            chromaDBService.clearCollection();
            return "✅ Vector store cleared successfully";
        } catch (Exception e) {
            return "❌ Error clearing index: " + e.getMessage();
        }
    }

    private boolean shouldIndexFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        
        if (fileName.startsWith(".")) {
            return false;
        }

        List<String> indexableExtensions = List.of(
            ".java", ".xml", ".properties", ".md", ".txt",
            ".sql", ".json", ".yaml", ".yml", ".conf"
        );

        return indexableExtensions.stream().anyMatch(fileName::endsWith);
    }

    private boolean shouldIgnoreDirectory(Path dir) {
        String dirName = dir.getFileName().toString();
        
        List<String> ignoredDirs = List.of(
            ".git", ".svn", "node_modules", "target", "build",
            ".idea", ".vscode", "__pycache__", "bin", "obj"
        );

        return ignoredDirs.contains(dirName) || dirName.startsWith(".");
    }

    private String getFileExtension(Path file) {
        String name = file.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }
}
