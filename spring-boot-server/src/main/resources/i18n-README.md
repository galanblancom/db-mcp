# Internationalization (i18n) Guide

## Overview
This application supports internationalization using Spring Boot's MessageSource. Messages are externalized in properties files and can be displayed in different languages based on the user's locale.

## Supported Languages
- **English** (default) - `messages.properties`
- **Spanish** - `messages_es.properties`

## How to Use

### As a Client (HTTP Requests)

#### Option 1: Using Accept-Language Header
Add the `Accept-Language` header to your HTTP requests:

```bash
# English (default)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello"}'

# Spanish
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{"message":"Hola"}'
```

#### Option 2: Using Query Parameter
Add `?lang=es` to the URL:

```bash
# Spanish using query parameter
curl -X POST "http://localhost:8080/api/chat?lang=es" \
  -H "Content-Type: application/json" \
  -d '{"message":"Hola"}'
```

### As a Developer

#### Adding New Messages
1. Add the key-value pair to `messages.properties` (English):
```properties
error.custom.message=This is a custom error message
success.operation.complete=Operation completed successfully with {0} items
```

2. Add the translation to `messages_es.properties` (Spanish):
```properties
error.custom.message=Este es un mensaje de error personalizado
success.operation.complete=Operación completada exitosamente con {0} elementos
```

#### Using Messages in Code
In any Spring-managed component with `MessageSource` injected:

```java
@Autowired
private MessageSource messageSource;

// Simple message
String msg = messageSource.getMessage("error.custom.message", null, LocaleContextHolder.getLocale());

// Message with parameters
String msg = messageSource.getMessage("success.operation.complete", 
    new Object[]{count}, 
    LocaleContextHolder.getLocale());
```

#### Helper Method in ChatController
The `ChatController` has a helper method for convenience:

```java
private String getMessage(String key, Object... params) {
    return messageSource.getMessage(key, params, LocaleContextHolder.getLocale());
}

// Usage
String errorMsg = getMessage("error.message.required");
String successMsg = getMessage("success.files.processed", fileCount);
```

## Message Categories

### Error Messages
- `error.message.required` - Validation error for missing message
- `error.internal` - Generic internal error (takes exception message as parameter)
- `error.chromadb.search` - ChromaDB search error (takes exception message)
- `error.chromadb.not.initialized` - ChromaDB not initialized
- `error.chromadb.not.available` - ChromaDB not available

### Success Messages
- `success.conversation.cleared` - Conversation cleared successfully
- `success.files.processed` - Files processed (takes file count as parameter)
- `success.indexed.chromadb` - Files indexed in ChromaDB

### Context Headers
- `context.file.contents` - Header for file contents section
- `context.relevant.content` - Header for relevant content from search
- `context.end` - End of context marker
- `context.match` - Match label in search results
- `context.source` - Source file label
- `context.chunk.part` - Chunk part label
- `context.relevant.percent` - Relevance percentage (takes formatted percentage)

### User Prompts
- `prompt.user.question` - User question label

### Thread Messages
- `thread.count` - Thread count (takes number)
- `thread.message.count` - Message count in thread (takes number)

## Adding a New Language

To add support for a new language (e.g., French):

1. Create `messages_fr.properties` in `src/main/resources/`
2. Copy all keys from `messages.properties`
3. Translate all values to French
4. The language will be automatically available

Example `messages_fr.properties`:
```properties
error.message.required=Le message est requis
error.internal=Erreur: {0}
success.conversation.cleared=Conversation effacée
# ... etc
```

## Testing

### Test with Browser
1. Change browser language preferences to Spanish
2. Make requests - responses will be in Spanish

### Test with Postman
1. Add header: `Accept-Language: es`
2. Or add query parameter: `?lang=es`

### Test Default Fallback
If a translation is missing, Spring will automatically fall back to the default (English) messages.

## Configuration

The i18n configuration is defined in `I18nConfig.java`:
- Default locale: English
- Encoding: UTF-8
- Fallback: Enabled (falls back to default if translation missing)
- Query parameter: `lang` (e.g., `?lang=es`)

## Notes

- Locale resolution is thread-safe using `LocaleContextHolder`
- Messages support placeholders: `{0}`, `{1}`, etc. using `MessageFormat`
- The `Accept-Language` header takes precedence over the query parameter
- If no locale is specified, English (default) is used
