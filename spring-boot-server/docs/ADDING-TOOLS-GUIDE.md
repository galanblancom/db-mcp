# Guía Completa: Cómo Agregar un Nuevo Tool al Proyecto

Esta guía explica paso a paso cómo agregar una nueva función (tool) que estará disponible para que la IA la llame.

## 📋 Tabla de Contenidos

1. [Arquitectura del Sistema](#arquitectura-del-sistema)
2. [Pasos para Agregar un Tool](#pasos-para-agregar-un-tool)
3. [Ejemplo Completo](#ejemplo-completo)
4. [Tipos de Parámetros Soportados](#tipos-de-parámetros-soportados)
5. [Mejores Prácticas](#mejores-prácticas)
6. [Solución de Problemas](#solución-de-problemas)

---

## 🏗️ Arquitectura del Sistema

El sistema utiliza un enfoque basado en **anotaciones** para definir tools:

```
┌─────────────────────────────────────────────┐
│         ToolsConfiguration.java              │
│  Define los tools con @ToolDefinition        │
│  (Configuración centralizada)                │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│    AnnotationBasedFunctionProvider.java     │
│  Descubre y ejecuta métodos anotados        │
│  (Auto-discovery)                            │
└────────────────┬────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────┐
│         McpToolService.java                  │
│  Implementa la lógica de negocio            │
│  (Acceso a base de datos)                   │
└─────────────────────────────────────────────┘
```

**Archivos que necesitas modificar:**
1. ✅ `McpToolService.java` - Implementación de la lógica
2. ✅ `ToolsConfiguration.java` - Declaración del tool con anotaciones

---

## 🛠️ Pasos para Agregar un Tool

### Paso 1: Agregar el método en `McpToolService.java`

Ubicación: `src/main/java/com/indrard/dbmcp/service/McpToolService.java`

```java
@Tool(description = "Descripción breve del tool")
public TipoRetorno nombreDelMetodo(TipoParam1 param1, TipoParam2 param2) throws Exception {
    // 1. Validar parámetros si es necesario
    if (param1 == null || param1.isEmpty()) {
        throw new IllegalArgumentException("param1 is required");
    }
    
    // 2. Implementar la lógica de negocio
    // Puede usar: databaseService, queryLogger, uptimeTracker, queryTemplates
    
    // 3. Retornar el resultado
    return resultado;
}
```

**Ejemplo real:**
```java
@Tool(description = "Get user information by email address")
public UserInfo getUserByEmail(String email) throws Exception {
    if (email == null || email.trim().isEmpty()) {
        throw new IllegalArgumentException("Email is required");
    }
    
    String query = "SELECT * FROM users WHERE email = '" + email + "'";
    QueryResult result = databaseService.executeQuery(query, 1, false);
    
    // Procesar y retornar el resultado
    return new UserInfo(result);
}
```

---

### Paso 2: Declarar el tool en `ToolsConfiguration.java`

Ubicación: `src/main/java/com/indrard/dbmcp/config/ToolsConfiguration.java`

Agrega el método con las anotaciones correspondientes:

```java
@ToolDefinition(
    name = "nombreDelTool",
    description = "Descripción detallada del tool que la IA leerá para decidir cuándo usarlo",
    priority = 100
)
public TipoRetorno nombreDelTool(
    @ToolParameter(name = "param1", description = "Descripción del parámetro 1", required = true, type = "string") TipoParam1 param1,
    @ToolParameter(name = "param2", description = "Descripción del parámetro 2", type = "integer") TipoParam2 param2
) throws Exception {
    return mcpToolService.nombreDelMetodo(param1, param2);
}
```

**Ejemplo real:**
```java
@ToolDefinition(
    name = "getUserByEmail",
    description = "Retrieves user information by email address. Returns user details including name, registration date, and status.",
    priority = 100
)
public UserInfo getUserByEmail(
    @ToolParameter(name = "email", description = "Email address of the user to retrieve", required = true, type = "string") String email
) throws Exception {
    return mcpToolService.getUserByEmail(email);
}
```

---

## 📝 Ejemplo Completo

Vamos a agregar un tool que obtiene pedidos pendientes por cliente.

### 1. En `McpToolService.java`:

```java
@Tool(description = "Get pending orders for a customer by customer ID")
public List<Order> getPendingOrders(String customerId, Integer maxOrders) throws Exception {
    if (customerId == null || customerId.trim().isEmpty()) {
        throw new IllegalArgumentException("Customer ID is required");
    }
    
    int limit = maxOrders != null ? maxOrders : 100;
    
    String query = "SELECT order_id, order_date, total_amount, status " +
                   "FROM orders " +
                   "WHERE customer_id = '" + customerId + "' " +
                   "AND status = 'PENDING' " +
                   "ORDER BY order_date DESC";
    
    QueryResult result = databaseService.executeQuery(query, limit, false);
    
    // Convertir QueryResult a List<Order>
    List<Order> orders = new ArrayList<>();
    for (Map<String, Object> row : result.getRows()) {
        Order order = new Order();
        order.setOrderId((String) row.get("ORDER_ID"));
        order.setOrderDate((Date) row.get("ORDER_DATE"));
        order.setTotalAmount((BigDecimal) row.get("TOTAL_AMOUNT"));
        order.setStatus((String) row.get("STATUS"));
        orders.add(order);
    }
    
    return orders;
}
```

### 2. En `ToolsConfiguration.java`:

```java
@ToolDefinition(
    name = "getPendingOrders",
    description = "Retrieves pending orders for a specific customer. Returns order details including order ID, date, amount, and status. Present orders in chronological order, highlighting the most recent ones.",
    priority = 100
)
public List<Order> getPendingOrders(
    @ToolParameter(name = "customerId", description = "Customer ID to query pending orders", required = true, type = "string") String customerId,
    @ToolParameter(name = "maxOrders", description = "Maximum number of orders to return (default: 100)", type = "integer") Integer maxOrders
) throws Exception {
    return mcpToolService.getPendingOrders(customerId, maxOrders);
}
```

### 3. ¡Listo! El tool ya está disponible

El sistema automáticamente:
- ✅ Descubre el nuevo método anotado
- ✅ Lo registra como función disponible para la IA
- ✅ Valida los parámetros requeridos
- ✅ Maneja conversiones de tipo automáticas

---

## 🔤 Tipos de Parámetros Soportados

### Tipos Básicos

| Tipo Java | type en @ToolParameter | Ejemplo JSON |
|-----------|------------------------|--------------|
| `String` | `"string"` | `"valor"` |
| `Integer` | `"integer"` | `123` |
| `Long` | `"integer"` | `123` |
| `Boolean` | `"boolean"` | `true` |
| `Double` | `"number"` | `123.45` |

### Tipos Complejos

| Tipo Java | type en @ToolParameter | Ejemplo JSON |
|-----------|------------------------|--------------|
| `List<String>` | `"array"` | `["item1", "item2"]` |
| `Map<String, String>` | `"object"` | `{"key": "value"}` |

### Ejemplos de Parámetros:

```java
// String
@ToolParameter(name = "tableName", description = "Name of the table", required = true, type = "string") 
String tableName

// Integer
@ToolParameter(name = "maxRows", description = "Maximum rows to return", type = "integer") 
Integer maxRows

// Boolean
@ToolParameter(name = "includeDeleted", description = "Include deleted records", type = "boolean") 
Boolean includeDeleted

// Array de Strings
@ToolParameter(name = "customerIds", description = "List of customer IDs", required = true, type = "array") 
List<String> customerIds

// Object (clave-valor)
@ToolParameter(name = "filters", description = "Filter conditions", type = "object") 
Map<String, String> filters
```

---

## ✅ Mejores Prácticas

### 1. Naming Convention (Nombres)

**✅ CORRECTO:**
```java
@ToolParameter(name = "customerId", ...) String customerId  // camelCase
@ToolParameter(name = "maxRows", ...) Integer maxRows       // camelCase
@ToolParameter(name = "isActive", ...) Boolean isActive     // camelCase
```

**❌ INCORRECTO:**
```java
@ToolParameter(name = "customer_id", ...) String customerId  // snake_case
@ToolParameter(name = "CustomerID", ...) String customerId   // PascalCase
@ToolParameter(name = "MAX_ROWS", ...) Integer maxRows       // UPPER_CASE
```

**IMPORTANTE:** Usa siempre **camelCase** porque el sistema está configurado para instruir a la IA que use este formato.

### 2. Descripciones Claras

Las descripciones son **críticas** porque la IA las lee para decidir cuándo usar tu tool.

**✅ CORRECTO:**
```java
@ToolDefinition(
    name = "getInvoicesToPayByContract",
    description = "Get invoices to pay by contract. Returns pending invoices with due date, invoice number, and debt amount for one or more contract NICs. Present each invoice with its due date, invoice number (SIMBOLO_VAR), and debt amount in a clear, easy-to-read format. Highlight overdue invoices if applicable.",
    priority = 100
)
```

**❌ INCORRECTO:**
```java
@ToolDefinition(
    name = "getInvoices",
    description = "Gets invoices",  // Muy vaga
    priority = 100
)
```

**Tips para descripciones:**
- 📝 Describe QUÉ hace el tool
- 📊 Menciona QUÉ datos retorna
- 🎨 Incluye instrucciones de presentación si es relevante
- ⚠️ Indica casos especiales o advertencias

### 3. Validación de Parámetros

Siempre valida parámetros requeridos en `McpToolService`:

```java
@Tool(description = "...")
public Result doSomething(String id, String name) throws Exception {
    // Validar parámetros obligatorios
    if (id == null || id.trim().isEmpty()) {
        throw new IllegalArgumentException("id is required");
    }
    
    if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("name is required");
    }
    
    // Lógica del método...
}
```

### 4. Manejo de Valores Predeterminados

```java
@Tool(description = "...")
public Result query(Integer maxRows, Boolean includeDeleted) throws Exception {
    // Valores predeterminados
    int limit = maxRows != null ? maxRows : 1000;
    boolean showDeleted = includeDeleted != null ? includeDeleted : false;
    
    // Usar los valores...
}
```

### 5. Manejo de Excepciones

```java
@Tool(description = "...")
public Result doSomething(String id) throws Exception {
    try {
        // Lógica que puede fallar
        return databaseService.executeQuery(query, 100, false);
    } catch (SQLException e) {
        throw new Exception("Error querying database: " + e.getMessage(), e);
    }
}
```

---

## 🐛 Solución de Problemas

### Problema 1: El tool no aparece disponible

**Síntomas:**
- La IA no puede llamar a tu nuevo tool
- No aparece en la lista de funciones

**Soluciones:**
1. ✅ Verifica que agregaste el método en `ToolsConfiguration.java` con `@ToolDefinition`
2. ✅ Verifica que la clase `ToolsConfiguration` tiene `@Component`
3. ✅ Reinicia la aplicación (Spring Boot debe re-escanear las anotaciones)

### Problema 2: Error "argument type mismatch"

**Síntomas:**
```
java.lang.IllegalArgumentException: argument type mismatch
```

**Causas comunes:**
- La IA envía un String y esperas un Integer
- La IA envía `"[\"item1\", \"item2\"]"` (string) en lugar de `["item1", "item2"]` (array)

**Solución:**
El sistema ya maneja conversiones automáticas de String → Integer/Long/Boolean/List. Asegúrate de:
1. Usar el tipo correcto en `@ToolParameter`
2. El tipo Java del parámetro coincide con el tipo declarado

### Problema 3: Parámetro requerido falta

**Síntomas:**
```
IllegalArgumentException: parameterName is required for functionName function
```

**Solución:**
1. ✅ Verifica que `required = true` en `@ToolParameter`
2. ✅ Agrega validación en `McpToolService` si es necesario
3. ✅ La descripción debe indicar claramente que el parámetro es obligatorio

### Problema 4: La IA no formatea el resultado como esperas

**Síntomas:**
- La IA retorna JSON crudo en lugar de una tabla
- La presentación no es clara

**Solución:**
Agrega instrucciones de presentación en la descripción del tool:

```java
@ToolDefinition(
    name = "getCustomers",
    description = "Lists all customers. Present results in a clear table format with columns: ID, Name, Email, Status. Highlight inactive customers.",
    priority = 100
)
```

### Problema 5: NullPointerException al ejecutar

**Síntomas:**
```
NullPointerException in McpToolService
```

**Solución:**
1. ✅ Verifica que `mcpToolService` está inyectado correctamente en el constructor de `ToolsConfiguration`
2. ✅ Valida parámetros antes de usarlos
3. ✅ Usa valores predeterminados para parámetros opcionales

---

## 📚 Checklist Final

Antes de considerar tu tool completo, verifica:

- [ ] ✅ Método agregado en `McpToolService.java` con `@Tool`
- [ ] ✅ Método agregado en `ToolsConfiguration.java` con `@ToolDefinition`
- [ ] ✅ Todos los parámetros tienen `@ToolParameter` con descripciones claras
- [ ] ✅ Parámetros usan **camelCase**
- [ ] ✅ Descripción del tool es detallada y útil para la IA
- [ ] ✅ Validación de parámetros requeridos implementada
- [ ] ✅ Valores predeterminados para parámetros opcionales
- [ ] ✅ Manejo de excepciones apropiado
- [ ] ✅ Aplicación reiniciada para que Spring descubra el nuevo tool
- [ ] ✅ Probado con llamadas reales desde la IA

---

## 🎓 Ejemplo Mínimo (Quick Start)

Si solo quieres copiar y pegar:

### En `McpToolService.java`:
```java
@Tool(description = "Get data by ID")
public String getDataById(String id) throws Exception {
    if (id == null || id.trim().isEmpty()) {
        throw new IllegalArgumentException("id is required");
    }
    
    String query = "SELECT * FROM table WHERE id = '" + id + "'";
    QueryResult result = databaseService.executeQuery(query, 1, false);
    
    return result.toString();
}
```

### En `ToolsConfiguration.java`:
```java
@ToolDefinition(
    name = "getDataById",
    description = "Retrieves data by ID from the table",
    priority = 100
)
public String getDataById(
    @ToolParameter(name = "id", description = "The ID to search for", required = true, type = "string") String id
) throws Exception {
    return mcpToolService.getDataById(id);
}
```

¡Y listo! 🎉

---

## 📞 Soporte

Si encuentras problemas no cubiertos en esta guía:

1. Revisa los logs de la aplicación en la consola
2. Busca excepciones en el stack trace
3. Verifica que los nombres de parámetros coincidan exactamente (case-sensitive)
4. Compara tu código con los ejemplos existentes en `ToolsConfiguration.java`

---

**Última actualización:** Diciembre 2025
**Versión:** 1.0
