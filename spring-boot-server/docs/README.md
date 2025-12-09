# Database MCP Server - Documentación

Este directorio contiene toda la documentación técnica del proyecto.

## 📚 Guías Disponibles

### Funcionalidades Principales

- **[FILE-EXPORT-GUIDE.md](FILE-EXPORT-GUIDE.md)** - Exportación de archivos (PDF, Excel, Word)
  - Exportar conversaciones de chat
  - Exportar consultas SQL a CSV/PDF
  - Exportar tablas completas

### Integraciones y Optimizaciones

- **[CHROMADB-FIXES.md](CHROMADB-FIXES.md)** - Correcciones de ChromaDB para archivos CSV
  - Chunking inteligente (100 filas para CSV, 2000 chars para documentos)
  - Preservación de metadata
  - Mejoras en contexto para AI

- **[DOCUMENT-OPTIMIZATION.md](DOCUMENT-OPTIMIZATION.md)** - Optimización para PDF/Word
  - Chunking adaptativo para documentos
  - Overlap inteligente
  - Contexto específico por tipo de archivo

- **[MULTILINGUAL-SYSTEM.md](MULTILINGUAL-SYSTEM.md)** - Sistema multiidioma (i18n)
  - Soporte para español e inglés
  - Instrucciones localizadas para ChromaDB
  - Detección automática de idioma

### Desarrollo

- **[ADDING-TOOLS-GUIDE.md](ADDING-TOOLS-GUIDE.md)** - Cómo agregar nuevos tools/funciones
  - Patrón para crear tools MCP
  - Ejemplos y best practices

- **[DUAL-PROVIDER-GUIDE.md](DUAL-PROVIDER-GUIDE.md)** - Guía para Ollama + OpenAI
  - Arquitectura para dual provider
  - (Pendiente de implementación)

## 🚀 Quick Start

Para comenzar, revisa las guías en este orden:

1. `ADDING-TOOLS-GUIDE.md` - Entiende cómo funciona el sistema de tools
2. `FILE-EXPORT-GUIDE.md` - Aprende a exportar datos
3. `CHROMADB-FIXES.md` - Comprende cómo funciona el RAG con archivos

## 📝 Notas

Todas las funcionalidades están implementadas y probadas excepto:
- Dual provider (Ollama + OpenAI simultáneos) - documentado pero no implementado
