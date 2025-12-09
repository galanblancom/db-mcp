package com.indrard.dbmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChromaDBService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    @Value("${chroma.url:http://localhost:8000}")
    private String chromaUrl;

    @Value("${chroma.collection.name:folder_context}")
    private String collectionName;

    @Value("${chroma.tenant:default_tenant}")
    private String tenant;

    @Value("${chroma.database:default_database}")
    private String database;

    @Autowired
    private OllamaEmbeddingService embeddingService;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String collectionId;

    @PostConstruct
    public void initialize() {
        try {
            // Create or get collection via REST API
            collectionId = getOrCreateCollection();
            System.out.println("✅ ChromaDB collection initialized: " + collectionName + " (ID: " + collectionId + ")");
        } catch (Exception e) {
            System.err.println("⚠️ ChromaDB not available: " + e.getMessage());
            System.err.println("   Run: docker run -d -p 8000:8000 chromadb/chroma");
            e.printStackTrace();
        }
    }

    private String getOrCreateCollection() throws IOException {
        // ChromaDB v2 API: Create collection
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", collectionName);
        createBody.put("get_or_create", true);
        createBody.put("metadata", Map.of("description", "Folder context for semantic search"));

        RequestBody body = RequestBody.create(objectMapper.writeValueAsString(createBody), JSON);
        String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections", 
                                    chromaUrl, tenant, database);
        
        Request createRequest = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create collection: " + response.code() + " - " + response.message());
            }
            if (response.body() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                return (String) result.get("id");
            }
        }
        throw new IOException("Failed to get collection ID");
    }
    
    /**
     * Delete the collection (useful when changing embedding models)
     */
    public void deleteCollection() throws IOException {
        String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s",
                                    chromaUrl, tenant, database, collectionName);
        
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("✅ Collection deleted: " + collectionName);
            } else {
                System.err.println("⚠️  Failed to delete collection: " + response.code());
            }
        }
    }
    
    /**
     * Reset collection - delete and recreate
     */
    public void resetCollection() {
        try {
            System.out.println("🔄 Resetting ChromaDB collection...");
            deleteCollection();
            collectionId = getOrCreateCollection();
            System.out.println("✅ Collection reset complete: " + collectionName);
        } catch (Exception e) {
            System.err.println("❌ Failed to reset collection: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Add documents to the vector store with batching for large uploads
     */
    public void addDocuments(List<String> ids, List<String> documents, List<Map<String, String>> metadatas) {
        try {
            if (collectionId == null) {
                System.err.println("⚠️ ChromaDB addDocuments called but collectionId is null!");
                System.err.println("   ChromaDB URL: " + chromaUrl);
                System.err.println("   Collection Name: " + collectionName);
                throw new IllegalStateException("ChromaDB collection not initialized");
            }

            // Batch large uploads to avoid payload size limits (ChromaDB typically handles ~100-200 at a time)
            int batchSize = 100;
            int totalBatches = (int) Math.ceil((double) documents.size() / batchSize);
            
            System.out.println("📥 Adding " + documents.size() + " documents in " + totalBatches + " batch(es)");
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                int start = batchIndex * batchSize;
                int end = Math.min(start + batchSize, documents.size());
                
                List<String> batchIds = ids.subList(start, end);
                List<String> batchDocuments = documents.subList(start, end);
                List<Map<String, String>> batchMetadatas = metadatas.subList(start, end);
                
                System.out.println("  Batch " + (batchIndex + 1) + "/" + totalBatches + ": " + batchDocuments.size() + " documents");
                
                // Generate embeddings for batch
                List<List<Double>> embeddings = embeddingService.generateEmbeddings(batchDocuments);
                
                // Convert to float lists
                List<List<Float>> floatEmbeddings = embeddings.stream()
                    .map(embedding -> embedding.stream()
                        .map(Double::floatValue)
                        .collect(Collectors.toList()))
                    .collect(Collectors.toList());

                // Build request body
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("ids", batchIds);
                requestBody.put("embeddings", floatEmbeddings);
                requestBody.put("metadatas", batchMetadatas);
                requestBody.put("documents", batchDocuments);

                RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
                String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/upsert",
                                            chromaUrl, tenant, database, collectionId);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        
                        // Check for dimension mismatch error
                        if (response.code() == 400 && (errorBody.contains("dimension") || 
                                                       errorBody.contains("embedding"))) {
                            System.err.println("⚠️  Embedding dimension mismatch detected!");
                            System.err.println("   This happens when you change embedding models.");
                            System.err.println("   Resetting collection and retrying...");
                            
                            // Reset collection and retry once
                            resetCollection();
                            
                            // Retry the failed batch
                            try (Response retryResponse = httpClient.newCall(request).execute()) {
                                if (!retryResponse.isSuccessful()) {
                                    throw new IOException("Failed to add documents after reset (batch " + (batchIndex + 1) + "): " + 
                                                        retryResponse.code() + " - " + retryResponse.message());
                                }
                            }
                        } else {
                            throw new IOException("Failed to add documents (batch " + (batchIndex + 1) + "): " + 
                                                response.code() + " - " + response.message() + "\n" + errorBody);
                        }
                    }
                }
            }
            
            System.out.println("✅ Successfully added all " + documents.size() + " documents");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to add documents: " + e.getMessage(), e);
        }
    }

    /**
     * Search for similar documents
     */
    public List<SearchResult> search(String query, int nResults) {
        try {
            if (collectionId == null) {
                throw new IllegalStateException("ChromaDB collection not initialized");
            }

            // Generate embedding for query
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);
            List<Float> floatQueryEmbedding = queryEmbedding.stream()
                .map(Double::floatValue)
                .collect(Collectors.toList());

            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query_embeddings", List.of(floatQueryEmbedding));
            requestBody.put("n_results", nResults);
            requestBody.put("include", List.of("documents", "metadatas", "distances"));

            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
            String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/query",
                                        chromaUrl, tenant, database, collectionId);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to search: " + response.code());
                }
                
                if (response.body() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                    
                    @SuppressWarnings("unchecked")
                    List<List<String>> ids = (List<List<String>>) result.get("ids");
                    @SuppressWarnings("unchecked")
                    List<List<Map<String, Object>>> metadatas = (List<List<Map<String, Object>>>) result.get("metadatas");
                    @SuppressWarnings("unchecked")
                    List<List<String>> documents = (List<List<String>>) result.get("documents");
                    @SuppressWarnings("unchecked")
                    List<List<Number>> distances = (List<List<Number>>) result.get("distances");

                    List<SearchResult> searchResults = new ArrayList<>();
                    if (ids != null && !ids.isEmpty() && ids.get(0) != null) {
                        for (int i = 0; i < ids.get(0).size(); i++) {
                            SearchResult searchResult = new SearchResult();
                            searchResult.setId(ids.get(0).get(i));
                            searchResult.setDocument(documents.get(0).get(i));
                            
                            // Convert metadata to Map<String, String>
                            Map<String, String> metadata = new HashMap<>();
                            if (metadatas != null && !metadatas.isEmpty() && metadatas.get(0).get(i) != null) {
                                metadatas.get(0).get(i).forEach((k, v) -> metadata.put(k, v != null ? v.toString() : null));
                            }
                            searchResult.setMetadata(metadata);
                            
                            float distance = distances.get(0).get(i).floatValue();
                            searchResult.setDistance(distance);
                            searchResult.setSimilarity(1.0f - distance);
                            searchResults.add(searchResult);
                        }
                    }
                    return searchResults;
                }
            }
            return new ArrayList<>();

        } catch (Exception e) {
            throw new RuntimeException("Failed to search: " + e.getMessage(), e);
        }
    }

    /**
     * Delete documents by IDs
     */
    public void deleteDocuments(List<String> ids) {
        try {
            if (collectionId == null) {
                throw new IllegalStateException("ChromaDB collection not initialized");
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", ids);

            RequestBody body = RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON);
            String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/delete",
                                        chromaUrl, tenant, database, collectionId);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to delete documents: " + response.code());
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete documents: " + e.getMessage(), e);
        }
    }

    /**
     * Clear all documents in the collection
     */
    public void clearCollection() {
        try {
            if (collectionId != null) {
                // Delete the collection
                String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s",
                                            chromaUrl, tenant, database, collectionName);
                Request deleteRequest = new Request.Builder()
                        .url(url)
                        .delete()
                        .build();

                try (Response response = httpClient.newCall(deleteRequest).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Failed to delete collection: " + response.code());
                    }
                }
                
                // Recreate it
                initialize();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear collection: " + e.getMessage(), e);
        }
    }

    /**
     * Get collection statistics
     */
    public Map<String, Object> getStats() {
        try {
            if (collectionId == null) {
                return Map.of("status", "not_initialized");
            }

            String url = String.format("%s/api/v2/tenants/%s/databases/%s/collections/%s/count",
                                        chromaUrl, tenant, database, collectionId);
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to get count: " + response.code());
                }
                
                if (response.body() != null) {
                    int count = Integer.parseInt(response.body().string());
                    return Map.of(
                        "collection_name", collectionName,
                        "document_count", count,
                        "status", "active"
                    );
                }
            }
            
            return Map.of("status", "error");
            
        } catch (Exception e) {
            return Map.of(
                "status", "error",
                "error", e.getMessage()
            );
        }
    }

    // Search result model
    public static class SearchResult {
        private String id;
        private String document;
        private Map<String, String> metadata;
        private Float distance;
        private Float similarity;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getDocument() { return document; }
        public void setDocument(String document) { this.document = document; }
        
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        
        public Float getDistance() { return distance; }
        public void setDistance(Float distance) { this.distance = distance; }
        
        public Float getSimilarity() { return similarity; }
        public void setSimilarity(Float similarity) { this.similarity = similarity; }
    }
    
    /**
     * Check if ChromaDB is initialized and ready to use
     */
    public boolean isInitialized() {
        return collectionId != null;
    }
}
