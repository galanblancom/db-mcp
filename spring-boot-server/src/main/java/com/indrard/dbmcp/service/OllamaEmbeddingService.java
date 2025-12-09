package com.indrard.dbmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OllamaEmbeddingService {

    @Value("${ollama.api.url}")
    private String ollamaUrl;

    @Value("${ollama.embedding.model:nomic-embed-text}")
    private String embeddingModel;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Maximum text length for embeddings (reduced to be safer with nomic-embed-text)
    private static final int MAX_EMBEDDING_LENGTH = 8000;  // ~2000 tokens, much safer
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 2000;
    
    // Fallback model if primary fails
    private static final String FALLBACK_MODEL = "mxbai-embed-large";
    private boolean useFallbackModel = false;

    /**
     * Generate embeddings for a single text using Ollama
     */
    public List<Double> generateEmbedding(String text) {
        // Validate and clean text
        String processedText = cleanText(text);
        
        if (processedText.isEmpty()) {
            System.err.println("⚠️  Empty text after cleaning, using placeholder");
            processedText = "Empty document";
        }
        
        // Truncate if too long
        if (processedText.length() > MAX_EMBEDDING_LENGTH) {
            System.out.println("⚠️  Text too long (" + processedText.length() + " chars), truncating to " + MAX_EMBEDDING_LENGTH);
            processedText = processedText.substring(0, MAX_EMBEDDING_LENGTH);
        }
        
        return generateEmbeddingWithRetry(processedText, 0);
    }
    
    /**
     * Clean text to prevent embedding failures
     */
    private String cleanText(String text) {
        if (text == null) return "";
        
        return text
            // Remove null bytes and control characters
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
            // Remove excessive whitespace
            .replaceAll("\\s+", " ")
            // Trim
            .trim();
    }
    
    /**
     * Generate embedding with retry logic
     */
    private List<Double> generateEmbeddingWithRetry(String text, int attempt) {
        try {
            String url = ollamaUrl + "/api/embeddings";
            
            // Use fallback model if primary has failed multiple times
            String modelToUse = useFallbackModel ? FALLBACK_MODEL : embeddingModel;

            Map<String, Object> requestBody = Map.of(
                "model", modelToUse,
                "prompt", text
            );

            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                
                if (!response.isSuccessful()) {
                    String modelInUse = useFallbackModel ? FALLBACK_MODEL : embeddingModel;
                    System.err.println("❌ Ollama embedding error:");
                    System.err.println("   Status: " + response.code() + " - " + response.message());
                    System.err.println("   Model: " + modelInUse);
                    System.err.println("   URL: " + url);
                    System.err.println("   Response: " + responseBody);
                    System.err.println("   Text length: " + text.length() + " chars");
                    System.err.println("   Attempt: " + (attempt + 1) + "/" + (MAX_RETRIES + 1));
                    
                    // Retry on 500 errors (connection failures)
                    if (response.code() == 500 && attempt < MAX_RETRIES) {
                        // Try fallback model if not already using it
                        if (!useFallbackModel && attempt == 0) {
                            System.err.println("⚠️  Switching to fallback model: " + FALLBACK_MODEL);
                            useFallbackModel = true;
                            Thread.sleep(RETRY_DELAY_MS);
                            return generateEmbeddingWithRetry(text, attempt + 1);
                        }
                        
                        System.err.println("⏳ Retrying in " + RETRY_DELAY_MS + "ms...");
                        Thread.sleep(RETRY_DELAY_MS);
                        return generateEmbeddingWithRetry(text, attempt + 1);
                    }
                    
                    System.err.println("\n💡 Possible solutions:");
                    System.err.println("   1. Switch embedding model in application.properties: ollama.embedding.model=" + FALLBACK_MODEL);
                    System.err.println("   2. Check if model '" + embeddingModel + "' is working: ollama run " + embeddingModel);
                    System.err.println("   3. Restart Ollama service: restart the Ollama application");
                    System.err.println("   4. Try reducing text length or switching to OpenAI provider");
                    
                    throw new IOException("Ollama embedding failed (" + response.code() + "): " + responseBody);
                }

                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) result.get("embedding");
                
                if (embedding == null || embedding.isEmpty()) {
                    throw new IOException("No embedding returned from Ollama. Response: " + responseBody);
                }
                
                return embedding;
            }

        } catch (IOException e) {
            // Retry on connection errors
            if (attempt < MAX_RETRIES && (e.getMessage().contains("Connection reset") || 
                                          e.getMessage().contains("forcibly closed") ||
                                          e.getMessage().contains("Connection refused"))) {
                System.err.println("⚠️  Connection error (attempt " + (attempt + 1) + "/" + (MAX_RETRIES + 1) + "): " + e.getMessage());
                System.err.println("⏳ Retrying in " + RETRY_DELAY_MS + "ms...");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                    return generateEmbeddingWithRetry(text, attempt + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Generate embeddings for multiple texts
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        System.out.println("🔄 Generating embeddings for " + texts.size() + " texts...");
        
        try {
            List<List<Double>> embeddings = new java.util.ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                try {
                    String text = texts.get(i);
                    System.out.println("   Processing embedding " + (i + 1) + "/" + texts.size() + 
                                     " (text length: " + text.length() + " chars)");
                    
                    List<Double> embedding = generateEmbedding(text);
                    embeddings.add(embedding);
                    
                } catch (Exception e) {
                    System.err.println("❌ Failed at embedding " + (i + 1) + "/" + texts.size());
                    System.err.println("   Text preview: " + 
                        (texts.get(i).length() > 100 ? texts.get(i).substring(0, 100) + "..." : texts.get(i)));
                    throw new RuntimeException("Failed to generate embedding for text " + (i + 1) + 
                                             " of " + texts.size() + ": " + e.getMessage(), e);
                }
            }
            
            System.out.println("✅ Successfully generated all " + embeddings.size() + " embeddings");
            return embeddings;
            
        } catch (Exception e) {
            System.err.println("❌ Batch embedding generation failed: " + e.getMessage());
            throw e;
        }
    }
}
