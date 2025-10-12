// Utility functions for the MCP server

// Query history storage
interface QueryLogEntry {
  timestamp: Date;
  sql: string;
  executionTime: number;
  success: boolean;
  error?: string;
}

export class QueryLogger {
  private static enabled = process.env.LOG_QUERIES === 'true';
  private static maxEntries = 100;
  private static logs: QueryLogEntry[] = [];

  static log(sql: string, executionTime: number, success: boolean, error?: string) {
    if (!this.enabled) return;

    this.logs.push({
      timestamp: new Date(),
      sql: sql.substring(0, 500), // Truncate long queries
      executionTime,
      success,
      error,
    });

    // Keep only last N entries
    if (this.logs.length > this.maxEntries) {
      this.logs.shift();
    }
  }

  static getLogs(): QueryLogEntry[] {
    return [...this.logs];
  }

  static getStats() {
    if (this.logs.length === 0) {
      return {
        totalQueries: 0,
        successRate: 0,
        avgExecutionTime: 0,
        slowestQuery: null,
      };
    }

    const successful = this.logs.filter(l => l.success).length;
    const avgTime = this.logs.reduce((sum, l) => sum + l.executionTime, 0) / this.logs.length;
    const slowest = this.logs.reduce((max, l) => l.executionTime > max.executionTime ? l : max);

    return {
      totalQueries: this.logs.length,
      successRate: (successful / this.logs.length) * 100,
      avgExecutionTime: Math.round(avgTime),
      slowestQuery: {
        sql: slowest.sql,
        executionTime: slowest.executionTime,
      },
    };
  }
}

// Metadata cache
interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number;
}

export class MetadataCache {
  private static enabled = process.env.ENABLE_CACHE !== 'false';
  private static defaultTTL = parseInt(process.env.CACHE_TTL_MS || '300000', 10); // 5 minutes
  private static cache = new Map<string, CacheEntry<any>>();

  static get<T>(key: string): T | null {
    if (!this.enabled) return null;

    const entry = this.cache.get(key);
    if (!entry) return null;

    const now = Date.now();
    if (now - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }

    return entry.data;
  }

  static set<T>(key: string, data: T, ttl?: number) {
    if (!this.enabled) return;

    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl: ttl || this.defaultTTL,
    });
  }

  static clear() {
    this.cache.clear();
  }

  static getStats() {
    return {
      enabled: this.enabled,
      size: this.cache.size,
      ttlMs: this.defaultTTL,
    };
  }
}

// Retry logic with exponential backoff
export async function withRetry<T>(
  fn: () => Promise<T>,
  maxRetries = 3,
  baseDelay = 1000
): Promise<T> {
  let lastError: Error;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));

      // Don't retry if it's not a connection error
      if (!isRetriableError(lastError)) {
        throw lastError;
      }

      if (attempt < maxRetries) {
        const delay = baseDelay * Math.pow(2, attempt);
        console.error(`Retry attempt ${attempt + 1}/${maxRetries} after ${delay}ms...`);
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
  }

  throw lastError!;
}

function isRetriableError(error: Error): boolean {
  const retriableMessages = [
    'ECONNREFUSED',
    'ECONNRESET',
    'ETIMEDOUT',
    'ENOTFOUND',
    'Connection lost',
    'Connection terminated',
    'socket hang up',
  ];

  return retriableMessages.some(msg => error.message.includes(msg));
}

// Error message mapping
export function mapDatabaseError(error: Error, dbType: string): string {
  const message = error.message;

  // Oracle errors
  if (dbType === 'oracle') {
    if (message.includes('ORA-00942')) return 'Table or view does not exist';
    if (message.includes('ORA-00904')) return 'Invalid column name';
    if (message.includes('ORA-01017')) return 'Invalid username or password';
    if (message.includes('ORA-12154')) return 'TNS: could not resolve the connect identifier';
    if (message.includes('ORA-12170')) return 'TNS: connect timeout occurred';
    if (message.includes('ORA-01031')) return 'Insufficient privileges';
  }

  // PostgreSQL errors
  if (dbType === 'postgres') {
    if (message.includes('relation') && message.includes('does not exist')) {
      return 'Table or view does not exist';
    }
    if (message.includes('column') && message.includes('does not exist')) {
      return 'Invalid column name';
    }
    if (message.includes('password authentication failed')) {
      return 'Invalid username or password';
    }
    if (message.includes('permission denied')) return 'Insufficient privileges';
  }

  // SQL Server errors
  if (dbType === 'sqlserver') {
    if (message.includes('Invalid object name')) return 'Table or view does not exist';
    if (message.includes('Invalid column name')) return 'Invalid column name';
    if (message.includes('Login failed')) return 'Invalid username or password';
    if (message.includes('permission')) return 'Insufficient privileges';
  }

  // MySQL errors
  if (dbType === 'mysql') {
    if (message.includes("doesn't exist")) return 'Table or view does not exist';
    if (message.includes('Unknown column')) return 'Invalid column name';
    if (message.includes('Access denied')) return 'Invalid username or password';
    if (message.includes('command denied')) return 'Insufficient privileges';
  }

  return message;
}

// Query validation
export function validateWhereClause(whereClause: string): { isValid: boolean; error?: string } {
  const trimmed = whereClause.trim();
  
  if (!trimmed) {
    return { isValid: false, error: "WHERE clause cannot be empty" };
  }

  // Check for dangerous keywords
  const dangerousKeywords = /\b(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|EXEC|EXECUTE)\b/i;
  if (dangerousKeywords.test(trimmed)) {
    return {
      isValid: false,
      error: "WHERE clause contains disallowed SQL operations",
    };
  }

  // Check for common SQL injection patterns
  const injectionPatterns = [
    /;\s*DROP/i,
    /;\s*DELETE/i,
    /;\s*UPDATE/i,
    /UNION\s+SELECT/i,
    /--\s*$/,
    /\/\*.*\*\//,
  ];

  for (const pattern of injectionPatterns) {
    if (pattern.test(trimmed)) {
      return {
        isValid: false,
        error: "WHERE clause contains potentially malicious patterns",
      };
    }
  }

  return { isValid: true };
}

// Format results as CSV
export function formatAsCSV(columns: string[], rows: any[]): string {
  const escapeCSV = (value: any): string => {
    if (value === null || value === undefined) return '';
    const str = String(value);
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };

  const header = columns.map(escapeCSV).join(',');
  const body = rows.map(row =>
    columns.map(col => escapeCSV(row[col])).join(',')
  ).join('\n');

  return `${header}\n${body}`;
}

// Format results as ASCII table
export function formatAsTable(columns: string[], rows: any[]): string {
  if (rows.length === 0) return 'No rows returned';

  // Calculate column widths
  const widths = columns.map(col => {
    const dataWidth = Math.max(...rows.map(row => String(row[col] || '').length));
    return Math.max(col.length, dataWidth, 3);
  });

  // Create separator
  const separator = '+' + widths.map(w => '-'.repeat(w + 2)).join('+') + '+';

  // Create header
  const header = '|' + columns.map((col, i) =>
    ` ${col.padEnd(widths[i])} `
  ).join('|') + '|';

  // Create rows
  const bodyRows = rows.map(row =>
    '|' + columns.map((col, i) =>
      ` ${String(row[col] || '').padEnd(widths[i])} `
    ).join('|') + '|'
  );

  return [separator, header, separator, ...bodyRows, separator].join('\n');
}

// Query timeout wrapper
export async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  errorMessage = 'Operation timed out'
): Promise<T> {
  let timeoutHandle: NodeJS.Timeout;

  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutHandle = setTimeout(() => {
      reject(new Error(errorMessage));
    }, timeoutMs);
  });

  try {
    return await Promise.race([promise, timeoutPromise]);
  } finally {
    clearTimeout(timeoutHandle!);
  }
}

// Server uptime tracking
export class UptimeTracker {
  private static startTime = Date.now();

  static getUptime(): number {
    return Date.now() - this.startTime;
  }

  static getUptimeFormatted(): string {
    const ms = this.getUptime();
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ${hours % 24}h ${minutes % 60}m`;
    if (hours > 0) return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
    if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
    return `${seconds}s`;
  }
}

// Query templates system
export interface QueryTemplate {
  id: string;
  name: string;
  description: string;
  sql: string;
  parameters: string[];
}

export class QueryTemplates {
  private static templates: Map<string, QueryTemplate> = new Map([
    ['top-rows', {
      id: 'top-rows',
      name: 'Get Top N Rows',
      description: 'Retrieve the first N rows from a table',
      sql: 'SELECT * FROM {{table}} LIMIT {{limit}}',
      parameters: ['table', 'limit'],
    }],
    ['filter-equals', {
      id: 'filter-equals',
      name: 'Filter by Exact Match',
      description: 'Select rows where a column equals a specific value',
      sql: 'SELECT * FROM {{table}} WHERE {{column}} = {{value}}',
      parameters: ['table', 'column', 'value'],
    }],
    ['filter-like', {
      id: 'filter-like',
      name: 'Filter by Pattern',
      description: 'Select rows where a column matches a pattern',
      sql: 'SELECT * FROM {{table}} WHERE {{column}} LIKE {{pattern}}',
      parameters: ['table', 'column', 'pattern'],
    }],
    ['date-range', {
      id: 'date-range',
      name: 'Filter by Date Range',
      description: 'Select rows within a date range',
      sql: 'SELECT * FROM {{table}} WHERE {{dateColumn}} BETWEEN {{startDate}} AND {{endDate}}',
      parameters: ['table', 'dateColumn', 'startDate', 'endDate'],
    }],
    ['aggregate-count', {
      id: 'aggregate-count',
      name: 'Count by Group',
      description: 'Count rows grouped by a column',
      sql: 'SELECT {{groupColumn}}, COUNT(*) as count FROM {{table}} GROUP BY {{groupColumn}} ORDER BY count DESC',
      parameters: ['table', 'groupColumn'],
    }],
    ['aggregate-sum', {
      id: 'aggregate-sum',
      name: 'Sum by Group',
      description: 'Sum a column grouped by another column',
      sql: 'SELECT {{groupColumn}}, SUM({{sumColumn}}) as total FROM {{table}} GROUP BY {{groupColumn}} ORDER BY total DESC',
      parameters: ['table', 'groupColumn', 'sumColumn'],
    }],
    ['join-tables', {
      id: 'join-tables',
      name: 'Join Two Tables',
      description: 'Inner join two tables on specified columns',
      sql: 'SELECT * FROM {{table1}} t1 INNER JOIN {{table2}} t2 ON t1.{{joinColumn1}} = t2.{{joinColumn2}}',
      parameters: ['table1', 'table2', 'joinColumn1', 'joinColumn2'],
    }],
    ['distinct-values', {
      id: 'distinct-values',
      name: 'Get Distinct Values',
      description: 'Get unique values from a column',
      sql: 'SELECT DISTINCT {{column}} FROM {{table}} ORDER BY {{column}}',
      parameters: ['table', 'column'],
    }],
    ['null-check', {
      id: 'null-check',
      name: 'Filter Null Values',
      description: 'Select rows where a column is or is not null',
      sql: 'SELECT * FROM {{table}} WHERE {{column}} {{operator}}',
      parameters: ['table', 'column', 'operator'],
    }],
    ['recent-records', {
      id: 'recent-records',
      name: 'Get Recent Records',
      description: 'Get most recent N records ordered by date column',
      sql: 'SELECT * FROM {{table}} ORDER BY {{dateColumn}} DESC LIMIT {{limit}}',
      parameters: ['table', 'dateColumn', 'limit'],
    }],
  ]);

  static list(): QueryTemplate[] {
    return Array.from(this.templates.values());
  }

  static get(id: string): QueryTemplate | undefined {
    return this.templates.get(id);
  }

  static execute(id: string, params: Record<string, string>): string {
    const template = this.templates.get(id);
    if (!template) {
      throw new Error(`Template '${id}' not found`);
    }

    // Validate required parameters
    const missing = template.parameters.filter(p => !params[p]);
    if (missing.length > 0) {
      throw new Error(`Missing required parameters: ${missing.join(', ')}`);
    }

    // Replace parameters in SQL
    let sql = template.sql;
    for (const [key, value] of Object.entries(params)) {
      const placeholder = `{{${key}}}`;
      
      // Check if value needs quotes (not a number)
      const needsQuotes = isNaN(Number(value)) && !value.startsWith("'") && !value.startsWith('"');
      const replacementValue = needsQuotes ? `'${value}'` : value;
      
      sql = sql.replace(new RegExp(placeholder, 'g'), replacementValue);
    }

    return sql;
  }

  static add(template: QueryTemplate): void {
    this.templates.set(template.id, template);
  }
}

// Schema comparison utilities
export interface ColumnDiff {
  columnName: string;
  status: 'added' | 'removed' | 'modified' | 'identical';
  changes?: {
    type?: { before: string; after: string };
    nullable?: { before: boolean; after: boolean };
    length?: { before?: number; after?: number };
    precision?: { before?: number; after?: number };
    scale?: { before?: number; after?: number };
    defaultValue?: { before?: string; after?: string };
  };
}

export interface SchemaDiffResult {
  tableName: string;
  status: 'added' | 'removed' | 'modified' | 'identical';
  columnDifferences?: ColumnDiff[];
  addedColumns?: string[];
  removedColumns?: string[];
  modifiedColumns?: string[];
}

export function compareTableSchemas(
  table1: any,
  table2: any,
  table1Name: string = 'schema1',
  table2Name: string = 'schema2'
): SchemaDiffResult {
  if (!table1 && !table2) {
    throw new Error('Both tables are null');
  }

  if (!table1) {
    return {
      tableName: table2.tableName,
      status: 'added',
    };
  }

  if (!table2) {
    return {
      tableName: table1.tableName,
      status: 'removed',
    };
  }

  const columns1 = new Map(table1.columns.map((c: any) => [c.name, c]));
  const columns2 = new Map(table2.columns.map((c: any) => [c.name, c]));

  const allColumnNames = new Set([...columns1.keys(), ...columns2.keys()]);
  const columnDifferences: ColumnDiff[] = [];
  const addedColumns: string[] = [];
  const removedColumns: string[] = [];
  const modifiedColumns: string[] = [];

  for (const columnName of allColumnNames) {
    const colName = columnName as string;
    const col1 = columns1.get(colName) as any;
    const col2 = columns2.get(colName) as any;

    if (!col1) {
      addedColumns.push(colName);
      columnDifferences.push({
        columnName: colName,
        status: 'added',
      });
    } else if (!col2) {
      removedColumns.push(colName);
      columnDifferences.push({
        columnName: colName,
        status: 'removed',
      });
    } else {
      // Compare column properties
      const changes: any = {};
      let hasChanges = false;

      if (col1.type !== col2.type) {
        changes.type = { before: col1.type, after: col2.type };
        hasChanges = true;
      }

      if (col1.nullable !== col2.nullable) {
        changes.nullable = { before: col1.nullable, after: col2.nullable };
        hasChanges = true;
      }

      if (col1.length !== col2.length && col1.length !== undefined && col2.length !== undefined) {
        changes.length = { before: col1.length, after: col2.length };
        hasChanges = true;
      }

      if (col1.precision !== col2.precision && col1.precision !== undefined && col2.precision !== undefined) {
        changes.precision = { before: col1.precision, after: col2.precision };
        hasChanges = true;
      }

      if (col1.scale !== col2.scale && col1.scale !== undefined && col2.scale !== undefined) {
        changes.scale = { before: col1.scale, after: col2.scale };
        hasChanges = true;
      }

      if (col1.defaultValue !== col2.defaultValue) {
        changes.defaultValue = { before: col1.defaultValue, after: col2.defaultValue };
        hasChanges = true;
      }

      if (hasChanges) {
        modifiedColumns.push(colName);
        columnDifferences.push({
          columnName: colName,
          status: 'modified',
          changes,
        });
      } else {
        columnDifferences.push({
          columnName: colName,
          status: 'identical',
        });
      }
    }
  }

  const status = addedColumns.length > 0 || removedColumns.length > 0 || modifiedColumns.length > 0
    ? 'modified'
    : 'identical';

  return {
    tableName: table1.tableName,
    status,
    columnDifferences,
    addedColumns,
    removedColumns,
    modifiedColumns,
  };
}
