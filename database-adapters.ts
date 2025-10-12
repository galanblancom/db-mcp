// Enhanced Database adapter interface and implementations for multi-database support
import oracledb from "oracledb";
import pg from "pg";
import mssql from "mssql";
import mysql from "mysql2/promise";
import Database from "better-sqlite3";
import { withTimeout, withRetry } from "./utils.js";

type OraclePool = any; // Oracle pool type

// Common types for database operations
export interface QueryResult {
  rows: any[];
  rowCount: number;
  columns: string[];
}

export interface TableInfo {
  tableName: string;
  owner?: string;
  schema?: string;
  rowCount: number;
  columns: ColumnInfo[];
}

export interface ColumnInfo {
  name: string;
  type: string;
  length?: number;
  precision?: number;
  scale?: number;
  nullable: boolean;
  defaultValue?: string;
  isPrimaryKey?: boolean;
  isForeignKey?: boolean;
}

export interface TableListItem {
  name: string;
  rowCount?: number;
  lastAnalyzed?: Date | null;
}

export interface SchemaInfo {
  name: string;
  owner?: string;
  tableCount?: number;
  viewCount?: number;
}

export interface ViewInfo {
  name: string;
  schema?: string;
  owner?: string;
}

export interface ViewDefinition {
  name: string;
  schema?: string;
  owner?: string;
  definition: string;
}

export interface IndexInfo {
  indexName: string;
  tableName: string;
  columns: string[];
  isUnique: boolean;
  indexType: string;
  schema?: string;
}

export interface ForeignKeyInfo {
  constraintName: string;
  tableName: string;
  columnName: string;
  referencedTable: string;
  referencedColumn: string;
  onDelete?: string;
  onUpdate?: string;
  schema?: string;
}

export interface StoredProcedureInfo {
  name: string;
  schema?: string;
  type: 'PROCEDURE' | 'FUNCTION';
  returnType?: string;
  parameters?: string;
}

export interface TableStatistics {
  tableName: string;
  schema?: string;
  rowCount: number;
  sizeInMB?: number;
  indexSizeInMB?: number;
  lastAnalyzed?: Date | null;
  fragmentation?: number;
}

export interface ExplainPlan {
  query: string;
  plan: string;
  estimatedCost?: number;
  estimatedRows?: number;
}

// Abstract base class for database adapters
export abstract class DatabaseAdapter {
  protected queryTimeout = parseInt(process.env.QUERY_TIMEOUT_MS || '30000', 10);

  // Core operations
  abstract connect(): Promise<void>;
  abstract disconnect(): Promise<void>;
  abstract executeQuery(sql: string, maxRows?: number, excludeLargeColumns?: boolean): Promise<QueryResult>;
  abstract getTableInfo(tableName: string, schema?: string): Promise<TableInfo>;
  abstract listTables(schema?: string, pattern?: string): Promise<TableListItem[]>;
  abstract getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number>;
  abstract listSchemas(): Promise<SchemaInfo[]>;
  abstract listViews(schema?: string, pattern?: string): Promise<ViewInfo[]>;
  abstract getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition>;
  abstract testConnection(): Promise<boolean>;

  // New advanced operations
  abstract getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]>;
  abstract getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]>;
  abstract listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]>;
  abstract getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics>;
  abstract explainQuery(sql: string): Promise<ExplainPlan>;
  abstract sampleTableData(tableName: string, schema?: string, limit?: number, random?: boolean): Promise<QueryResult>;
  
  // Streaming operation for large result sets
  abstract executeQueryStream(sql: string, batchSize?: number): AsyncGenerator<any[], void, unknown>;
  
  // Read-only transaction support
  abstract executeTransaction(queries: string[]): Promise<QueryResult[]>;
}

// Oracle Database Adapter with Connection Pooling
export class OracleAdapter extends DatabaseAdapter {
  private pool: OraclePool | null = null;
  private config: {
    user: string;
    password: string;
    connectString: string;
    thickMode: boolean;
    libDir?: string;
  };

  constructor(config: {
    user: string;
    password: string;
    host: string;
    port: string;
    service: string;
    thickMode?: boolean;
    libDir?: string;
  }) {
    super();
    this.config = {
      user: config.user,
      password: config.password,
      connectString: `${config.host}:${config.port}/${config.service}`,
      thickMode: config.thickMode || false,
      libDir: config.libDir,
    };

    if (this.config.thickMode && this.config.libDir) {
      try {
        (oracledb as any).initOracleClient({ libDir: this.config.libDir });
        console.error("Oracle Client initialized in Thick mode");
      } catch (err: any) {
        console.error("Warning: Could not initialize Oracle Client:", err.message);
      }
    }
  }

  async connect(): Promise<void> {
    if (this.pool) return;

    this.pool = await withRetry(async () =>
      (oracledb as any).createPool({
        user: this.config.user,
        password: this.config.password,
        connectString: this.config.connectString,
        poolMin: 1,
        poolMax: 10,
        poolIncrement: 1,
      })
    );
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.close(10);
      this.pool = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000, excludeLargeColumns: boolean = false): Promise<QueryResult> {
    await this.connect();
    
    const connection = await this.pool!.getConnection();
    try {
      const result: any = await withTimeout(
        connection.execute(sql, [], {
          outFormat: oracledb.OUT_FORMAT_OBJECT,
          maxRows: maxRows,
        }),
        this.queryTimeout
      );

      let columns = result.metaData?.map((m: any) => m.name) || [];
      let rows = (result.rows || []) as any[];

      // Filter out large columns if requested
      if (excludeLargeColumns && result.metaData) {
        const largeTypes = ['CLOB', 'BLOB', 'LONG', 'LONG RAW', 'BFILE'];
        const filteredColumns = columns.filter((_: any, i: any) =>
          !largeTypes.includes(result.metaData![i].fetchType?.toString() || '')
        );
        
        rows = rows.map(row => {
          const filtered: any = {};
          filteredColumns.forEach((col: any) => filtered[col] = row[col]);
          return filtered;
        });
        columns = filteredColumns;
      }

      return { rows, rowCount: rows.length, columns };
    } finally {
      await connection.close();
    }
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const tableNameUpper = tableName.toUpperCase();

      const columnQuery = `
        SELECT 
          c.COLUMN_NAME,
          c.DATA_TYPE,
          c.DATA_LENGTH,
          c.DATA_PRECISION,
          c.DATA_SCALE,
          c.NULLABLE,
          c.DATA_DEFAULT,
          CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 'Y' ELSE 'N' END as IS_PRIMARY_KEY,
          CASE WHEN fk.COLUMN_NAME IS NOT NULL THEN 'Y' ELSE 'N' END as IS_FOREIGN_KEY
        FROM ALL_TAB_COLUMNS c
        LEFT JOIN (
          SELECT acc.COLUMN_NAME, acc.TABLE_NAME, acc.OWNER
          FROM ALL_CONSTRAINTS ac
          JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
          WHERE ac.CONSTRAINT_TYPE = 'P'
        ) pk ON c.COLUMN_NAME = pk.COLUMN_NAME AND c.TABLE_NAME = pk.TABLE_NAME AND c.OWNER = pk.OWNER
        LEFT JOIN (
          SELECT acc.COLUMN_NAME, acc.TABLE_NAME, acc.OWNER
          FROM ALL_CONSTRAINTS ac
          JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
          WHERE ac.CONSTRAINT_TYPE = 'R'
        ) fk ON c.COLUMN_NAME = fk.COLUMN_NAME AND c.TABLE_NAME = fk.TABLE_NAME AND c.OWNER = fk.OWNER
        WHERE c.TABLE_NAME = :tableName AND c.OWNER = :owner
        ORDER BY c.COLUMN_ID
      `;

      const columnResult = await connection.execute(
        columnQuery,
        { tableName: tableNameUpper, owner: owner.toUpperCase() },
        { outFormat: oracledb.OUT_FORMAT_OBJECT }
      );

      const countQuery = `SELECT COUNT(*) as ROW_COUNT FROM ${owner}.${tableNameUpper}`;
      const countResult = await connection.execute(countQuery, [], {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      const rowCount = (countResult.rows as any)?.[0]?.ROW_COUNT || 0;

      const columns: ColumnInfo[] = ((columnResult.rows || []) as any[]).map((col: any) => ({
        name: col.COLUMN_NAME,
        type: col.DATA_TYPE,
        length: col.DATA_LENGTH,
        precision: col.DATA_PRECISION,
        scale: col.DATA_SCALE,
        nullable: col.NULLABLE === "Y",
        defaultValue: col.DATA_DEFAULT,
        isPrimaryKey: col.IS_PRIMARY_KEY === 'Y',
        isForeignKey: col.IS_FOREIGN_KEY === 'Y',
      }));

      return {
        tableName: tableNameUpper,
        owner,
        rowCount,
        columns,
      };
    } finally {
      await connection.close();
    }
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      let query = `
        SELECT TABLE_NAME, NUM_ROWS, LAST_ANALYZED
        FROM ALL_TABLES
        WHERE OWNER = :owner
      `;

      const binds: any = { owner: owner.toUpperCase() };

      if (pattern) {
        query += ` AND TABLE_NAME LIKE :pattern`;
        binds.pattern = pattern.toUpperCase();
      }

      query += ` ORDER BY TABLE_NAME`;

      const result = await connection.execute(query, binds, {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      return ((result.rows || []) as any[]).map((row: any) => ({
        name: row.TABLE_NAME,
        rowCount: row.NUM_ROWS,
        lastAnalyzed: row.LAST_ANALYZED,
      }));
    } finally {
      await connection.close();
    }
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const tableNameUpper = tableName.toUpperCase();

      let query = `SELECT COUNT(*) as ROW_COUNT FROM ${owner}.${tableNameUpper}`;
      
      if (whereClause) {
        query += ` WHERE ${whereClause}`;
      }

      const result = await connection.execute(query, [], {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      return (result.rows as any)?.[0]?.ROW_COUNT || 0;
    } finally {
      await connection.close();
    }
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const query = `
        SELECT 
          username as SCHEMA_NAME,
          (SELECT COUNT(*) FROM all_tables WHERE owner = u.username) as TABLE_COUNT,
          (SELECT COUNT(*) FROM all_views WHERE owner = u.username) as VIEW_COUNT
        FROM all_users u
        ORDER BY username
      `;

      const result = await connection.execute(query, [], {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      return ((result.rows || []) as any[]).map((row: any) => ({
        name: row.SCHEMA_NAME,
        owner: row.SCHEMA_NAME,
        tableCount: row.TABLE_COUNT,
        viewCount: row.VIEW_COUNT,
      }));
    } finally {
      await connection.close();
    }
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      
      let query = `
        SELECT VIEW_NAME, OWNER
        FROM ALL_VIEWS
        WHERE OWNER = :owner
      `;

      const binds: any = { owner: owner.toUpperCase() };

      if (pattern) {
        query += ` AND VIEW_NAME LIKE :pattern`;
        binds.pattern = pattern.toUpperCase();
      }

      query += ` ORDER BY VIEW_NAME`;

      const result = await connection.execute(query, binds, {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      return ((result.rows || []) as any[]).map((row: any) => ({
        name: row.VIEW_NAME,
        owner: row.OWNER,
      }));
    } finally {
      await connection.close();
    }
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const viewNameUpper = viewName.toUpperCase();

      const query = `
        SELECT VIEW_NAME, OWNER, TEXT
        FROM ALL_VIEWS
        WHERE VIEW_NAME = :viewName AND OWNER = :owner
      `;

      const result = await connection.execute(
        query,
        { viewName: viewNameUpper, owner: owner.toUpperCase() },
        { outFormat: oracledb.OUT_FORMAT_OBJECT }
      );

      if (!result.rows || result.rows.length === 0) {
        throw new Error(`View ${owner}.${viewNameUpper} not found`);
      }

      const row = (result.rows as any[])[0];

      return {
        name: row.VIEW_NAME,
        owner: row.OWNER,
        definition: row.TEXT,
      };
    } finally {
      await connection.close();
    }
  }

  async getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const tableNameUpper = tableName.toUpperCase();

      const query = `
        SELECT 
          i.INDEX_NAME,
          i.TABLE_NAME,
          i.UNIQUENESS,
          i.INDEX_TYPE,
          LISTAGG(ic.COLUMN_NAME, ', ') WITHIN GROUP (ORDER BY ic.COLUMN_POSITION) as COLUMNS
        FROM ALL_INDEXES i
        JOIN ALL_IND_COLUMNS ic ON i.INDEX_NAME = ic.INDEX_NAME AND i.TABLE_OWNER = ic.TABLE_OWNER
        WHERE i.TABLE_NAME = :tableName AND i.TABLE_OWNER = :owner
        GROUP BY i.INDEX_NAME, i.TABLE_NAME, i.UNIQUENESS, i.INDEX_TYPE
        ORDER BY i.INDEX_NAME
      `;

      const result = await connection.execute(
        query,
        { tableName: tableNameUpper, owner: owner.toUpperCase() },
        { outFormat: oracledb.OUT_FORMAT_OBJECT }
      );

      return ((result.rows || []) as any[]).map((row: any) => ({
        indexName: row.INDEX_NAME,
        tableName: row.TABLE_NAME,
        columns: row.COLUMNS.split(', '),
        isUnique: row.UNIQUENESS === 'UNIQUE',
        indexType: row.INDEX_TYPE,
        schema: owner,
      }));
    } finally {
      await connection.close();
    }
  }

  async getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const tableNameUpper = tableName.toUpperCase();

      const query = `
        SELECT 
          ac.CONSTRAINT_NAME,
          ac.TABLE_NAME,
          acc.COLUMN_NAME,
          r_acc.TABLE_NAME as REFERENCED_TABLE,
          r_acc.COLUMN_NAME as REFERENCED_COLUMN,
          ac.DELETE_RULE
        FROM ALL_CONSTRAINTS ac
        JOIN ALL_CONS_COLUMNS acc ON ac.CONSTRAINT_NAME = acc.CONSTRAINT_NAME AND ac.OWNER = acc.OWNER
        JOIN ALL_CONSTRAINTS r_ac ON ac.R_CONSTRAINT_NAME = r_ac.CONSTRAINT_NAME AND ac.R_OWNER = r_ac.OWNER
        JOIN ALL_CONS_COLUMNS r_acc ON r_ac.CONSTRAINT_NAME = r_acc.CONSTRAINT_NAME AND r_ac.OWNER = r_acc.OWNER
        WHERE ac.CONSTRAINT_TYPE = 'R' AND ac.TABLE_NAME = :tableName AND ac.OWNER = :owner
        ORDER BY ac.CONSTRAINT_NAME
      `;

      const result = await connection.execute(
        query,
        { tableName: tableNameUpper, owner: owner.toUpperCase() },
        { outFormat: oracledb.OUT_FORMAT_OBJECT }
      );

      return ((result.rows || []) as any[]).map((row: any) => ({
        constraintName: row.CONSTRAINT_NAME,
        tableName: row.TABLE_NAME,
        columnName: row.COLUMN_NAME,
        referencedTable: row.REFERENCED_TABLE,
        referencedColumn: row.REFERENCED_COLUMN,
        onDelete: row.DELETE_RULE,
        schema: owner,
      }));
    } finally {
      await connection.close();
    }
  }

  async listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      
      let query = `
        SELECT OBJECT_NAME, OBJECT_TYPE
        FROM ALL_OBJECTS
        WHERE OBJECT_TYPE IN ('PROCEDURE', 'FUNCTION') AND OWNER = :owner
      `;

      const binds: any = { owner: owner.toUpperCase() };

      if (pattern) {
        query += ` AND OBJECT_NAME LIKE :pattern`;
        binds.pattern = pattern.toUpperCase();
      }

      query += ` ORDER BY OBJECT_NAME`;

      const result = await connection.execute(query, binds, {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      return ((result.rows || []) as any[]).map((row: any) => ({
        name: row.OBJECT_NAME,
        schema: owner,
        type: row.OBJECT_TYPE as 'PROCEDURE' | 'FUNCTION',
      }));
    } finally {
      await connection.close();
    }
  }

  async getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const owner = schema || this.config.user;
      const tableNameUpper = tableName.toUpperCase();

      const query = `
        SELECT 
          t.TABLE_NAME,
          t.NUM_ROWS,
          t.LAST_ANALYZED,
          s.BYTES / 1024 / 1024 as SIZE_MB
        FROM ALL_TABLES t
        LEFT JOIN (
          SELECT SEGMENT_NAME, SUM(BYTES) as BYTES
          FROM DBA_SEGMENTS
          WHERE SEGMENT_TYPE = 'TABLE' AND OWNER = :owner
          GROUP BY SEGMENT_NAME
        ) s ON t.TABLE_NAME = s.SEGMENT_NAME
        WHERE t.TABLE_NAME = :tableName AND t.OWNER = :owner
      `;

      const result = await connection.execute(
        query,
        { tableName: tableNameUpper, owner: owner.toUpperCase() },
        { outFormat: oracledb.OUT_FORMAT_OBJECT }
      );

      if (!result.rows || result.rows.length === 0) {
        throw new Error(`Table ${owner}.${tableNameUpper} not found`);
      }

      const row = (result.rows as any[])[0];

      return {
        tableName: row.TABLE_NAME,
        schema: owner,
        rowCount: row.NUM_ROWS || 0,
        sizeInMB: row.SIZE_MB || 0,
        lastAnalyzed: row.LAST_ANALYZED,
      };
    } finally {
      await connection.close();
    }
  }

  async explainQuery(sql: string): Promise<ExplainPlan> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      await connection.execute(`EXPLAIN PLAN FOR ${sql}`);
      
      const planQuery = `
        SELECT PLAN_TABLE_OUTPUT
        FROM TABLE(DBMS_XPLAN.DISPLAY())
      `;

      const result = await connection.execute(planQuery, [], {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      const planLines = ((result.rows || []) as any[]).map(row => row.PLAN_TABLE_OUTPUT).join('\n');

      return {
        query: sql,
        plan: planLines,
      };
    } finally {
      await connection.close();
    }
  }

  async sampleTableData(tableName: string, schema?: string, limit: number = 10, random: boolean = false): Promise<QueryResult> {
    const owner = schema || this.config.user;
    const tableNameUpper = tableName.toUpperCase();

    let sql = `SELECT * FROM ${owner}.${tableNameUpper}`;
    
    if (random) {
      sql += ` ORDER BY DBMS_RANDOM.VALUE`;
    }
    
    return await this.executeQuery(sql, limit);
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      return true;
    } catch {
      return false;
    }
  }

  async *executeQueryStream(sql: string, batchSize: number = 100): AsyncGenerator<any[], void, unknown> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      const stream = connection.queryStream(sql, [], {
        outFormat: oracledb.OUT_FORMAT_OBJECT,
      });

      let batch: any[] = [];
      
      for await (const row of stream) {
        batch.push(row);
        
        if (batch.length >= batchSize) {
          yield batch;
          batch = [];
        }
      }
      
      // Yield remaining rows
      if (batch.length > 0) {
        yield batch;
      }
    } finally {
      await connection.close();
    }
  }

  async executeTransaction(queries: string[]): Promise<QueryResult[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      // Start read-only transaction
      await connection.execute('SET TRANSACTION READ ONLY');
      
      const results: QueryResult[] = [];
      
      for (const sql of queries) {
        const result: any = await connection.execute(sql, [], {
          outFormat: oracledb.OUT_FORMAT_OBJECT,
        });
        
        const columns = result.metaData?.map((m: any) => m.name) || [];
        const rows = (result.rows || []) as any[];
        
        results.push({ rows, rowCount: rows.length, columns });
      }
      
      // Commit (no changes, but closes transaction)
      await connection.commit();
      
      return results;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      await connection.close();
    }
  }
}

// PostgreSQL Adapter with Connection Pooling
export class PostgresAdapter extends DatabaseAdapter {
  private pool: pg.Pool | null = null;
  private config: pg.PoolConfig;

  constructor(config: {
    user: string;
    password: string;
    host: string;
    port: number;
    database: string;
  }) {
    super();
    this.config = {
      ...config,
      max: 10,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 2000,
    };
  }

  async connect(): Promise<void> {
    if (this.pool) return;
    
    this.pool = await withRetry(async () => new pg.Pool(this.config));
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.end();
      this.pool = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000, excludeLargeColumns: boolean = false): Promise<QueryResult> {
    await this.connect();
    
    const limitedSql = sql.trim().toLowerCase().includes('limit') 
      ? sql 
      : `${sql} LIMIT ${maxRows}`;

    const result = await withTimeout(
      this.pool!.query(limitedSql),
      this.queryTimeout
    );

    let columns = result.fields.map(f => f.name);
    let rows = result.rows;

    if (excludeLargeColumns) {
      const largeTypes = ['bytea', 'text'];
      const filteredColumns = result.fields
        .filter(f => !largeTypes.includes(f.dataTypeID.toString()))
        .map(f => f.name);
      
      rows = rows.map(row => {
        const filtered: any = {};
        filteredColumns.forEach(col => filtered[col] = row[col]);
        return filtered;
      });
      columns = filteredColumns;
    }

    return {
      rows,
      rowCount: rows.length,
      columns,
    };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const columnQuery = `
      SELECT 
        c.column_name,
        c.data_type,
        c.character_maximum_length,
        c.numeric_precision,
        c.numeric_scale,
        c.is_nullable,
        c.column_default,
        CASE WHEN pk.column_name IS NOT NULL THEN true ELSE false END as is_primary_key,
        CASE WHEN fk.column_name IS NOT NULL THEN true ELSE false END as is_foreign_key
      FROM information_schema.columns c
      LEFT JOIN (
        SELECT kcu.column_name, kcu.table_name, kcu.table_schema
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu 
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'PRIMARY KEY'
      ) pk ON c.column_name = pk.column_name 
        AND c.table_name = pk.table_name 
        AND c.table_schema = pk.table_schema
      LEFT JOIN (
        SELECT kcu.column_name, kcu.table_name, kcu.table_schema
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu 
          ON tc.constraint_name = kcu.constraint_name
          AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
      ) fk ON c.column_name = fk.column_name 
        AND c.table_name = fk.table_name 
        AND c.table_schema = fk.table_schema
      WHERE c.table_name = $1 AND c.table_schema = $2
      ORDER BY c.ordinal_position
    `;

    const columnResult = await this.pool!.query(columnQuery, [tableName.toLowerCase(), schemaName]);

    const countQuery = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    const countResult = await this.pool!.query(countQuery);
    const rowCount = parseInt(countResult.rows[0].count, 10);

    const columns: ColumnInfo[] = columnResult.rows.map((col: any) => ({
      name: col.column_name,
      type: col.data_type,
      length: col.character_maximum_length,
      precision: col.numeric_precision,
      scale: col.numeric_scale,
      nullable: col.is_nullable === 'YES',
      defaultValue: col.column_default,
      isPrimaryKey: col.is_primary_key,
      isForeignKey: col.is_foreign_key,
    }));

    return {
      tableName,
      schema: schemaName,
      rowCount,
      columns,
    };
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();
    
    const schemaName = schema || 'public';
    
    let query = `
      SELECT 
        table_name,
        (SELECT reltuples::bigint FROM pg_class WHERE relname = table_name AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = $1)) as row_count
      FROM information_schema.tables
      WHERE table_schema = $1 AND table_type = 'BASE TABLE'
    `;

    const params: any[] = [schemaName];

    if (pattern) {
      query += ` AND table_name LIKE $2`;
      params.push(pattern.toLowerCase());
    }

    query += ` ORDER BY table_name`;

    const result = await this.pool!.query(query, params);

    return result.rows.map((row: any) => ({
      name: row.table_name,
      rowCount: row.row_count ? parseInt(row.row_count, 10) : undefined,
      lastAnalyzed: null,
    }));
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    
    const schemaName = schema || 'public';
    let query = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    
    if (whereClause) {
      query += ` WHERE ${whereClause}`;
    }

    const result = await this.pool!.query(query);
    return parseInt(result.rows[0].count, 10);
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    await this.connect();
    
    const query = `
      SELECT 
        schema_name,
        (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = s.schema_name AND table_type = 'BASE TABLE') as table_count,
        (SELECT COUNT(*) FROM information_schema.views WHERE table_schema = s.schema_name) as view_count
      FROM information_schema.schemata s
      WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
      ORDER BY schema_name
    `;

    const result = await this.pool!.query(query);

    return result.rows.map((row: any) => ({
      name: row.schema_name,
      tableCount: parseInt(row.table_count, 10),
      viewCount: parseInt(row.view_count, 10),
    }));
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'public';
    
    let query = `
      SELECT table_name as view_name, table_schema as schema_name
      FROM information_schema.views
      WHERE table_schema = $1
    `;

    const params: any[] = [schemaName];

    if (pattern) {
      query += ` AND table_name LIKE $2`;
      params.push(pattern.toLowerCase());
    }

    query += ` ORDER BY table_name`;

    const result = await this.pool!.query(query, params);

    return result.rows.map((row: any) => ({
      name: row.view_name,
      schema: row.schema_name,
    }));
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const query = `
      SELECT table_name as view_name, table_schema as schema_name, view_definition
      FROM information_schema.views
      WHERE table_name = $1 AND table_schema = $2
    `;

    const result = await this.pool!.query(query, [viewName.toLowerCase(), schemaName]);

    if (result.rows.length === 0) {
      throw new Error(`View ${schemaName}.${viewName} not found`);
    }

    const row = result.rows[0];

    return {
      name: row.view_name,
      schema: row.schema_name,
      definition: row.view_definition,
    };
  }

  async getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const query = `
      SELECT
        i.relname as index_name,
        t.relname as table_name,
        ix.indisunique as is_unique,
        am.amname as index_type,
        array_agg(a.attname ORDER BY a.attnum) as columns
      FROM pg_class t
      JOIN pg_index ix ON t.oid = ix.indrelid
      JOIN pg_class i ON i.oid = ix.indexrelid
      JOIN pg_am am ON i.relam = am.oid
      JOIN pg_namespace n ON t.relnamespace = n.oid
      JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
      WHERE t.relname = $1 AND n.nspname = $2
      GROUP BY i.relname, t.relname, ix.indisunique, am.amname
      ORDER BY i.relname
    `;

    const result = await this.pool!.query(query, [tableName.toLowerCase(), schemaName]);

    return result.rows.map((row: any) => ({
      indexName: row.index_name,
      tableName: row.table_name,
      columns: row.columns,
      isUnique: row.is_unique,
      indexType: row.index_type,
      schema: schemaName,
    }));
  }

  async getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const query = `
      SELECT
        tc.constraint_name,
        tc.table_name,
        kcu.column_name,
        ccu.table_name AS referenced_table,
        ccu.column_name AS referenced_column,
        rc.delete_rule,
        rc.update_rule
      FROM information_schema.table_constraints tc
      JOIN information_schema.key_column_usage kcu
        ON tc.constraint_name = kcu.constraint_name
        AND tc.table_schema = kcu.table_schema
      JOIN information_schema.constraint_column_usage ccu
        ON ccu.constraint_name = tc.constraint_name
        AND ccu.table_schema = tc.table_schema
      JOIN information_schema.referential_constraints rc
        ON tc.constraint_name = rc.constraint_name
        AND tc.table_schema = rc.constraint_schema
      WHERE tc.constraint_type = 'FOREIGN KEY'
        AND tc.table_name = $1
        AND tc.table_schema = $2
      ORDER BY tc.constraint_name
    `;

    const result = await this.pool!.query(query, [tableName.toLowerCase(), schemaName]);

    return result.rows.map((row: any) => ({
      constraintName: row.constraint_name,
      tableName: row.table_name,
      columnName: row.column_name,
      referencedTable: row.referenced_table,
      referencedColumn: row.referenced_column,
      onDelete: row.delete_rule,
      onUpdate: row.update_rule,
      schema: schemaName,
    }));
  }

  async listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'public';
    
    let query = `
      SELECT 
        p.proname as name,
        n.nspname as schema,
        CASE p.prokind
          WHEN 'f' THEN 'FUNCTION'
          WHEN 'p' THEN 'PROCEDURE'
          ELSE 'FUNCTION'
        END as type,
        pg_get_function_result(p.oid) as return_type
      FROM pg_proc p
      JOIN pg_namespace n ON p.pronamespace = n.oid
      WHERE n.nspname = $1
    `;

    const params: any[] = [schemaName];

    if (pattern) {
      query += ` AND p.proname LIKE $2`;
      params.push(pattern.toLowerCase());
    }

    query += ` ORDER BY p.proname`;

    const result = await this.pool!.query(query, params);

    return result.rows.map((row: any) => ({
      name: row.name,
      schema: row.schema,
      type: row.type as 'PROCEDURE' | 'FUNCTION',
      returnType: row.return_type,
    }));
  }

  async getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const query = `
      SELECT
        schemaname || '.' || tablename as full_name,
        n_live_tup as row_count,
        pg_total_relation_size(schemaname || '.' || tablename) / 1024.0 / 1024.0 as size_mb,
        pg_indexes_size(schemaname || '.' || tablename) / 1024.0 / 1024.0 as index_size_mb,
        last_analyze,
        last_autoanalyze
      FROM pg_stat_user_tables
      WHERE tablename = $1 AND schemaname = $2
    `;

    const result = await this.pool!.query(query, [tableName.toLowerCase(), schemaName]);

    if (result.rows.length === 0) {
      throw new Error(`Table ${schemaName}.${tableName} not found`);
    }

    const row = result.rows[0];

    return {
      tableName,
      schema: schemaName,
      rowCount: parseInt(row.row_count, 10) || 0,
      sizeInMB: parseFloat(row.size_mb) || 0,
      indexSizeInMB: parseFloat(row.index_size_mb) || 0,
      lastAnalyzed: row.last_analyze || row.last_autoanalyze,
    };
  }

  async explainQuery(sql: string): Promise<ExplainPlan> {
    await this.connect();
    
    const result = await this.pool!.query(`EXPLAIN (FORMAT JSON, ANALYZE FALSE) ${sql}`);
    const plan = result.rows[0]['QUERY PLAN'];
    
    return {
      query: sql,
      plan: JSON.stringify(plan, null, 2),
      estimatedCost: plan[0]?.Plan?.['Total Cost'],
      estimatedRows: plan[0]?.Plan?.['Plan Rows'],
    };
  }

  async sampleTableData(tableName: string, schema?: string, limit: number = 10, random: boolean = false): Promise<QueryResult> {
    const schemaName = schema || 'public';

    let sql = `SELECT * FROM ${schemaName}.${tableName}`;
    
    if (random) {
      sql += ` ORDER BY RANDOM()`;
    }
    
    return await this.executeQuery(sql, limit);
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      await this.pool!.query('SELECT 1');
      return true;
    } catch {
      return false;
    }
  }

  async *executeQueryStream(sql: string, batchSize: number = 100): AsyncGenerator<any[], void, unknown> {
    await this.connect();
    const client = await this.pool!.connect();
    
    try {
      const cursor = client.query(new (pg as any).Cursor(sql));
      
      let batch: any[];
      do {
        batch = await new Promise((resolve, reject) => {
          cursor.read(batchSize, (err: any, rows: any[]) => {
            if (err) reject(err);
            else resolve(rows);
          });
        });
        
        if (batch && batch.length > 0) {
          yield batch;
        }
      } while (batch && batch.length === batchSize);
      
      cursor.close();
    } finally {
      client.release();
    }
  }

  async executeTransaction(queries: string[]): Promise<QueryResult[]> {
    await this.connect();
    const client = await this.pool!.connect();
    
    try {
      await client.query('BEGIN TRANSACTION READ ONLY');
      
      const results: QueryResult[] = [];
      
      for (const sql of queries) {
        const result = await client.query(sql);
        results.push({
          rows: result.rows,
          rowCount: result.rowCount || 0,
          columns: result.fields.map(f => f.name),
        });
      }
      
      await client.query('COMMIT');
      
      return results;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }
}

// SQL Server Adapter with Connection Pooling  
export class SQLServerAdapter extends DatabaseAdapter {
  private pool: mssql.ConnectionPool | null = null;
  private config: mssql.config;

  constructor(config: {
    user: string;
    password: string;
    server: string;
    port?: number;
    database: string;
    encrypt?: boolean;
    trustServerCertificate?: boolean;
  }) {
    super();
    this.config = {
      user: config.user,
      password: config.password,
      server: config.server,
      port: config.port || 1433,
      database: config.database,
      pool: {
        max: 10,
        min: 1,
        idleTimeoutMillis: 30000,
      },
      options: {
        encrypt: config.encrypt !== false,
        trustServerCertificate: config.trustServerCertificate !== false,
      },
    };
  }

  async connect(): Promise<void> {
    if (this.pool && this.pool.connected) return;
    
    this.pool = await withRetry(async () => mssql.connect(this.config));
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.close();
      this.pool = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000, excludeLargeColumns: boolean = false): Promise<QueryResult> {
    await this.connect();
    
    let limitedSql = sql.trim();
    if (!limitedSql.toLowerCase().includes('top ') && limitedSql.toLowerCase().startsWith('select')) {
      limitedSql = limitedSql.replace(/^select/i, `SELECT TOP ${maxRows}`);
    }

    const result = await withTimeout(
      this.pool!.request().query(limitedSql),
      this.queryTimeout
    );

    let columns = result.recordset.columns ? Object.keys(result.recordset.columns) : [];
    let rows: any[] = Array.from(result.recordset);

    if (excludeLargeColumns && result.recordset.columns) {
      const largeTypes = ['VarBinary', 'Binary', 'Image', 'Text', 'NText'];
      const filteredColumns = columns.filter((col: any) => {
        const colInfo = result.recordset.columns[col];
        const typeName = typeof colInfo.type === 'function' ? 'unknown' : (colInfo.type as any).name || 'unknown';
        return !largeTypes.includes(typeName);
      });
      
      rows = rows.map((row: any) => {
        const filtered: any = {};
        filteredColumns.forEach((col: any) => filtered[col] = row[col]);
        return filtered;
      });
      columns = filteredColumns;
    }

    return {
      rows,
      rowCount: rows.length,
      columns,
    };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    const columnQuery = `
      SELECT 
        c.COLUMN_NAME,
        c.DATA_TYPE,
        c.CHARACTER_MAXIMUM_LENGTH,
        c.NUMERIC_PRECISION,
        c.NUMERIC_SCALE,
        c.IS_NULLABLE,
        c.COLUMN_DEFAULT,
        CASE WHEN pk.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END as IS_PRIMARY_KEY,
        CASE WHEN fk.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END as IS_FOREIGN_KEY
      FROM INFORMATION_SCHEMA.COLUMNS c
      LEFT JOIN (
        SELECT kcu.COLUMN_NAME, kcu.TABLE_NAME, kcu.TABLE_SCHEMA
        FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
        JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu 
          ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY'
      ) pk ON c.COLUMN_NAME = pk.COLUMN_NAME 
        AND c.TABLE_NAME = pk.TABLE_NAME 
        AND c.TABLE_SCHEMA = pk.TABLE_SCHEMA
      LEFT JOIN (
        SELECT kcu.COLUMN_NAME, kcu.TABLE_NAME, kcu.TABLE_SCHEMA
        FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
        JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu 
          ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_TYPE = 'FOREIGN KEY'
      ) fk ON c.COLUMN_NAME = fk.COLUMN_NAME 
        AND c.TABLE_NAME = fk.TABLE_NAME 
        AND c.TABLE_SCHEMA = fk.TABLE_SCHEMA
      WHERE c.TABLE_NAME = @tableName AND c.TABLE_SCHEMA = @schemaName
      ORDER BY c.ORDINAL_POSITION
    `;

    const request = this.pool!.request();
    request.input('tableName', mssql.VarChar, tableName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const columnResult = await request.query(columnQuery);

    const countQuery = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    const countResult = await this.pool!.request().query(countQuery);
    const rowCount = countResult.recordset[0].count;

    const columns: ColumnInfo[] = columnResult.recordset.map((col: any) => ({
      name: col.COLUMN_NAME,
      type: col.DATA_TYPE,
      length: col.CHARACTER_MAXIMUM_LENGTH,
      precision: col.NUMERIC_PRECISION,
      scale: col.NUMERIC_SCALE,
      nullable: col.IS_NULLABLE === 'YES',
      defaultValue: col.COLUMN_DEFAULT,
      isPrimaryKey: col.IS_PRIMARY_KEY === 1,
      isForeignKey: col.IS_FOREIGN_KEY === 1,
    }));

    return {
      tableName,
      schema: schemaName,
      rowCount,
      columns,
    };
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    
    let query = `
      SELECT 
        t.TABLE_NAME,
        p.rows as ROW_COUNT
      FROM INFORMATION_SCHEMA.TABLES t
      LEFT JOIN sys.tables st ON t.TABLE_NAME = st.name
      LEFT JOIN sys.partitions p ON st.object_id = p.object_id AND p.index_id IN (0, 1)
      WHERE t.TABLE_SCHEMA = @schemaName AND t.TABLE_TYPE = 'BASE TABLE'
    `;

    if (pattern) {
      query += ` AND t.TABLE_NAME LIKE @pattern`;
    }

    query += ` ORDER BY t.TABLE_NAME`;

    const request = this.pool!.request();
    request.input('schemaName', mssql.VarChar, schemaName);
    
    if (pattern) {
      request.input('pattern', mssql.VarChar, pattern);
    }

    const result = await request.query(query);

    return result.recordset.map((row: any) => ({
      name: row.TABLE_NAME,
      rowCount: row.ROW_COUNT,
      lastAnalyzed: null,
    }));
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    let query = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    
    if (whereClause) {
      query += ` WHERE ${whereClause}`;
    }

    const result = await this.pool!.request().query(query);
    return result.recordset[0].count;
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    await this.connect();
    
    const query = `
      SELECT 
        s.name as schema_name,
        (SELECT COUNT(*) FROM sys.tables t WHERE t.schema_id = s.schema_id) as table_count,
        (SELECT COUNT(*) FROM sys.views v WHERE v.schema_id = s.schema_id) as view_count
      FROM sys.schemas s
      WHERE s.name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', 'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', 'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter')
      ORDER BY s.name
    `;

    const result = await this.pool!.request().query(query);

    return result.recordset.map((row: any) => ({
      name: row.schema_name,
      tableCount: row.table_count,
      viewCount: row.view_count,
    }));
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    
    let query = `
      SELECT TABLE_NAME as view_name, TABLE_SCHEMA as schema_name
      FROM INFORMATION_SCHEMA.VIEWS
      WHERE TABLE_SCHEMA = @schemaName
    `;

    const request = this.pool!.request();
    request.input('schemaName', mssql.VarChar, schemaName);
    
    if (pattern) {
      query += ` AND TABLE_NAME LIKE @pattern`;
      request.input('pattern', mssql.VarChar, pattern);
    }

    query += ` ORDER BY TABLE_NAME`;

    const result = await request.query(query);

    return result.recordset.map((row: any) => ({
      name: row.view_name,
      schema: row.schema_name,
    }));
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    const query = `
      SELECT 
        v.TABLE_NAME as view_name,
        v.TABLE_SCHEMA as schema_name,
        m.definition as view_definition
      FROM INFORMATION_SCHEMA.VIEWS v
      INNER JOIN sys.views sv ON v.TABLE_NAME = sv.name
      INNER JOIN sys.schemas s ON sv.schema_id = s.schema_id AND s.name = v.TABLE_SCHEMA
      INNER JOIN sys.sql_modules m ON sv.object_id = m.object_id
      WHERE v.TABLE_NAME = @viewName AND v.TABLE_SCHEMA = @schemaName
    `;

    const request = this.pool!.request();
    request.input('viewName', mssql.VarChar, viewName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const result = await request.query(query);

    if (result.recordset.length === 0) {
      throw new Error(`View ${schemaName}.${viewName} not found`);
    }

    const row = result.recordset[0];

    return {
      name: row.view_name,
      schema: row.schema_name,
      definition: row.view_definition,
    };
  }

  async getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    const query = `
      SELECT 
        i.name as index_name,
        t.name as table_name,
        i.is_unique,
        i.type_desc as index_type,
        STRING_AGG(c.name, ', ') as columns
      FROM sys.indexes i
      INNER JOIN sys.tables t ON i.object_id = t.object_id
      INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
      INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
      INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
      WHERE t.name = @tableName AND s.name = @schemaName
      GROUP BY i.name, t.name, i.is_unique, i.type_desc
      ORDER BY i.name
    `;

    const request = this.pool!.request();
    request.input('tableName', mssql.VarChar, tableName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const result = await request.query(query);

    return result.recordset.map((row: any) => ({
      indexName: row.index_name,
      tableName: row.table_name,
      columns: row.columns.split(', '),
      isUnique: row.is_unique,
      indexType: row.index_type,
      schema: schemaName,
    }));
  }

  async getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    const query = `
      SELECT 
        fk.name as constraint_name,
        tp.name as table_name,
        cp.name as column_name,
        tr.name as referenced_table,
        cr.name as referenced_column,
        fk.delete_referential_action_desc as on_delete,
        fk.update_referential_action_desc as on_update
      FROM sys.foreign_keys fk
      INNER JOIN sys.tables tp ON fk.parent_object_id = tp.object_id
      INNER JOIN sys.schemas sp ON tp.schema_id = sp.schema_id
      INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
      INNER JOIN sys.columns cp ON fkc.parent_object_id = cp.object_id AND fkc.parent_column_id = cp.column_id
      INNER JOIN sys.tables tr ON fk.referenced_object_id = tr.object_id
      INNER JOIN sys.columns cr ON fkc.referenced_object_id = cr.object_id AND fkc.referenced_column_id = cr.column_id
      WHERE tp.name = @tableName AND sp.name = @schemaName
      ORDER BY fk.name
    `;

    const request = this.pool!.request();
    request.input('tableName', mssql.VarChar, tableName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const result = await request.query(query);

    return result.recordset.map((row: any) => ({
      constraintName: row.constraint_name,
      tableName: row.table_name,
      columnName: row.column_name,
      referencedTable: row.referenced_table,
      referencedColumn: row.referenced_column,
      onDelete: row.on_delete,
      onUpdate: row.on_update,
      schema: schemaName,
    }));
  }

  async listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    
    let query = `
      SELECT 
        p.name,
        s.name as schema_name,
        CASE p.type
          WHEN 'P' THEN 'PROCEDURE'
          WHEN 'FN' THEN 'FUNCTION'
          WHEN 'IF' THEN 'FUNCTION'
          WHEN 'TF' THEN 'FUNCTION'
          ELSE 'PROCEDURE'
        END as type
      FROM sys.objects p
      INNER JOIN sys.schemas s ON p.schema_id = s.schema_id
      WHERE p.type IN ('P', 'FN', 'IF', 'TF') AND s.name = @schemaName
    `;

    const request = this.pool!.request();
    request.input('schemaName', mssql.VarChar, schemaName);
    
    if (pattern) {
      query += ` AND p.name LIKE @pattern`;
      request.input('pattern', mssql.VarChar, pattern);
    }

    query += ` ORDER BY p.name`;

    const result = await request.query(query);

    return result.recordset.map((row: any) => ({
      name: row.name,
      schema: row.schema_name,
      type: row.type as 'PROCEDURE' | 'FUNCTION',
    }));
  }

  async getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    const query = `
      SELECT 
        t.name as table_name,
        SUM(p.rows) as row_count,
        SUM(a.total_pages) * 8 / 1024.0 as size_mb,
        SUM(a.used_pages) * 8 / 1024.0 - SUM(CASE WHEN p.index_id IN (0,1) THEN a.used_pages ELSE 0 END) * 8 / 1024.0 as index_size_mb,
        STATS_DATE(t.object_id, i.index_id) as last_analyzed
      FROM sys.tables t
      INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
      INNER JOIN sys.partitions p ON t.object_id = p.object_id
      INNER JOIN sys.allocation_units a ON p.partition_id = a.container_id
      LEFT JOIN sys.indexes i ON t.object_id = i.object_id AND i.index_id IN (0,1)
      WHERE t.name = @tableName AND s.name = @schemaName
      GROUP BY t.name, t.object_id, i.index_id
    `;

    const request = this.pool!.request();
    request.input('tableName', mssql.VarChar, tableName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const result = await request.query(query);

    if (result.recordset.length === 0) {
      throw new Error(`Table ${schemaName}.${tableName} not found`);
    }

    const row = result.recordset[0];

    return {
      tableName: row.table_name,
      schema: schemaName,
      rowCount: row.row_count || 0,
      sizeInMB: row.size_mb || 0,
      indexSizeInMB: row.index_size_mb || 0,
      lastAnalyzed: row.last_analyzed,
    };
  }

  async explainQuery(sql: string): Promise<ExplainPlan> {
    await this.connect();
    
    await this.pool!.request().query('SET SHOWPLAN_TEXT ON');
    
    try {
      const result = await this.pool!.request().query(sql);
      const plan = result.recordset.map((row: any) => row['SHOWPLAN TEXT'] || row['StmtText']).join('\n');
      
      return {
        query: sql,
        plan,
      };
    } finally {
      await this.pool!.request().query('SET SHOWPLAN_TEXT OFF');
    }
  }

  async sampleTableData(tableName: string, schema?: string, limit: number = 10, random: boolean = false): Promise<QueryResult> {
    const schemaName = schema || 'dbo';

    let sql = `SELECT TOP ${limit} * FROM ${schemaName}.${tableName}`;
    
    if (random) {
      sql += ` ORDER BY NEWID()`;
    }
    
    return await this.executeQuery(sql, limit);
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      await this.pool!.request().query('SELECT 1');
      return true;
    } catch {
      return false;
    }
  }

  async *executeQueryStream(sql: string, batchSize: number = 100): AsyncGenerator<any[], void, unknown> {
    await this.connect();
    
    // SQL Server streaming - fetch all and yield in batches
    // (mssql library streaming is complex with events)
    const result = await this.pool!.request().query(sql);
    const rows = Array.from(result.recordset);
    
    for (let i = 0; i < rows.length; i += batchSize) {
      yield rows.slice(i, i + batchSize);
    }
  }

  async executeTransaction(queries: string[]): Promise<QueryResult[]> {
    await this.connect();
    const transaction = this.pool!.transaction();
    
    try {
      await transaction.begin();
      
      const results: QueryResult[] = [];
      
      for (const sql of queries) {
        const request = transaction.request();
        const result = await request.query(sql);
        
        results.push({
          rows: Array.from(result.recordset),
          rowCount: result.recordset.length,
          columns: result.recordset.columns ? Object.keys(result.recordset.columns) : [],
        });
      }
      
      await transaction.commit();
      
      return results;
    } catch (error) {
      await transaction.rollback();
      throw error;
    }
  }
}

// MySQL/MariaDB Database Adapter with Connection Pooling
export class MySQLAdapter extends DatabaseAdapter {
  private pool: mysql.Pool | null = null;
  private config: mysql.PoolOptions;

  constructor(config: {
    host: string;
    port: number;
    user: string;
    password: string;
    database: string;
  }) {
    super();
    this.config = {
      host: config.host,
      port: config.port,
      user: config.user,
      password: config.password,
      database: config.database,
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0,
    };
  }

  async connect(): Promise<void> {
    if (this.pool) return;
    
    this.pool = await withRetry(async () => mysql.createPool(this.config));
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.end();
      this.pool = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000, excludeLargeColumns: boolean = false): Promise<QueryResult> {
    await this.connect();
    
    const connection = await this.pool!.getConnection();
    try {
      const [rows, fields] = await withTimeout(
        connection.query(sql),
        this.queryTimeout
      ) as any;

      let columns = fields.map((f: any) => f.name);
      let resultRows = Array.isArray(rows) ? rows.slice(0, maxRows) : [];

      // Filter out large columns if requested
      if (excludeLargeColumns && fields) {
        const largeTypes = ['BLOB', 'TEXT', 'MEDIUMBLOB', 'MEDIUMTEXT', 'LONGBLOB', 'LONGTEXT'];
        const filteredColumns = columns.filter((_: any, i: number) =>
          !largeTypes.includes(fields[i].type?.toString() || '')
        );
        
        resultRows = resultRows.map((row: any) => {
          const filtered: any = {};
          filteredColumns.forEach((col: string) => filtered[col] = row[col]);
          return filtered;
        });
        columns = filteredColumns;
      }

      return { rows: resultRows, rowCount: resultRows.length, columns };
    } finally {
      connection.release();
    }
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    const dbName = schema || this.config.database;

    const columnQuery = `
      SELECT 
        c.COLUMN_NAME,
        c.DATA_TYPE,
        c.CHARACTER_MAXIMUM_LENGTH as DATA_LENGTH,
        c.NUMERIC_PRECISION as DATA_PRECISION,
        c.NUMERIC_SCALE as DATA_SCALE,
        c.IS_NULLABLE,
        c.COLUMN_DEFAULT,
        CASE WHEN k.CONSTRAINT_NAME IS NOT NULL THEN 'YES' ELSE 'NO' END as IS_PRIMARY_KEY,
        CASE WHEN fk.CONSTRAINT_NAME IS NOT NULL THEN 'YES' ELSE 'NO' END as IS_FOREIGN_KEY
      FROM INFORMATION_SCHEMA.COLUMNS c
      LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k 
        ON c.TABLE_SCHEMA = k.TABLE_SCHEMA 
        AND c.TABLE_NAME = k.TABLE_NAME 
        AND c.COLUMN_NAME = k.COLUMN_NAME
        AND k.CONSTRAINT_NAME = 'PRIMARY'
      LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE fk
        ON c.TABLE_SCHEMA = fk.TABLE_SCHEMA
        AND c.TABLE_NAME = fk.TABLE_NAME
        AND c.COLUMN_NAME = fk.COLUMN_NAME
        AND fk.REFERENCED_TABLE_NAME IS NOT NULL
      WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ?
      ORDER BY c.ORDINAL_POSITION
    `;

    const connection = await this.pool!.getConnection();
    try {
      const [rows] = await connection.query(columnQuery, [dbName, tableName]) as any;
      
      const columns: ColumnInfo[] = rows.map((row: any) => ({
        name: row.COLUMN_NAME,
        type: row.DATA_TYPE,
        length: row.DATA_LENGTH,
        precision: row.DATA_PRECISION,
        scale: row.DATA_SCALE,
        nullable: row.IS_NULLABLE === 'YES',
        defaultValue: row.COLUMN_DEFAULT,
        isPrimaryKey: row.IS_PRIMARY_KEY === 'YES',
        isForeignKey: row.IS_FOREIGN_KEY === 'YES',
      }));

      const countQuery = `SELECT COUNT(*) as cnt FROM \`${dbName}\`.\`${tableName}\``;
      const [countResult] = await connection.query(countQuery) as any;
      const rowCount = countResult[0]?.cnt || 0;

      return {
        tableName,
        schema: dbName,
        rowCount,
        columns,
      };
    } finally {
      connection.release();
    }
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();
    const dbName = schema || this.config.database;

    let sql = `
      SELECT TABLE_NAME as name, TABLE_ROWS as rowCount, UPDATE_TIME as lastAnalyzed
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
    `;

    if (pattern) {
      sql += ` AND TABLE_NAME LIKE ?`;
    }
    sql += ` ORDER BY TABLE_NAME`;

    const connection = await this.pool!.getConnection();
    try {
      const params = pattern ? [dbName, `%${pattern}%`] : [dbName];
      const [rows] = await connection.query(sql, params) as any;
      
      return rows.map((row: any) => ({
        name: row.name,
        rowCount: row.rowCount,
        lastAnalyzed: row.lastAnalyzed,
      }));
    } finally {
      connection.release();
    }
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    const dbName = schema || this.config.database;

    let sql = `SELECT COUNT(*) as cnt FROM \`${dbName}\`.\`${tableName}\``;
    if (whereClause) {
      sql += ` WHERE ${whereClause}`;
    }

    const result = await this.executeQuery(sql, 1);
    return result.rows[0]?.cnt || 0;
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    await this.connect();

    const sql = `
      SELECT 
        SCHEMA_NAME as name,
        DEFAULT_CHARACTER_SET_NAME as charset,
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES t WHERE t.TABLE_SCHEMA = s.SCHEMA_NAME AND t.TABLE_TYPE = 'BASE TABLE') as tableCount,
        (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES v WHERE v.TABLE_SCHEMA = s.SCHEMA_NAME AND v.TABLE_TYPE = 'VIEW') as viewCount
      FROM INFORMATION_SCHEMA.SCHEMATA s
      ORDER BY SCHEMA_NAME
    `;

    const result = await this.executeQuery(sql);
    return result.rows.map((row: any) => ({
      name: row.name,
      tableCount: row.tableCount,
      viewCount: row.viewCount,
    }));
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    const dbName = schema || this.config.database;

    let sql = `
      SELECT TABLE_NAME as name, TABLE_SCHEMA as schema
      FROM INFORMATION_SCHEMA.VIEWS
      WHERE TABLE_SCHEMA = ?
    `;

    if (pattern) {
      sql += ` AND TABLE_NAME LIKE ?`;
    }
    sql += ` ORDER BY TABLE_NAME`;

    const connection = await this.pool!.getConnection();
    try {
      const params = pattern ? [dbName, `%${pattern}%`] : [dbName];
      const [rows] = await connection.query(sql, params) as any;
      
      return rows.map((row: any) => ({
        name: row.name,
        schema: row.schema,
      }));
    } finally {
      connection.release();
    }
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    const dbName = schema || this.config.database;

    const sql = `
      SELECT VIEW_DEFINITION as definition
      FROM INFORMATION_SCHEMA.VIEWS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
    `;

    const connection = await this.pool!.getConnection();
    try {
      const [rows] = await connection.query(sql, [dbName, viewName]) as any;
      
      if (!rows || rows.length === 0) {
        throw new Error(`View ${viewName} not found`);
      }

      return {
        name: viewName,
        schema: dbName,
        definition: rows[0].definition,
      };
    } finally {
      connection.release();
    }
  }

  async getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]> {
    await this.connect();
    const dbName = schema || this.config.database;

    const sql = `
      SELECT 
        INDEX_NAME as indexName,
        TABLE_NAME as tableName,
        GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) as columns,
        CASE WHEN NON_UNIQUE = 0 THEN 1 ELSE 0 END as isUnique,
        INDEX_TYPE as indexType
      FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
      GROUP BY INDEX_NAME, TABLE_NAME, NON_UNIQUE, INDEX_TYPE
      ORDER BY INDEX_NAME
    `;

    const connection = await this.pool!.getConnection();
    try {
      const [rows] = await connection.query(sql, [dbName, tableName]) as any;
      
      return rows.map((row: any) => ({
        indexName: row.indexName,
        tableName: row.tableName,
        columns: row.columns.split(','),
        isUnique: row.isUnique === 1,
        indexType: row.indexType,
        schema: dbName,
      }));
    } finally {
      connection.release();
    }
  }

  async getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]> {
    await this.connect();
    const dbName = schema || this.config.database;

    const sql = `
      SELECT 
        k.CONSTRAINT_NAME as constraintName,
        k.TABLE_NAME as tableName,
        k.COLUMN_NAME as columnName,
        k.REFERENCED_TABLE_NAME as referencedTable,
        k.REFERENCED_COLUMN_NAME as referencedColumn,
        r.UPDATE_RULE as onUpdate,
        r.DELETE_RULE as onDelete
      FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
      JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS r
        ON k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
        AND k.TABLE_SCHEMA = r.CONSTRAINT_SCHEMA
      WHERE k.TABLE_SCHEMA = ? AND k.TABLE_NAME = ?
        AND k.REFERENCED_TABLE_NAME IS NOT NULL
      ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION
    `;

    const connection = await this.pool!.getConnection();
    try {
      const [rows] = await connection.query(sql, [dbName, tableName]) as any;
      
      return rows.map((row: any) => ({
        constraintName: row.constraintName,
        tableName: row.tableName,
        columnName: row.columnName,
        referencedTable: row.referencedTable,
        referencedColumn: row.referencedColumn,
        onUpdate: row.onUpdate,
        onDelete: row.onDelete,
        schema: dbName,
      }));
    } finally {
      connection.release();
    }
  }

  async listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]> {
    await this.connect();
    const dbName = schema || this.config.database;

    let sql = `
      SELECT 
        ROUTINE_NAME as name,
        ROUTINE_SCHEMA as schema,
        ROUTINE_TYPE as type,
        DTD_IDENTIFIER as returnType
      FROM INFORMATION_SCHEMA.ROUTINES
      WHERE ROUTINE_SCHEMA = ?
    `;

    if (pattern) {
      sql += ` AND ROUTINE_NAME LIKE ?`;
    }
    sql += ` ORDER BY ROUTINE_NAME`;

    const connection = await this.pool!.getConnection();
    try {
      const params = pattern ? [dbName, `%${pattern}%`] : [dbName];
      const [rows] = await connection.query(sql, params) as any;
      
      return rows.map((row: any) => ({
        name: row.name,
        schema: row.schema,
        type: row.type as 'PROCEDURE' | 'FUNCTION',
        returnType: row.returnType,
      }));
    } finally {
      connection.release();
    }
  }

  async getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics> {
    await this.connect();
    const dbName = schema || this.config.database;

    const sql = `
      SELECT 
        TABLE_NAME as tableName,
        TABLE_SCHEMA as schema,
        TABLE_ROWS as rowCount,
        ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) as sizeInMB,
        ROUND(INDEX_LENGTH / 1024 / 1024, 2) as indexSizeInMB,
        UPDATE_TIME as lastAnalyzed
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
    `;

    const connection = await this.pool!.getConnection();
    try {
      const [rows] = await connection.query(sql, [dbName, tableName]) as any;
      
      if (!rows || rows.length === 0) {
        throw new Error(`Table ${tableName} not found`);
      }

      const row = rows[0];
      return {
        tableName: row.tableName,
        schema: row.schema,
        rowCount: row.rowCount || 0,
        sizeInMB: row.sizeInMB,
        indexSizeInMB: row.indexSizeInMB,
        lastAnalyzed: row.lastAnalyzed,
      };
    } finally {
      connection.release();
    }
  }

  async explainQuery(sql: string): Promise<ExplainPlan> {
    await this.connect();

    const explainSql = `EXPLAIN FORMAT=JSON ${sql}`;
    const result = await this.executeQuery(explainSql, 1);
    
    const explainData = JSON.parse(result.rows[0]['EXPLAIN']);
    const plan = JSON.stringify(explainData, null, 2);
    
    return {
      query: sql,
      plan,
      estimatedCost: explainData.query_block?.cost_info?.query_cost,
      estimatedRows: explainData.query_block?.cost_info?.read_cost,
    };
  }

  async sampleTableData(tableName: string, schema?: string, limit: number = 10, random: boolean = false): Promise<QueryResult> {
    const dbName = schema || this.config.database;

    let sql = `SELECT * FROM \`${dbName}\`.\`${tableName}\``;
    
    if (random) {
      sql += ` ORDER BY RAND()`;
    }
    
    sql += ` LIMIT ${limit}`;
    
    return await this.executeQuery(sql, limit);
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      const connection = await this.pool!.getConnection();
      await connection.query('SELECT 1');
      connection.release();
      return true;
    } catch {
      return false;
    }
  }

  async *executeQueryStream(sql: string, batchSize: number = 100): AsyncGenerator<any[], void, unknown> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      // Fetch all rows and yield in batches
      const [rows] = await connection.query(sql) as any;
      const allRows = Array.isArray(rows) ? rows : [];
      
      for (let i = 0; i < allRows.length; i += batchSize) {
        yield allRows.slice(i, i + batchSize);
      }
    } finally {
      connection.release();
    }
  }

  async executeTransaction(queries: string[]): Promise<QueryResult[]> {
    await this.connect();
    const connection = await this.pool!.getConnection();
    
    try {
      await connection.beginTransaction();
      
      const results: QueryResult[] = [];
      
      for (const sql of queries) {
        const [rows, fields] = await connection.query(sql) as any;
        results.push({
          rows: Array.isArray(rows) ? rows : [],
          rowCount: Array.isArray(rows) ? rows.length : 0,
          columns: fields.map((f: any) => f.name),
        });
      }
      
      await connection.commit();
      
      return results;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }
}

// SQLite Database Adapter (File-based, no connection pooling)
export class SQLiteAdapter extends DatabaseAdapter {
  private db: Database.Database | null = null;
  private dbPath: string;

  constructor(config: { path: string }) {
    super();
    this.dbPath = config.path;
  }

  async connect(): Promise<void> {
    if (this.db) return;
    
    this.db = await withRetry(async () => new Database(this.dbPath, { readonly: true }));
  }

  async disconnect(): Promise<void> {
    if (this.db) {
      this.db.close();
      this.db = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000, excludeLargeColumns: boolean = false): Promise<QueryResult> {
    await this.connect();
    
    const stmt = this.db!.prepare(sql);
    let rows = stmt.all() as any[];
    
    if (rows.length > maxRows) {
      rows = rows.slice(0, maxRows);
    }

    const columns = rows.length > 0 ? Object.keys(rows[0] as object) : [];

    // Filter out large columns if requested (TEXT/BLOB types)
    if (excludeLargeColumns && rows.length > 0) {
      const filteredColumns = columns.filter(col => {
        const val = (rows[0] as any)[col];
        return !(typeof val === 'string' && val.length > 10000) && !Buffer.isBuffer(val);
      });
      
      rows = rows.map((row: any) => {
        const filtered: any = {};
        filteredColumns.forEach(col => filtered[col] = row[col]);
        return filtered;
      });
      
      return { rows, rowCount: rows.length, columns: filteredColumns };
    }

    return { rows, rowCount: rows.length, columns };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();

    const pragma = `PRAGMA table_info(${tableName})`;
    const columns = this.db!.prepare(pragma).all() as any[];

    // Get primary keys
    const pkInfo = this.db!.prepare(`PRAGMA table_info(${tableName})`).all() as any[];
    const primaryKeys = new Set(pkInfo.filter((col: any) => col.pk).map((col: any) => col.name));

    // Get foreign keys
    const fkInfo = this.db!.prepare(`PRAGMA foreign_key_list(${tableName})`).all() as any[];
    const foreignKeys = new Set(fkInfo.map((fk: any) => fk.from));

    const columnInfo: ColumnInfo[] = columns.map((col: any) => ({
      name: col.name,
      type: col.type,
      nullable: !col.notnull,
      defaultValue: col.dflt_value,
      isPrimaryKey: primaryKeys.has(col.name),
      isForeignKey: foreignKeys.has(col.name),
    }));

    const countQuery = `SELECT COUNT(*) as cnt FROM ${tableName}`;
    const countResult = this.db!.prepare(countQuery).get() as any;
    const rowCount = countResult?.cnt || 0;

    return {
      tableName,
      rowCount,
      columns: columnInfo,
    };
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();

    let sql = `
      SELECT name 
      FROM sqlite_master 
      WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
    `;

    if (pattern) {
      sql += ` AND name LIKE '%${pattern}%'`;
    }
    sql += ` ORDER BY name`;

    const tables = this.db!.prepare(sql).all() as any[];

    return tables.map((table: any) => {
      const countQuery = `SELECT COUNT(*) as cnt FROM ${table.name}`;
      const countResult = this.db!.prepare(countQuery).get() as any;
      
      return {
        name: table.name,
        rowCount: countResult?.cnt || 0,
      };
    });
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();

    let sql = `SELECT COUNT(*) as cnt FROM ${tableName}`;
    if (whereClause) {
      sql += ` WHERE ${whereClause}`;
    }

    const result = this.db!.prepare(sql).get() as any;
    return result?.cnt || 0;
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    // SQLite doesn't have schemas in the traditional sense
    return [
      {
        name: 'main',
        tableCount: (await this.listTables()).length,
      },
    ];
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();

    let sql = `
      SELECT name 
      FROM sqlite_master 
      WHERE type = 'view'
    `;

    if (pattern) {
      sql += ` AND name LIKE '%${pattern}%'`;
    }
    sql += ` ORDER BY name`;

    const views = this.db!.prepare(sql).all() as any[];

    return views.map((view: any) => ({
      name: view.name,
    }));
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();

    const sql = `
      SELECT sql as definition 
      FROM sqlite_master 
      WHERE type = 'view' AND name = ?
    `;

    const result = this.db!.prepare(sql).get(viewName) as any;

    if (!result) {
      throw new Error(`View ${viewName} not found`);
    }

    return {
      name: viewName,
      definition: result.definition,
    };
  }

  async getIndexes(tableName: string, schema?: string): Promise<IndexInfo[]> {
    await this.connect();

    const indexList = this.db!.prepare(`PRAGMA index_list(${tableName})`).all() as any[];

    const indexes: IndexInfo[] = [];
    for (const idx of indexList) {
      const indexInfo = this.db!.prepare(`PRAGMA index_info(${idx.name})`).all() as any[];
      const columns = indexInfo.map((col: any) => col.name);

      indexes.push({
        indexName: idx.name,
        tableName,
        columns,
        isUnique: idx.unique === 1,
        indexType: idx.origin === 'pk' ? 'PRIMARY' : 'INDEX',
      });
    }

    return indexes;
  }

  async getForeignKeys(tableName: string, schema?: string): Promise<ForeignKeyInfo[]> {
    await this.connect();

    const fkList = this.db!.prepare(`PRAGMA foreign_key_list(${tableName})`).all() as any[];

    return fkList.map((fk: any) => ({
      constraintName: `FK_${tableName}_${fk.from}`,
      tableName,
      columnName: fk.from,
      referencedTable: fk.table,
      referencedColumn: fk.to,
      onUpdate: fk.on_update,
      onDelete: fk.on_delete,
    }));
  }

  async listStoredProcedures(schema?: string, pattern?: string): Promise<StoredProcedureInfo[]> {
    // SQLite doesn't support stored procedures
    return [];
  }

  async getTableStatistics(tableName: string, schema?: string): Promise<TableStatistics> {
    await this.connect();

    const countQuery = `SELECT COUNT(*) as cnt FROM ${tableName}`;
    const countResult = this.db!.prepare(countQuery).get() as any;
    const rowCount = countResult?.cnt || 0;

    // Get page count and calculate size
    const pageInfo = this.db!.prepare(`PRAGMA page_count`).get() as any;
    const pageSize = this.db!.prepare(`PRAGMA page_size`).get() as any;
    const sizeInMB = (pageInfo.page_count * pageSize.page_size) / 1024 / 1024;

    return {
      tableName,
      rowCount,
      sizeInMB,
    };
  }

  async explainQuery(sql: string): Promise<ExplainPlan> {
    await this.connect();

    const explainSql = `EXPLAIN QUERY PLAN ${sql}`;
    const result = this.db!.prepare(explainSql).all() as any[];
    
    const plan = result.map((row: any) => row.detail).join('\n');
    
    return {
      query: sql,
      plan,
    };
  }

  async sampleTableData(tableName: string, schema?: string, limit: number = 10, random: boolean = false): Promise<QueryResult> {
    let sql = `SELECT * FROM ${tableName}`;
    
    if (random) {
      sql += ` ORDER BY RANDOM()`;
    }
    
    sql += ` LIMIT ${limit}`;
    
    return await this.executeQuery(sql, limit);
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      this.db!.prepare('SELECT 1').get();
      return true;
    } catch {
      return false;
    }
  }

  async *executeQueryStream(sql: string, batchSize: number = 100): AsyncGenerator<any[], void, unknown> {
    await this.connect();
    
    const stmt = this.db!.prepare(sql);
    const rows = stmt.all() as any[];
    
    for (let i = 0; i < rows.length; i += batchSize) {
      yield rows.slice(i, i + batchSize);
    }
  }

  async executeTransaction(queries: string[]): Promise<QueryResult[]> {
    await this.connect();
    
    // SQLite doesn't need explicit transactions for read-only queries
    // but we can still group them
    const results: QueryResult[] = [];
    
    for (const sql of queries) {
      const stmt = this.db!.prepare(sql);
      const rows = stmt.all() as any[];
      const columns = rows.length > 0 ? Object.keys(rows[0] as object) : [];
      
      results.push({
        rows,
        rowCount: rows.length,
        columns,
      });
    }
    
    return results;
  }
}

// Factory function to create appropriate adapter based on database type
export function createDatabaseAdapter(dbType: string, config: any): DatabaseAdapter {
  switch (dbType.toLowerCase()) {
    case 'oracle':
      return new OracleAdapter(config);
    case 'postgres':
    case 'postgresql':
      return new PostgresAdapter(config);
    case 'sqlserver':
    case 'mssql':
      return new SQLServerAdapter(config);
    case 'mysql':
    case 'mariadb':
      return new MySQLAdapter(config);
    case 'sqlite':
    case 'sqlite3':
      return new SQLiteAdapter(config);
    default:
      throw new Error(`Unsupported database type: ${dbType}`);
  }
}
