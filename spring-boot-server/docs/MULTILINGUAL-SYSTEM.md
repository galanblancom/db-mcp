# 🌍 Sistema Multiidioma para ChromaDB y RAG

## 🎯 Resumen

Tu proyecto ahora soporta **múltiples idiomas de forma automática** en todas las instrucciones y mensajes que se envían al AI cuando trabaja con ChromaDB. El sistema detecta el idioma del usuario y adapta todas las respuestas y contextos accordingly.

---

## 🏗️ Arquitectura Multiidioma

```
Usuario (Cualquier idioma)
    ↓
HTTP Request con header Accept-Language
    ↓
Spring LocaleResolver
    ↓ (detecta idioma)
    ↓
┌──────────────────────────────────────┐
│      MessageSource (i18n)            │
│  - messages.properties (EN)          │
│  - messages_es.properties (ES)       │
│  - messages_fr.properties (FR) etc.  │
└──────────────────────────────────────┘
    ↓
ChatController.buildChromaDBContext()
    ↓ (construye contexto localizado)
    ↓
ChromaDB Search Results + Instrucciones Localizadas
    ↓
AI recibe contexto en el idioma correcto
    ↓
Respuesta en el idioma del usuario
```

---

## ✅ Mejoras Implementadas

### 1. **Mensajes Localizados para ChromaDB**

Antes (hardcoded en inglés):
```java
contextBuilder.append("TOTAL ROWS: " + totalRows + " (data rows, excluding header)\n");
```

Ahora (multiidioma automático):
```java
contextBuilder.append(getMessage("context.total.rows", totalRows)).append("\n");
```

### 2. **Nuevas Claves de Mensaje**

#### messages.properties (English)
```properties
context.file.summary=FILE SUMMARY
context.total.rows=TOTAL ROWS: {0} (data rows, excluding header)
context.instructions=IMPORTANT INSTRUCTIONS
context.instruction.exact.rows=This file has EXACTLY {0} data rows
context.instruction.no.count=DO NOT count the chunks/fragments shown below
context.instruction.no.sum=DO NOT sum chunk numbers or indexes
context.instruction.use.metadata=When asked about row count, ALWAYS use: {0}
context.instruction.fragments=The fragments below are for CONTENT ANALYSIS only
context.chunk=Chunk
```

#### messages_es.properties (Español)
```properties
context.file.summary=RESUMEN DEL ARCHIVO
context.total.rows=TOTAL DE FILAS: {0} (filas de datos, excluyendo encabezado)
context.instructions=INSTRUCCIONES IMPORTANTES
context.instruction.exact.rows=Este archivo tiene EXACTAMENTE {0} filas de datos
context.instruction.no.count=NO cuentes los fragmentos/chunks mostrados abajo
context.instruction.no.sum=NO sumes números de chunks o índices
context.instruction.use.metadata=Cuando pregunten por el conteo de filas, USA SIEMPRE: {0}
context.instruction.fragments=Los fragmentos de abajo son SOLO para ANÁLISIS DE CONTENIDO
context.chunk=Fragmento
```

### 3. **Detección Automática de Idioma**

El sistema usa tres métodos para detectar el idioma:

1. **Accept-Language header** (automático desde navegador)
2. **Query parameter** `?lang=es` (override manual)
3. **Default configurado** en `application.properties`

---

## 📋 Ejemplo de Uso

### Escenario 1: Usuario en Español

```bash
# Request con header español
POST http://localhost:8080/api/chat
Accept-Language: es
Content-Type: application/json

{
  "message": "¿Cuántas filas tiene el archivo?",
  "useChromaDB": true
}
```

**Contexto enviado al AI:**
```
═══════════════════════════════════════════
📊 RESUMEN DEL ARCHIVO: datos.csv
═══════════════════════════════════════════
TOTAL DE FILAS: 450 (filas de datos, excluyendo encabezado)

INSTRUCCIONES IMPORTANTES:
- Este archivo tiene EXACTAMENTE 450 filas de datos
- NO cuentes los fragmentos/chunks mostrados abajo
- NO sumes números de chunks o índices
- Cuando pregunten por el conteo de filas, USA SIEMPRE: 450
- Los fragmentos de abajo son SOLO para ANÁLISIS DE CONTENIDO
═══════════════════════════════════════════

--- Coincidencia 1 (95.2% relevante) ---
Fuente: datos.csv (Fragmento 1/5 - CSV)

[contenido del chunk...]
```

### Escenario 2: Usuario en Inglés

```bash
# Request con header inglés
POST http://localhost:8080/api/chat
Accept-Language: en
Content-Type: application/json

{
  "message": "How many rows does the file have?",
  "useChromaDB": true
}
```

**Contexto enviado al AI:**
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

--- Match 1 (95.2% relevant) ---
Source: data.csv (Chunk 1/5 - CSV)

[chunk content...]
```

### Escenario 3: Forzar Idioma con Query Parameter

```bash
# Forzar español aunque el navegador esté en inglés
POST http://localhost:8080/api/chat?lang=es
Accept-Language: en
Content-Type: application/json

{
  "message": "¿Cuántas filas tiene el archivo?",
  "useChromaDB": true
}
```

---

## 🔧 Configuración

### application.properties

```properties
# Default language when no Accept-Language header is present
app.default.language=en

# Available options: en, es, fr, de, etc.
# The system will use the Accept-Language header if present
# Otherwise falls back to this default
```

### Agregar Nuevos Idiomas

#### 1. Crear archivo de mensajes
```bash
# Ejemplo: Agregar francés
touch src/main/resources/messages_fr.properties
```

#### 2. Traducir todas las claves
```properties
# messages_fr.properties
context.file.summary=RÉSUMÉ DU FICHIER
context.total.rows=TOTAL DES LIGNES: {0} (lignes de données, en-tête exclu)
context.instructions=INSTRUCTIONS IMPORTANTES
context.instruction.exact.rows=Ce fichier a EXACTEMENT {0} lignes de données
context.instruction.no.count=NE comptez PAS les fragments/chunks affichés ci-dessous
context.instruction.no.sum=NE faites PAS la somme des numéros de chunks
context.instruction.use.metadata=Lorsqu'on demande le nombre de lignes, UTILISEZ TOUJOURS: {0}
context.instruction.fragments=Les fragments ci-dessous sont UNIQUEMENT pour l'ANALYSE DE CONTENU
context.chunk=Fragment
```

#### 3. Reiniciar aplicación
```bash
mvn spring-boot:run
```

#### 4. Probar
```bash
curl -H "Accept-Language: fr" http://localhost:8080/api/chat
```

---

## 🧪 Testing Multiidioma

### Test 1: Detectar Idioma Automáticamente

```bash
# Español
curl -X POST http://localhost:8080/api/chat \
  -H "Accept-Language: es" \
  -H "Content-Type: application/json" \
  -d '{"message": "¿Cuántas filas?", "useChromaDB": true}'

# Inglés
curl -X POST http://localhost:8080/api/chat \
  -H "Accept-Language: en" \
  -H "Content-Type: application/json" \
  -d '{"message": "How many rows?", "useChromaDB": true}'
```

### Test 2: Override con Query Parameter

```bash
# Forzar español
curl -X POST "http://localhost:8080/api/chat?lang=es" \
  -H "Content-Type: application/json" \
  -d '{"message": "How many rows?", "useChromaDB": true}'
```

### Test 3: Verificar Contexto Localizado

Agregar logging temporal en `ChatController.java`:

```java
private String buildChromaDBContext(String userMessage) {
    // ... código existente ...
    
    String context = contextBuilder.toString();
    System.out.println("=== CONTEXTO GENERADO (idioma: " + 
        LocaleContextHolder.getLocale().getLanguage() + ") ===");
    System.out.println(context);
    System.out.println("=== FIN CONTEXTO ===");
    
    return context;
}
```

---

## 🌐 Idiomas Soportados

### Ya Implementados
- ✅ **English (en)** - messages.properties
- ✅ **Español (es)** - messages_es.properties

### Fáciles de Agregar
- 🔜 **Français (fr)** - messages_fr.properties
- 🔜 **Deutsch (de)** - messages_de.properties
- 🔜 **Português (pt)** - messages_pt.properties
- 🔜 **Italiano (it)** - messages_it.properties
- 🔜 **中文 (zh)** - messages_zh.properties
- 🔜 **日本語 (ja)** - messages_ja.properties

---

## 📊 Ventajas del Sistema Multiidioma

### 1. **Consistencia**
- Todos los mensajes en el idioma correcto
- No mezcla de idiomas en el contexto del AI

### 2. **Mantenibilidad**
- Cambios en un solo lugar (properties)
- No código hardcodeado
- Fácil agregar idiomas

### 3. **Experiencia del Usuario**
- Detección automática del idioma
- Respuestas naturales en su idioma
- Override manual cuando sea necesario

### 4. **Mejor Precisión del AI**
- Instrucciones claras en el idioma del usuario
- Mejor comprensión del contexto
- Respuestas más precisas

---

## 🔍 Cómo Funciona Internamente

### Flujo Completo

```java
1. Usuario envía request
   ↓
2. Spring intercepta Accept-Language header
   LocaleContextHolder.setLocale(Locale.forLanguageTag("es"))
   ↓
3. ChatController.buildChromaDBContext() ejecuta
   ↓
4. Cada getMessage("key", params) llama a:
   messageSource.getMessage("key", params, LocaleContextHolder.getLocale())
   ↓
5. MessageSource busca en:
   - messages_es.properties (si locale = es)
   - messages.properties (fallback)
   ↓
6. Retorna mensaje localizado
   ↓
7. Contexto completo se construye en el idioma correcto
   ↓
8. Se envía al AI con instrucciones localizadas
   ↓
9. AI responde en el idioma del contexto
```

### Código Ejemplo

```java
// Antes (hardcoded)
contextBuilder.append("TOTAL ROWS: " + totalRows + "\n");

// Ahora (multiidioma)
String localizedMessage = getMessage("context.total.rows", totalRows);
contextBuilder.append(localizedMessage).append("\n");

// getMessage() internamente hace:
messageSource.getMessage(
    "context.total.rows",           // key
    new Object[]{totalRows},        // params
    LocaleContextHolder.getLocale() // es, en, fr, etc.
);
```

---

## 🎓 Mejores Prácticas

### ✅ Hacer
- Usar `getMessage("key", params)` para TODOS los mensajes
- Mantener claves descriptivas y organizadas
- Probar con múltiples idiomas
- Incluir contexto en las claves (context.*, error.*, success.*)

### ❌ No Hacer
- Hardcodear texto en el código
- Mezclar idiomas en el mismo contexto
- Olvidar traducir claves nuevas en todos los idiomas
- Usar claves genéricas (msg1, msg2, etc.)

---

## 🚀 Roadmap Futuro

### Corto Plazo
- [ ] Agregar francés y alemán
- [ ] Crear endpoint para listar idiomas disponibles
- [ ] Tests automatizados multiidioma

### Mediano Plazo
- [ ] Sistema de traducción automática de mensajes
- [ ] UI para gestionar traducciones
- [ ] Soporte para idiomas RTL (árabe, hebreo)

### Largo Plazo
- [ ] Detección de idioma desde el contenido del mensaje
- [ ] Traducción automática de respuestas del AI
- [ ] Personalización de mensajes por usuario

---

## 📞 Ejemplo de Integración Frontend

### JavaScript/TypeScript

```typescript
// Detectar idioma del navegador
const userLanguage = navigator.language.split('-')[0]; // 'es', 'en', etc.

// Opción 1: Usar Accept-Language header (recomendado)
fetch('http://localhost:8080/api/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Accept-Language': userLanguage
  },
  body: JSON.stringify({
    message: '¿Cuántas filas tiene?',
    useChromaDB: true
  })
});

// Opción 2: Usar query parameter
fetch(`http://localhost:8080/api/chat?lang=${userLanguage}`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    message: '¿Cuántas filas tiene?',
    useChromaDB: true
  })
});
```

### React Hook Ejemplo

```typescript
import { useState, useEffect } from 'react';

function useUserLanguage() {
  const [language, setLanguage] = useState('en');
  
  useEffect(() => {
    const browserLang = navigator.language.split('-')[0];
    setLanguage(browserLang);
  }, []);
  
  return language;
}

function ChatComponent() {
  const language = useUserLanguage();
  
  const sendMessage = async (message: string) => {
    const response = await fetch('http://localhost:8080/api/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept-Language': language
      },
      body: JSON.stringify({ message, useChromaDB: true })
    });
    
    return response.json();
  };
  
  // ...
}
```

---

## 📚 Referencias

- [Spring i18n Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource)
- [Java Locale](https://docs.oracle.com/javase/8/docs/api/java/util/Locale.html)
- [Accept-Language Header](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language)
- [ResourceBundleMessageSource](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/support/ResourceBundleMessageSource.html)

---

**Estado**: ✅ Implementado y funcionando  
**Versión**: 1.0.0  
**Última actualización**: 8 de diciembre de 2025
