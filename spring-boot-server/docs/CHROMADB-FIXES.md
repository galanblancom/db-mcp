# 🔧 Diagnóstico y Correcciones para ChromaDB con Archivos Excel/CSV

## 📋 Problemas Identificados y Resueltos

### ✅ 1. Chunking de CSV Inadecuado
**Problema Original:**
- Solo 20 filas por chunk → Fragmentación excesiva
- Pérdida de contexto semántico
- Embeddings poco informativos

**Solución Aplicada:**
```java
// Antes: 20 filas
splitCsvIntoChunks(fileContent.getContent(), 20)

// Ahora: 100 filas para mejor contexto
splitCsvIntoChunks(fileContent.getContent(), 100)
```

### ✅ 2. Metadata Incompleta en Chunks
**Problema Original:**
- `total_rows` no se preservaba en todos los chunks
- Faltaba tipo de dato (`data_type`)
- El AI no podía acceder a información crítica

**Solución Aplicada:**
```java
// Agregado en cada chunk
if (fileContent.getTotalRows() != null) {
    metadata.put("total_rows", String.valueOf(fileContent.getTotalRows()));
    metadata.put("data_type", "csv");
}
```

### ✅ 3. Instrucciones Confusas para el AI
**Problema Original:**
- Mezcla español/inglés
- Instrucciones redundantes
- Formato difícil de leer

**Solución Aplicada:**
- Formato limpio y claro
- Solo en inglés para consistencia
- Instrucciones precisas sobre uso de metadata

### ✅ 4. Tamaño de Chunks de Texto Pequeño
**Problema Original:**
- 1000 caracteres → Fragmentación excesiva para documentos

**Solución Aplicada:**
```java
// Ahora: 1500 caracteres
splitIntoChunks(fileContent.getContent(), 1500)
```

---

## 🚀 Mejoras Implementadas

### 1. **Chunking Optimizado**
- **CSV**: 100 filas por chunk (antes: 20)
- **Texto**: 1500 caracteres (antes: 1000)
- Mejor preservación de contexto semántico

### 2. **Metadata Enriquecida**
Cada chunk ahora incluye:
```json
{
  "filename": "data.csv",
  "extension": "csv",
  "chunk": "1",
  "total_chunks": "5",
  "total_rows": "450",      // ← CRÍTICO para conteos
  "data_type": "csv",       // ← Nuevo
  "uploaded_at": "..."
}
```

### 3. **Instrucciones Claras para el AI**
```
═══════════════════════════════════════════
📊 FILE SUMMARY: data.csv
═══════════════════════════════════════════
TOTAL ROWS: 450 (data rows, excluding header)

IMPORTANT INSTRUCTIONS:
- This file has EXACTLY 450 data rows
- DO NOT count the chunks/fragments shown below
- DO NOT sum chunk numbers or indexes
- When asked about row count, ALWAYS use: 450
- The fragments below are for CONTENT ANALYSIS only
═══════════════════════════════════════════
```

### 4. **Identificación de Consultas Agregadas**
```java
boolean isAggregateQuery = userMessage.toLowerCase()
    .matches(".*(resumen|summary|resume|todo|all|cuant|how many|count|total).*");
int numResults = isAggregateQuery ? 15 : 8;
```

---

## 📊 Flujo Mejorado para Archivos CSV/Excel

```
1. SUBIR ARCHIVO
   ↓
2. FileProcessingService.extractContent()
   - Lee CSV/Excel
   - Cuenta total de filas (excluyendo header)
   - Retorna FileContent con totalRows
   ↓
3. CHUNKING INTELIGENTE
   - 100 filas por chunk (CSV)
   - Preserva header en cada chunk
   - Metadata completa en cada fragmento
   ↓
4. GENERACIÓN DE EMBEDDINGS
   - OllamaEmbeddingService (o OpenAI)
   - Embeddings por cada chunk
   ↓
5. ALMACENAMIENTO EN CHROMADB
   - Embeddings + documentos + metadata
   - total_rows en TODOS los chunks
   ↓
6. BÚSQUEDA SEMÁNTICA
   - Query del usuario → embedding
   - Búsqueda por similitud
   - Recupera chunks relevantes + metadata
   ↓
7. CONSTRUCCIÓN DE CONTEXTO
   - Muestra total_rows al inicio
   - Instrucciones claras para el AI
   - Chunks como referencia de contenido
   ↓
8. RESPUESTA DEL AI
   - Usa total_rows para conteos
   - Analiza contenido de chunks
   - Respuesta precisa
```

---

## 🧪 Cómo Probar las Correcciones

### Test 1: Subir CSV y Hacer Pregunta sobre Conteo
```bash
# 1. Subir archivo CSV con ChromaDB
POST http://localhost:8080/api/chat/upload
Content-Type: multipart/form-data

{
  "message": "¿Cuántas filas tiene el archivo?",
  "useChromaDB": true,
  "files": [archivo.csv]
}

# Respuesta esperada: Número exacto de filas (no suma de chunks)
```

### Test 2: Pregunta sobre Contenido Específico
```bash
POST http://localhost:8080/api/chat
Content-Type: application/json

{
  "message": "¿Cuántos productos tienen precio mayor a 100?",
  "useChromaDB": true
}

# El AI debe analizar el contenido y contar correctamente
```

### Test 3: Verificar Metadata
```bash
# Ver stats de ChromaDB
GET http://localhost:8080/api/chromadb/stats

# Ver colección directamente
curl http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections/folder_context/count
```

---

## 🛠️ Configuración Recomendada

### application.properties
```properties
# Usar el modelo de embeddings correcto
ollama.embedding.model=mxbai-embed-large

# ChromaDB
chroma.url=http://localhost:8000
chroma.collection.name=folder_context
chroma.tenant=default_tenant
chroma.database=default_database
```

### Modelos Ollama Recomendados
```bash
# Para embeddings (mejor precisión)
ollama pull mxbai-embed-large

# Alternativa ligera
ollama pull nomic-embed-text

# Para chat (mejor con datos estructurados)
ollama pull llama3.2
```

---

## 🐛 Problemas Conocidos y Soluciones

### Problema: "Dimension mismatch"
**Causa**: Cambiaste de modelo de embeddings con datos existentes

**Solución**:
```bash
# Resetear colección
POST http://localhost:8080/api/chromadb/reset

# O eliminar y recrear
docker restart <chromadb-container>
```

### Problema: Embeddings muy lentos
**Causa**: Modelo de embeddings pesado o chunks muy grandes

**Solución**:
1. Usar `nomic-embed-text` (más rápido)
2. Reducir tamaño de chunks si es necesario
3. Verificar que Ollama tenga suficiente RAM

### Problema: Respuestas incorrectas sobre conteos
**Causa**: Metadata no se preservó correctamente

**Verificación**:
```java
// En ChatController, agregar log
System.out.println("Metadata: " + result.getMetadata());
```

**Debe mostrar**: `{total_rows=450, data_type=csv, ...}`

---

## 📈 Próximas Mejoras Recomendadas

### 1. Soporte para OpenAI Embeddings
```java
@Service
public class EmbeddingService {
    @Autowired
    private OllamaEmbeddingService ollamaService;
    
    @Value("${ai.provider:ollama}")
    private String provider;
    
    public List<Double> generateEmbedding(String text) {
        if ("openai".equals(provider)) {
            return generateOpenAIEmbedding(text);
        }
        return ollamaService.generateEmbedding(text);
    }
}
```

### 2. Chunking Semántico Avanzado
- Detectar secciones en documentos
- Preservar estructura de tablas
- Overlap inteligente entre chunks

### 3. Caché de Embeddings
- Evitar regenerar embeddings idénticos
- Redis o base de datos para persistencia

### 4. Índice para Word/PDF
- Extraer secciones/capítulos
- Metadata estructurada por página
- Búsqueda por sección

---

## 🎯 Comparación: Antes vs Ahora

| Aspecto | Antes ❌ | Ahora ✅ |
|---------|---------|---------|
| **Chunking CSV** | 20 filas | 100 filas |
| **Chunking Texto** | 1000 chars | 1500 chars |
| **Metadata total_rows** | Solo en primer chunk | En TODOS los chunks |
| **Tipo de dato** | No especificado | `data_type: csv` |
| **Instrucciones AI** | Confusas (ES/EN) | Claras (EN) |
| **Búsqueda agregada** | 10 resultados fijo | 15 para agregados, 8 normal |
| **Presentación chunks** | "Sample fragment" | Con tipo de dato |

---

## ✅ Checklist de Verificación

- [x] Chunking aumentado a 100 filas (CSV)
- [x] Metadata `total_rows` en todos los chunks
- [x] Metadata `data_type` agregada
- [x] Instrucciones AI simplificadas
- [x] Detección de consultas agregadas
- [x] Presentación de resultados mejorada
- [ ] Tests de integración (recomendado)
- [ ] Soporte OpenAI embeddings (futuro)
- [ ] Caché de embeddings (futuro)

---

## 📞 Soporte y Debugging

### Ver logs de ChromaDB
```bash
# Logs del contenedor
docker logs <chromadb-container>

# Stats
curl http://localhost:8000/api/v2/tenants/default_tenant/databases/default_database/collections/folder_context/count
```

### Ver logs de Ollama
```bash
# Windows
Get-Content "$env:LOCALAPPDATA\Ollama\logs\server.log" -Tail 50

# Ver embeddings generados
# Agregar log en OllamaEmbeddingService.java
System.out.println("Generated embedding dimension: " + embedding.size());
```

---

## 🎓 Recursos Adicionales

- [ChromaDB Documentation](https://docs.trychroma.com/)
- [Ollama Models](https://ollama.ai/library)
- [RAG Best Practices](https://www.pinecone.io/learn/retrieval-augmented-generation/)
- [Chunking Strategies](https://www.pinecone.io/learn/chunking-strategies/)

---

**Última actualización**: 8 de diciembre de 2025  
**Versión del proyecto**: 0.0.1-SNAPSHOT
