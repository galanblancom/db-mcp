# Guía de Exportación de Archivos

## 📋 Descripción General

Tu aplicación ahora puede **generar y exportar archivos** en múltiples formatos:
- **PDF** - Reportes de conversaciones y resultados de consultas
- **Word (DOCX)** - Documentos de conversaciones
- **CSV/Excel** - Datos tabulares para análisis

## 🎯 Nuevas Capacidades

### ✅ Lo que se agregó:

1. **FileGenerationService** - Servicio para generación de archivos
2. **FileExportController** - Endpoints REST para descarga
3. **Dependencias**:
   - `iText 8.0.2` - Generación de PDF
   - `Apache Commons CSV 1.10.0` - Generación de CSV
   - `Apache POI` (ya existente) - Generación de Word

## 📡 Endpoints Disponibles

### 🎯 NUEVOS: Exportación Directa desde Base de Datos

#### 1A. Exportar Tabla Completa a CSV/Excel
```http
GET /api/export/table-to-csv?table={tableName}&schema={schema}&maxRows={maxRows}
```

**Parámetros:**
- `table` (obligatorio) - Nombre de la tabla
- `schema` (opcional) - Schema/propietario de la tabla
- `maxRows` (opcional, default: 10000) - Máximo de filas a exportar

**Respuesta:** CSV descargable con todos los datos de la tabla

**Ejemplo:**
```bash
# Exportar tabla 'users'
curl -X GET "http://localhost:8080/api/export/table-to-csv?table=users&maxRows=5000" \
  -o users.csv

# Exportar tabla con schema
curl -X GET "http://localhost:8080/api/export/table-to-csv?table=customers&schema=sales" \
  -o customers.csv
```

---

#### 1B. Ejecutar Query y Exportar a CSV
```http
POST /api/export/query-to-csv
Content-Type: application/json

{
  "sql": "SELECT * FROM users WHERE active = true",
  "title": "active_users",
  "maxRows": 10000
}
```

**Body:**
- `sql` (obligatorio) - Query SQL (solo SELECT)
- `title` (opcional) - Nombre del archivo
- `maxRows` (opcional, default: 10000) - Límite de filas

**Respuesta:** CSV con resultados de la consulta

**Ejemplo:**
```bash
curl -X POST "http://localhost:8080/api/export/query-to-csv" \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT id, name, email FROM users WHERE created_at > '\''2024-01-01'\''",
    "title": "new_users_2024",
    "maxRows": 5000
  }' \
  -o new_users_2024.csv
```

---

#### 1C. Ejecutar Query y Exportar a PDF
```http
POST /api/export/query-to-pdf
Content-Type: application/json

{
  "sql": "SELECT * FROM sales ORDER BY amount DESC LIMIT 100",
  "title": "Top 100 Sales Report",
  "maxRows": 1000
}
```

**Body:**
- `sql` (obligatorio) - Query SQL (solo SELECT)
- `title` (opcional) - Título del reporte
- `maxRows` (opcional, default: 1000) - Límite de filas (PDFs grandes pueden ser lentos)

**Respuesta:** PDF con resultados formateados

**Ejemplo:**
```bash
curl -X POST "http://localhost:8080/api/export/query-to-pdf" \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{
    "sql": "SELECT product, SUM(quantity) as total FROM orders GROUP BY product",
    "title": "Resumen de Ventas por Producto"
  }' \
  -o ventas_por_producto.pdf
```

---

### 📝 Otros Endpoints de Exportación

### 1. Exportar Conversación a PDF
```http
GET /api/export/chat/pdf?threadId={threadId}&title={title}
```

**Parámetros:**
- `threadId` (obligatorio) - ID del thread de conversación
- `title` (opcional) - Título del reporte

**Respuesta:** PDF descargable con toda la conversación

**Ejemplo con curl:**
```bash
curl -X GET "http://localhost:8080/api/export/chat/pdf?threadId=abc123&title=Mi%20Conversacion" \
  -H "Accept-Language: es" \
  -o conversacion.pdf
```

---

### 2. Exportar Conversación a Word
```http
GET /api/export/chat/word?threadId={threadId}&title={title}
```

**Parámetros:**
- `threadId` (obligatorio) - ID del thread de conversación
- `title` (opcional) - Título del documento

**Respuesta:** Documento Word (.docx) descargable

**Ejemplo con curl:**
```bash
curl -X GET "http://localhost:8080/api/export/chat/word?threadId=abc123" \
  -H "Accept-Language: en" \
  -o conversation.docx
```

---

### 3. Exportar Datos a CSV/Excel
```http
POST /api/export/csv
Content-Type: application/json

{
  "title": "Sales Report",
  "headers": ["Product", "Quantity", "Price"],
  "rows": [
    ["Laptop", 10, 1200.50],
    ["Mouse", 50, 25.99],
    ["Keyboard", 30, 75.00]
  ]
}
```

**Respuesta:** CSV descargable (compatible con Excel, con separador `;` y BOM UTF-8)

**Ejemplo con curl:**
```bash
curl -X POST "http://localhost:8080/api/export/csv" \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{
    "title": "ventas_2024",
    "headers": ["Producto", "Cantidad", "Precio"],
    "rows": [
      ["Laptop", 10, 1200.50],
      ["Mouse", 50, 25.99]
    ]
  }' \
  -o ventas.csv
```

---

### 4. Exportar Resultados de Consulta a PDF
```http
POST /api/export/query/pdf
Content-Type: application/json

{
  "title": "Database Query Results",
  "headers": ["ID", "Name", "Email"],
  "rows": [
    [1, "John Doe", "john@example.com"],
    [2, "Jane Smith", "jane@example.com"]
  ]
}
```

**Respuesta:** PDF con resultados formateados

**Ejemplo con curl:**
```bash
curl -X POST "http://localhost:8080/api/export/query/pdf" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "User List",
    "headers": ["ID", "Name", "Email"],
    "rows": [
      [1, "Alice", "alice@test.com"],
      [2, "Bob", "bob@test.com"]
    ]
  }' \
  -o users.pdf
```

## 🌍 Soporte Multiidioma

Todos los endpoints respetan el header `Accept-Language`:
- `Accept-Language: en` → Textos en inglés
- `Accept-Language: es` → Textos en español

**Ejemplo:**
```bash
curl -X GET "http://localhost:8080/api/export/chat/pdf?threadId=abc123" \
  -H "Accept-Language: es" \
  -o reporte.pdf
```

## 🔧 Integración con Frontend

### JavaScript/TypeScript Examples

```javascript
// 1. Exportar tabla completa a CSV
async function exportTableToCsv(tableName, schema = null, maxRows = 10000) {
  const params = new URLSearchParams({ 
    table: tableName,
    maxRows: maxRows.toString()
  });
  if (schema) params.append('schema', schema);
  
  const response = await fetch(`/api/export/table-to-csv?${params}`);
  
  if (response.ok) {
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${tableName}.csv`;
    a.click();
  }
}

// 2. Ejecutar query y exportar a CSV
async function exportQueryToCsv(sql, title = 'export') {
  const response = await fetch('/api/export/query-to-csv', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': navigator.language.split('-')[0]
    },
    body: JSON.stringify({ 
      sql: sql,
      title: title,
      maxRows: 10000
    })
  });
  
  if (response.ok) {
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title}.csv`;
    a.click();
  }
}

// 3. Ejecutar query y exportar a PDF
async function exportQueryToPdf(sql, title = 'Query Results') {
  const response = await fetch('/api/export/query-to-pdf', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': 'es'
    },
    body: JSON.stringify({ 
      sql: sql,
      title: title,
      maxRows: 1000
    })
  });
  
  if (response.ok) {
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title}.pdf`;
    a.click();
  }
}

// Ejemplos de uso
exportTableToCsv('users', null, 5000);
exportQueryToCsv('SELECT * FROM orders WHERE status = "pending"', 'pending_orders');
exportQueryToPdf('SELECT product, COUNT(*) as total FROM sales GROUP BY product', 'Ventas por Producto');

// 4. Exportar conversación a PDF
async function exportChatToPdf(threadId, title = null) {
  const params = new URLSearchParams({ threadId });
  if (title) params.append('title', title);
  
  const response = await fetch(`/api/export/chat/pdf?${params}`, {
    headers: {
      'Accept-Language': navigator.language.split('-')[0] // 'en' o 'es'
    }
  });
  
  if (response.ok) {
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `chat_${threadId}.pdf`;
    a.click();
  }
}

// Exportar datos a CSV
async function exportDataToCsv(title, headers, rows) {
  const response = await fetch('/api/export/csv', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': 'es'
    },
    body: JSON.stringify({ title, headers, rows })
  });
  
  if (response.ok) {
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title}.csv`;
    a.click();
  }
}

// Ejemplo de uso
exportChatToPdf('thread-123', 'Mi Conversación');

exportDataToCsv(
  'ventas_enero',
  ['Producto', 'Cantidad', 'Total'],
  [
    ['Laptop', 5, 6000],
    ['Mouse', 20, 500]
  ]
);
```

## 📊 Características Especiales

### CSV Compatible con Excel
- **Separador:** `;` (punto y coma) en lugar de `,`
- **Codificación:** UTF-8 con BOM para reconocimiento automático en Excel
- **Formato:** RFC4180 con comillas automáticas cuando necesario

### PDF con Formato Profesional
- Título centrado en negrita
- Fecha de generación
- Formato de conversación con roles (Usuario/Asistente/Sistema)
- Pie de página con conteo de filas (en reportes de datos)

### Word con Estructura Clara
- Títulos con formato destacado
- Roles en negrita
- Contenido formateado y legible

## 🧪 Testing

### Probar Exportación de Base de Datos

```bash
# 1. Exportar tabla completa
curl -X GET "http://localhost:8080/api/export/table-to-csv?table=users&maxRows=100" \
  -o users_export.csv

# 2. Consulta SQL personalizada a CSV
curl -X POST "http://localhost:8080/api/export/query-to-csv" \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{
    "sql": "SELECT id, name, email FROM users WHERE active = true LIMIT 50",
    "title": "usuarios_activos",
    "maxRows": 50
  }' \
  -o usuarios_activos.csv

# 3. Consulta SQL a PDF
curl -X POST "http://localhost:8080/api/export/query-to-pdf" \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{
    "sql": "SELECT category, COUNT(*) as total FROM products GROUP BY category",
    "title": "Productos por Categoría"
  }' \
  -o productos_por_categoria.pdf

# 4. Con schema específico
curl -X GET "http://localhost:8080/api/export/table-to-csv?table=orders&schema=sales&maxRows=1000" \
  -o sales_orders.csv
```

### Probar con Chat Existente
```bash
# 1. Crear una conversación
curl -X POST "http://localhost:8080/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, how are you?", "threadId": "test-123"}'

# 2. Exportar a PDF
curl -X GET "http://localhost:8080/api/export/chat/pdf?threadId=test-123" \
  -H "Accept-Language: en" \
  -o test_chat.pdf

# 3. Exportar a Word
curl -X GET "http://localhost:8080/api/export/chat/word?threadId=test-123" \
  -o test_chat.docx
```

### Probar Exportación de Datos
```bash
# Exportar CSV
curl -X POST "http://localhost:8080/api/export/csv" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "test_data",
    "headers": ["Name", "Age", "City"],
    "rows": [
      ["Alice", 30, "Madrid"],
      ["Bob", 25, "Barcelona"],
      ["Carol", 35, "Valencia"]
    ]
  }' \
  -o test_data.csv

# Exportar PDF de consulta
curl -X POST "http://localhost:8080/api/export/query/pdf" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Users Report",
    "headers": ["ID", "Username"],
    "rows": [[1, "admin"], [2, "user"]]
  }' \
  -o query_results.pdf
```

## ⚙️ Configuración

No requiere configuración adicional. Los servicios se activan automáticamente al iniciar la aplicación.

## 🚀 Compilar y Ejecutar

```bash
# Compilar con las nuevas dependencias
mvn clean install

# Ejecutar
mvn spring-boot:run
```

## 📝 Notas Importantes

1. **Tamaño de Archivos:** Los endpoints están diseñados para conversaciones y datasets medianos. Para datasets muy grandes (>10,000 filas), considera implementar streaming o paginación.

2. **Seguridad:** En producción, considera agregar:
   - Autenticación/autorización para los endpoints de exportación
   - Límites de rate limiting
   - Validación de tamaño de datos

3. **Performance:** La generación de PDF es síncrona. Para reportes grandes, considera:
   - Implementar generación asíncrona con jobs
   - Notificar al usuario cuando el reporte esté listo

4. **Formatos Adicionales:** Si necesitas otros formatos (JSON, XML, etc.), el patrón es extensible en `FileGenerationService`.

## 🎨 Personalización

### Cambiar Estilo de PDF
Edita `FileGenerationService.generatePdfReport()` para personalizar:
- Fuentes (`.setFont()`)
- Colores (`.setFontColor()`)
- Márgenes (Document constructor)
- Encabezados/pies de página

### Agregar Nuevos Formatos
```java
// En FileGenerationService.java
public byte[] generateJsonExport(List<Map<String, String>> messages) {
    // Tu implementación
}

// En FileExportController.java
@GetMapping("/chat/json")
public ResponseEntity<String> exportChatToJson(@RequestParam String threadId) {
    // Tu implementación
}
```

## ✅ Resumen de Archivos Modificados

1. ✅ `pom.xml` - Dependencias: iText, Commons CSV
2. ✅ `FileGenerationService.java` - Servicio de generación
3. ✅ `FileExportController.java` - Endpoints REST
4. ✅ `messages.properties` - Textos en inglés
5. ✅ `messages_es.properties` - Textos en español

**Estado:** ✅ Listo para usar
