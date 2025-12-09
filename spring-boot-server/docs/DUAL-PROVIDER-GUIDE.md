# 🤖 Guía de Implementación: Soporte Dual Ollama + OpenAI

## 🎯 Objetivo
Implementar un sistema RAG flexible que funcione con **Ollama** (local) y **OpenAI** (cloud), permitiendo cambiar entre proveedores mediante configuración.

---

## 📐 Arquitectura Propuesta

```
┌─────────────────────────────────────────────────────┐
│                  ChatController                     │
│  (REST API - recibe preguntas de usuarios)         │
└─────────────────┬───────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────┐
│              EmbeddingService                       │
│  (Unified interface - selecciona provider)         │
└───────┬─────────────────────────────────────┬───────┘
        │                                     │
        ▼                                     ▼
┌──────────────────┐              ┌──────────────────┐
│ OllamaEmbedding  │              │ OpenAIEmbedding  │
│    Service       │              │    Service       │
└──────────────────┘              └──────────────────┘
        │                                     │
        └──────────────┬──────────────────────┘
                       ▼
          ┌────────────────────────┐
          │     ChromaDBService    │
          │  (Vector store común)  │
          └────────────────────────┘
```

---

## 🛠️ Implementación Paso a Paso

### Paso 1: Crear Interfaz Común para Embeddings

```java
// src/main/java/com/indrard/dbmcp/service/ai/EmbeddingProvider.java
package com.indrard.dbmcp.service.ai;

import java.util.List;

/**
 * Common interface for embedding providers (Ollama, OpenAI, etc.)
 */
public interface EmbeddingProvider {
    
    /**
     * Generate embedding vector for a single text
     */
    List<Double> generateEmbedding(String text);
    
    /**
     * Generate embeddings for multiple texts
     */
    List<List<Double>> generateEmbeddings(List<String> texts);
    
    /**
     * Get the dimension of embeddings produced by this provider
     */
    int getEmbeddingDimension();
    
    /**
     * Get the name of the model being used
     */
    String getModelName();
    
    /**
     * Check if the provider is available and working
     */
    boolean isAvailable();
}
```

### Paso 2: Adaptar OllamaEmbeddingService

```java
// Modificar: src/main/java/com/indrard/dbmcp/service/OllamaEmbeddingService.java
package com.indrard.dbmcp.service;

import com.indrard.dbmcp.service.ai.EmbeddingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingService implements EmbeddingProvider {
    
    // ... código existente ...
    
    @Override
    public int getEmbeddingDimension() {
        // mxbai-embed-large: 1024
        // nomic-embed-text: 768
        if ("mxbai-embed-large".equals(embeddingModel)) {
            return 1024;
        } else if ("nomic-embed-text".equals(embeddingModel)) {
            return 768;
        }
        return 768; // default
    }
    
    @Override
    public String getModelName() {
        return embeddingModel;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            String url = ollamaUrl + "/api/tags";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
```

### Paso 3: Crear OpenAIEmbeddingService

```java
// src/main/java/com/indrard/dbmcp/service/OpenAIEmbeddingService.java
package com.indrard.dbmcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indrard.dbmcp.service.ai.EmbeddingProvider;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAIEmbeddingService implements EmbeddingProvider {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/embeddings";
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Double> generateEmbedding(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", text
            );

            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response";
                    throw new IOException("OpenAI API error: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                
                if (data == null || data.isEmpty()) {
                    throw new IOException("No embedding data in response");
                }
                
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                
                return embedding;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OpenAI embedding: " + e.getMessage(), e);
        }
    }

    @Override
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        System.out.println("🔄 Generating OpenAI embeddings for " + texts.size() + " texts...");
        
        // OpenAI supports batch embeddings - more efficient
        try {
            Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", texts
            );

            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("OpenAI API error: " + response.code());
                }

                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                
                // Extract embeddings maintaining order
                List<List<Double>> embeddings = new ArrayList<>();
                for (Map<String, Object> item : data) {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) item.get("embedding");
                    embeddings.add(embedding);
                }
                
                System.out.println("✅ Generated " + embeddings.size() + " OpenAI embeddings");
                return embeddings;
            }

        } catch (Exception e) {
            System.err.println("❌ Batch embedding failed, falling back to individual requests");
            return texts.stream()
                .map(this::generateEmbedding)
                .collect(Collectors.toList());
        }
    }

    @Override
    public int getEmbeddingDimension() {
        // text-embedding-3-small: 1536
        // text-embedding-3-large: 3072
        // text-embedding-ada-002: 1536
        if ("text-embedding-3-large".equals(embeddingModel)) {
            return 3072;
        }
        return 1536; // default for small and ada-002
    }

    @Override
    public String getModelName() {
        return embeddingModel;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
```

### Paso 4: Crear EmbeddingService Unificado

```java
// src/main/java/com/indrard/dbmcp/service/EmbeddingService.java
package com.indrard.dbmcp.service;

import com.indrard.dbmcp.service.ai.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Unified service for embeddings - delegates to the configured provider
 */
@Service
public class EmbeddingService implements EmbeddingProvider {

    @Value("${ai.provider:ollama}")
    private String provider;

    @Autowired(required = false)
    private OllamaEmbeddingService ollamaService;

    @Autowired(required = false)
    private OpenAIEmbeddingService openAIService;

    private EmbeddingProvider activeProvider;

    @PostConstruct
    public void initialize() {
        if ("openai".equalsIgnoreCase(provider)) {
            if (openAIService == null || !openAIService.isAvailable()) {
                throw new IllegalStateException("OpenAI provider selected but not available. Check API key.");
            }
            activeProvider = openAIService;
            System.out.println("✅ Using OpenAI embeddings: " + activeProvider.getModelName());
        } else {
            if (ollamaService == null || !ollamaService.isAvailable()) {
                throw new IllegalStateException("Ollama provider selected but not available. Is Ollama running?");
            }
            activeProvider = ollamaService;
            System.out.println("✅ Using Ollama embeddings: " + activeProvider.getModelName());
        }
        
        System.out.println("   Embedding dimension: " + activeProvider.getEmbeddingDimension());
    }

    @Override
    public List<Double> generateEmbedding(String text) {
        return activeProvider.generateEmbedding(text);
    }

    @Override
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        return activeProvider.generateEmbeddings(texts);
    }

    @Override
    public int getEmbeddingDimension() {
        return activeProvider.getEmbeddingDimension();
    }

    @Override
    public String getModelName() {
        return activeProvider.getModelName();
    }

    @Override
    public boolean isAvailable() {
        return activeProvider != null && activeProvider.isAvailable();
    }
    
    /**
     * Get the name of the active provider
     */
    public String getProviderName() {
        return provider;
    }
}
```

### Paso 5: Actualizar ChromaDBService

```java
// Modificar: src/main/java/com/indrard/dbmcp/service/ChromaDBService.java
// Cambiar línea 32:

@Autowired
private EmbeddingService embeddingService; // Antes: OllamaEmbeddingService

// Todo lo demás permanece igual
```

### Paso 6: Configuración en application.properties

```properties
# ============================================
# AI Provider Configuration
# ============================================
# Choose: openai or ollama
ai.provider=ollama

# ============================================
# Ollama Configuration (when ai.provider=ollama)
# ============================================
ollama.api.url=http://localhost:11434
ollama.model=llama3.2
ollama.temperature=0.7

# Ollama Embeddings
ollama.embedding.model=mxbai-embed-large
# Alternatives:
# - nomic-embed-text (faster, 768 dim)
# - mxbai-embed-large (better quality, 1024 dim)

# ============================================
# OpenAI Configuration (when ai.provider=openai)
# ============================================
# Get your API key from: https://platform.openai.com/api-keys
openai.api.key=${OPENAI_API_KEY:your-api-key-here}

# OpenAI Embeddings
openai.embedding.model=text-embedding-3-small
# Alternatives:
# - text-embedding-3-small (1536 dim, $0.00002/1K tokens)
# - text-embedding-3-large (3072 dim, $0.00013/1K tokens)
# - text-embedding-ada-002 (1536 dim, legacy)

# OpenAI Chat (if using for responses)
openai.chat.model=gpt-4
openai.temperature=0.7

# ============================================
# ChromaDB Configuration
# ============================================
chroma.url=http://localhost:8000
chroma.collection.name=knowledge_base
chroma.tenant=default_tenant
chroma.database=default_database
```

### Paso 7: Variables de Entorno (Recomendado)

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="sk-proj-xxxxxxxxxxxxx"
$env:AI_PROVIDER="openai"

# Linux/Mac
export OPENAI_API_KEY="sk-proj-xxxxxxxxxxxxx"
export AI_PROVIDER="openai"
```

O crear archivo `.env`:
```properties
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxx
AI_PROVIDER=openai
```

---

## 🧪 Testing

### Test 1: Verificar Proveedor Activo
```bash
# Agregar endpoint en ChatController o crear InfoController
GET http://localhost:8080/api/info/embeddings

Response:
{
  "provider": "ollama",
  "model": "mxbai-embed-large",
  "dimension": 1024,
  "available": true
}
```

### Test 2: Subir Documento con Ollama
```bash
# Configurar ai.provider=ollama
POST http://localhost:8080/api/chat/upload
Content-Type: multipart/form-data

{
  "message": "Analiza este documento",
  "useChromaDB": true,
  "files": [documento.pdf]
}
```

### Test 3: Subir Documento con OpenAI
```bash
# Configurar ai.provider=openai y OPENAI_API_KEY
POST http://localhost:8080/api/chat/upload
Content-Type: multipart/form-data

{
  "message": "Analiza este documento",
  "useChromaDB": true,
  "files": [documento.pdf]
}
```

### Test 4: Cambiar de Proveedor
```bash
# 1. Reiniciar con nuevo proveedor
# 2. Resetear ChromaDB (dimensiones diferentes)
POST http://localhost:8080/api/chromadb/reset

# 3. Re-indexar documentos
POST http://localhost:8080/api/chat/upload
```

---

## 📊 Comparación: Ollama vs OpenAI

| Aspecto | Ollama (Local) | OpenAI (Cloud) |
|---------|----------------|----------------|
| **Costo** | Gratis (hardware local) | Pay-per-use (~$0.02/1K tokens) |
| **Privacidad** | Total (local) | Datos en cloud |
| **Velocidad** | Depende de hardware | Rápido (optimizado) |
| **Modelos** | Limitados (open source) | Múltiples (propietarios) |
| **Calidad** | Buena | Excelente |
| **Setup** | Requiere instalación | Solo API key |
| **Escalabilidad** | Limitada por hardware | Infinita |
| **Dimensiones** | 768-1024 | 1536-3072 |

---

## 💡 Casos de Uso Recomendados

### Usar Ollama cuando:
- ✅ Datos sensibles (médicos, financieros, legales)
- ✅ Sin acceso a internet
- ✅ Alto volumen de consultas (costo predecible)
- ✅ Prototipado y desarrollo
- ✅ Requisitos de privacidad estrictos

### Usar OpenAI cuando:
- ✅ Máxima calidad de embeddings
- ✅ Sin hardware potente disponible
- ✅ Necesitas escalabilidad instantánea
- ✅ Prototipado rápido (sin instalar Ollama)
- ✅ Volumen bajo-medio de consultas

---

## 🎯 Estrategia Híbrida (Recomendado)

```properties
# Desarrollo: Ollama (gratis, privado)
ai.provider=ollama

# Producción: OpenAI (mejor calidad)
ai.provider=openai

# Staging: Combinar según necesidad
# - Embeddings: Ollama (más barato)
# - Chat: OpenAI (mejor calidad)
```

---

## 🔐 Seguridad: Manejo de API Keys

### NO hacer:
```properties
# ❌ NUNCA hardcodear API keys
openai.api.key=sk-proj-xxxxxxxxxxxxx
```

### SÍ hacer:
```properties
# ✅ Usar variables de entorno
openai.api.key=${OPENAI_API_KEY}
```

### Producción:
```bash
# Usar secrets manager (AWS, Azure, GCP)
# o variables de entorno del contenedor
docker run -e OPENAI_API_KEY=$OPENAI_API_KEY myapp
```

---

## 📈 Monitoreo y Costos (OpenAI)

### Agregar logging de uso:
```java
@Service
public class OpenAIEmbeddingService implements EmbeddingProvider {
    
    private int totalTokensUsed = 0;
    
    @Override
    public List<Double> generateEmbedding(String text) {
        // ... código existente ...
        
        // Estimar tokens (aproximado)
        int tokens = text.length() / 4;
        totalTokensUsed += tokens;
        
        // Log cada 10K tokens
        if (totalTokensUsed % 10000 < tokens) {
            double cost = (totalTokensUsed / 1000.0) * 0.00002; // $0.02 per 1K
            System.out.println("💰 OpenAI tokens used: " + totalTokensUsed + 
                             " (~$" + String.format("%.4f", cost) + ")");
        }
        
        return embedding;
    }
}
```

---

## 🚀 Siguientes Pasos

1. **Implementar el código** paso a paso
2. **Probar con Ollama** primero (más fácil)
3. **Configurar OpenAI** y probar
4. **Comparar resultados** entre proveedores
5. **Optimizar chunking** según resultados
6. **Implementar caché** para embeddings repetidos
7. **Agregar métricas** de uso y costo

---

## 📚 Referencias

- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)
- [Ollama Embedding Models](https://ollama.ai/library?type=embedding)
- [ChromaDB Documentation](https://docs.trychroma.com/)
- [Spring Boot Conditional Beans](https://www.baeldung.com/spring-conditionalonproperty)

---

**Última actualización**: 8 de diciembre de 2025  
**Estado**: Listo para implementación
