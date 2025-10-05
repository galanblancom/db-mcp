// Database adapter interface and implementations for multi-database support
import oracledb, { Connection as OracleConnection } from "oracledb";
import pg from "pg";
import mssql from "mssql";

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

// Abstract base class for database adapters
export abstract class DatabaseAdapter {
  abstract connect(): Promise<void>;
  abstract disconnect(): Promise<void>;
  abstract executeQuery(sql: string, maxRows?: number): Promise<QueryResult>;
  abstract getTableInfo(tableName: string, schema?: string): Promise<TableInfo>;
  abstract listTables(schema?: string, pattern?: string): Promise<TableListItem[]>;
  abstract getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number>;
  abstract listSchemas(): Promise<SchemaInfo[]>;
  abstract listViews(schema?: string, pattern?: string): Promise<ViewInfo[]>;
  abstract getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition>;
  abstract testConnection(): Promise<boolean>;
}

// Oracle Database Adapter
export class OracleAdapter extends DatabaseAdapter {
  private connection: OracleConnection | null = null;
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

    // Initialize Oracle Client in Thick mode if required
    if (this.config.thickMode && this.config.libDir) {
      try {
        (oracledb as any).initOracleClient({ libDir: this.config.libDir });
        console.error("Oracle Client initialized in Thick mode (supports Oracle 11g)");
      } catch (err: any) {
        console.error("Warning: Could not initialize Oracle Client:", err.message);
        console.error("Thin mode will be used (requires Oracle 12.1+)");
      }
    }
  }

  async connect(): Promise<void> {
    if (this.connection) return;
    
    this.connection = await oracledb.getConnection({
      user: this.config.user,
      password: this.config.password,
      connectString: this.config.connectString,
    });
  }

  async disconnect(): Promise<void> {
    if (this.connection) {
      await this.connection.close();
      this.connection = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000): Promise<QueryResult> {
    await this.connect();
    
    const result = await this.connection!.execute(sql, [], {
      outFormat: oracledb.OUT_FORMAT_OBJECT,
      maxRows: maxRows,
    });

    const columns = result.metaData?.map((m: any) => m.name) || [];
    const rows = (result.rows || []) as any[];

    await this.disconnect();

    return {
      rows,
      rowCount: rows.length,
      columns,
    };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    
    const owner = schema || this.config.user;
    const tableNameUpper = tableName.toUpperCase();

    // Get column information
    const columnQuery = `
      SELECT 
        COLUMN_NAME,
        DATA_TYPE,
        DATA_LENGTH,
        DATA_PRECISION,
        DATA_SCALE,
        NULLABLE,
        DATA_DEFAULT
      FROM ALL_TAB_COLUMNS
      WHERE TABLE_NAME = :tableName
        AND OWNER = :owner
      ORDER BY COLUMN_ID
    `;

    const columnResult = await this.connection!.execute(
      columnQuery,
      { tableName: tableNameUpper, owner: owner.toUpperCase() },
      { outFormat: oracledb.OUT_FORMAT_OBJECT }
    );

    // Get row count
    const countQuery = `SELECT COUNT(*) as ROW_COUNT FROM ${owner}.${tableNameUpper}`;
    const countResult = await this.connection!.execute(countQuery, [], {
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
    }));

    await this.disconnect();

    return {
      tableName: tableNameUpper,
      owner,
      rowCount,
      columns,
    };
  }

  async listTables(schema?: string, pattern?: string): Promise<TableListItem[]> {
    await this.connect();
    
    const owner = schema || this.config.user;
    let query = `
      SELECT 
        TABLE_NAME,
        NUM_ROWS,
        LAST_ANALYZED
      FROM ALL_TABLES
      WHERE OWNER = :owner
    `;

    const binds: any = { owner: owner.toUpperCase() };

    if (pattern) {
      query += ` AND TABLE_NAME LIKE :pattern`;
      binds.pattern = pattern.toUpperCase();
    }

    query += ` ORDER BY TABLE_NAME`;

    const result = await this.connection!.execute(query, binds, {
      outFormat: oracledb.OUT_FORMAT_OBJECT,
    });

    const tables: TableListItem[] = ((result.rows || []) as any[]).map((row: any) => ({
      name: row.TABLE_NAME,
      rowCount: row.NUM_ROWS,
      lastAnalyzed: row.LAST_ANALYZED,
    }));

    await this.disconnect();

    return tables;
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    
    const owner = schema || this.config.user;
    const tableNameUpper = tableName.toUpperCase();

    let query = `SELECT COUNT(*) as ROW_COUNT FROM ${owner}.${tableNameUpper}`;
    
    if (whereClause) {
      query += ` WHERE ${whereClause}`;
    }

    const result = await this.connection!.execute(query, [], {
      outFormat: oracledb.OUT_FORMAT_OBJECT,
    });

    const rowCount = (result.rows as any)?.[0]?.ROW_COUNT || 0;

    await this.disconnect();

    return rowCount;
  }

  async listSchemas(): Promise<SchemaInfo[]> {
    await this.connect();
    
    const query = `
      SELECT 
        username as SCHEMA_NAME,
        (SELECT COUNT(*) FROM all_tables WHERE owner = u.username) as TABLE_COUNT,
        (SELECT COUNT(*) FROM all_views WHERE owner = u.username) as VIEW_COUNT
      FROM all_users u
      ORDER BY username
    `;

    const result = await this.connection!.execute(query, [], {
      outFormat: oracledb.OUT_FORMAT_OBJECT,
    });

    const schemas: SchemaInfo[] = ((result.rows || []) as any[]).map((row: any) => ({
      name: row.SCHEMA_NAME,
      owner: row.SCHEMA_NAME,
      tableCount: row.TABLE_COUNT,
      viewCount: row.VIEW_COUNT,
    }));

    await this.disconnect();

    return schemas;
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    
    const owner = schema || this.config.user;
    
    let query = `
      SELECT 
        VIEW_NAME,
        OWNER
      FROM ALL_VIEWS
      WHERE OWNER = :owner
    `;

    const binds: any = { owner: owner.toUpperCase() };

    if (pattern) {
      query += ` AND VIEW_NAME LIKE :pattern`;
      binds.pattern = pattern.toUpperCase();
    }

    query += ` ORDER BY VIEW_NAME`;

    const result = await this.connection!.execute(query, binds, {
      outFormat: oracledb.OUT_FORMAT_OBJECT,
    });

    const views: ViewInfo[] = ((result.rows || []) as any[]).map((row: any) => ({
      name: row.VIEW_NAME,
      owner: row.OWNER,
    }));

    await this.disconnect();

    return views;
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    
    const owner = schema || this.config.user;
    const viewNameUpper = viewName.toUpperCase();

    const query = `
      SELECT 
        VIEW_NAME,
        OWNER,
        TEXT
      FROM ALL_VIEWS
      WHERE VIEW_NAME = :viewName
        AND OWNER = :owner
    `;

    const result = await this.connection!.execute(
      query,
      { viewName: viewNameUpper, owner: owner.toUpperCase() },
      { outFormat: oracledb.OUT_FORMAT_OBJECT }
    );

    if (!result.rows || result.rows.length === 0) {
      throw new Error(`View ${owner}.${viewNameUpper} not found`);
    }

    const row = (result.rows as any[])[0];

    await this.disconnect();

    return {
      name: row.VIEW_NAME,
      owner: row.OWNER,
      definition: row.TEXT,
    };
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      await this.disconnect();
      return true;
    } catch {
      return false;
    }
  }
}

// PostgreSQL Database Adapter
export class PostgresAdapter extends DatabaseAdapter {
  private client: pg.Client | null = null;
  private config: pg.ClientConfig;

  constructor(config: {
    user: string;
    password: string;
    host: string;
    port: number;
    database: string;
  }) {
    super();
    this.config = config;
  }

  async connect(): Promise<void> {
    if (this.client) return;
    
    this.client = new pg.Client(this.config);
    await this.client.connect();
  }

  async disconnect(): Promise<void> {
    if (this.client) {
      await this.client.end();
      this.client = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000): Promise<QueryResult> {
    await this.connect();
    
    // Add LIMIT clause if not present
    const limitedSql = sql.trim().toLowerCase().includes('limit') 
      ? sql 
      : `${sql} LIMIT ${maxRows}`;

    const result = await this.client!.query(limitedSql);
    const columns = result.fields.map(f => f.name);

    await this.disconnect();

    return {
      rows: result.rows,
      rowCount: result.rows.length,
      columns,
    };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    
    const schemaName = schema || 'public';

    // Get column information from information_schema
    const columnQuery = `
      SELECT 
        column_name,
        data_type,
        character_maximum_length,
        numeric_precision,
        numeric_scale,
        is_nullable,
        column_default
      FROM information_schema.columns
      WHERE table_name = $1
        AND table_schema = $2
      ORDER BY ordinal_position
    `;

    const columnResult = await this.client!.query(columnQuery, [tableName.toLowerCase(), schemaName]);

    // Get row count
    const countQuery = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    const countResult = await this.client!.query(countQuery);
    const rowCount = parseInt(countResult.rows[0].count, 10);

    const columns: ColumnInfo[] = columnResult.rows.map((col: any) => ({
      name: col.column_name,
      type: col.data_type,
      length: col.character_maximum_length,
      precision: col.numeric_precision,
      scale: col.numeric_scale,
      nullable: col.is_nullable === 'YES',
      defaultValue: col.column_default,
    }));

    await this.disconnect();

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
        (SELECT COUNT(*) FROM ${schemaName}."' || table_name || '") as row_count
      FROM information_schema.tables
      WHERE table_schema = $1
        AND table_type = 'BASE TABLE'
    `;

    const params: any[] = [schemaName];

    if (pattern) {
      query += ` AND table_name LIKE $2`;
      params.push(pattern.toLowerCase().replace(/%/g, '%'));
    }

    query += ` ORDER BY table_name`;

    const result = await this.client!.query(query, params);

    // Get row counts separately for each table (more reliable)
    const tables: TableListItem[] = [];
    for (const row of result.rows) {
      try {
        const countResult = await this.client!.query(
          `SELECT COUNT(*) as count FROM ${schemaName}."${row.table_name}"`
        );
        tables.push({
          name: row.table_name,
          rowCount: parseInt(countResult.rows[0].count, 10),
          lastAnalyzed: null,
        });
      } catch {
        tables.push({
          name: row.table_name,
          rowCount: undefined,
          lastAnalyzed: null,
        });
      }
    }

    await this.disconnect();

    return tables;
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    
    const schemaName = schema || 'public';
    let query = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    
    if (whereClause) {
      query += ` WHERE ${whereClause}`;
    }

    const result = await this.client!.query(query);
    const rowCount = parseInt(result.rows[0].count, 10);

    await this.disconnect();

    return rowCount;
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

    const result = await this.client!.query(query);

    const schemas: SchemaInfo[] = result.rows.map((row: any) => ({
      name: row.schema_name,
      tableCount: parseInt(row.table_count, 10),
      viewCount: parseInt(row.view_count, 10),
    }));

    await this.disconnect();

    return schemas;
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'public';
    
    let query = `
      SELECT 
        table_name as view_name,
        table_schema as schema_name
      FROM information_schema.views
      WHERE table_schema = $1
    `;

    const params: any[] = [schemaName];

    if (pattern) {
      query += ` AND table_name LIKE $2`;
      params.push(pattern.toLowerCase());
    }

    query += ` ORDER BY table_name`;

    const result = await this.client!.query(query, params);

    const views: ViewInfo[] = result.rows.map((row: any) => ({
      name: row.view_name,
      schema: row.schema_name,
    }));

    await this.disconnect();

    return views;
  }

  async getViewDefinition(viewName: string, schema?: string): Promise<ViewDefinition> {
    await this.connect();
    
    const schemaName = schema || 'public';

    const query = `
      SELECT 
        table_name as view_name,
        table_schema as schema_name,
        view_definition
      FROM information_schema.views
      WHERE table_name = $1
        AND table_schema = $2
    `;

    const result = await this.client!.query(query, [viewName.toLowerCase(), schemaName]);

    if (result.rows.length === 0) {
      throw new Error(`View ${schemaName}.${viewName} not found`);
    }

    const row = result.rows[0];

    await this.disconnect();

    return {
      name: row.view_name,
      schema: row.schema_name,
      definition: row.view_definition,
    };
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      await this.disconnect();
      return true;
    } catch {
      return false;
    }
  }
}

// SQL Server Database Adapter
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
      options: {
        encrypt: config.encrypt !== false,
        trustServerCertificate: config.trustServerCertificate !== false,
      },
    };
  }

  async connect(): Promise<void> {
    if (this.pool && this.pool.connected) return;
    
    this.pool = await mssql.connect(this.config);
  }

  async disconnect(): Promise<void> {
    if (this.pool) {
      await this.pool.close();
      this.pool = null;
    }
  }

  async executeQuery(sql: string, maxRows: number = 1000): Promise<QueryResult> {
    await this.connect();
    
    // Add TOP clause if not present (SQL Server doesn't use LIMIT)
    let limitedSql = sql.trim();
    if (!limitedSql.toLowerCase().includes('top ') && limitedSql.toLowerCase().startsWith('select')) {
      limitedSql = limitedSql.replace(/^select/i, `SELECT TOP ${maxRows}`);
    }

    const result = await this.pool!.request().query(limitedSql);
    const columns = result.recordset.columns ? Object.keys(result.recordset.columns) : [];

    await this.disconnect();

    return {
      rows: result.recordset,
      rowCount: result.recordset.length,
      columns,
    };
  }

  async getTableInfo(tableName: string, schema?: string): Promise<TableInfo> {
    await this.connect();
    
    const schemaName = schema || 'dbo';

    // Get column information from INFORMATION_SCHEMA
    const columnQuery = `
      SELECT 
        COLUMN_NAME,
        DATA_TYPE,
        CHARACTER_MAXIMUM_LENGTH,
        NUMERIC_PRECISION,
        NUMERIC_SCALE,
        IS_NULLABLE,
        COLUMN_DEFAULT
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_NAME = @tableName
        AND TABLE_SCHEMA = @schemaName
      ORDER BY ORDINAL_POSITION
    `;

    const request = this.pool!.request();
    request.input('tableName', mssql.VarChar, tableName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const columnResult = await request.query(columnQuery);

    // Get row count
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
    }));

    await this.disconnect();

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
      WHERE t.TABLE_SCHEMA = @schemaName
        AND t.TABLE_TYPE = 'BASE TABLE'
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

    const tables: TableListItem[] = result.recordset.map((row: any) => ({
      name: row.TABLE_NAME,
      rowCount: row.ROW_COUNT,
      lastAnalyzed: null,
    }));

    await this.disconnect();

    return tables;
  }

  async getRowCount(tableName: string, schema?: string, whereClause?: string): Promise<number> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    let query = `SELECT COUNT(*) as count FROM ${schemaName}.${tableName}`;
    
    if (whereClause) {
      query += ` WHERE ${whereClause}`;
    }

    const result = await this.pool!.request().query(query);
    const rowCount = result.recordset[0].count;

    await this.disconnect();

    return rowCount;
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

    const schemas: SchemaInfo[] = result.recordset.map((row: any) => ({
      name: row.schema_name,
      tableCount: row.table_count,
      viewCount: row.view_count,
    }));

    await this.disconnect();

    return schemas;
  }

  async listViews(schema?: string, pattern?: string): Promise<ViewInfo[]> {
    await this.connect();
    
    const schemaName = schema || 'dbo';
    
    let query = `
      SELECT 
        TABLE_NAME as view_name,
        TABLE_SCHEMA as schema_name
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

    const views: ViewInfo[] = result.recordset.map((row: any) => ({
      name: row.view_name,
      schema: row.schema_name,
    }));

    await this.disconnect();

    return views;
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
      WHERE v.TABLE_NAME = @viewName
        AND v.TABLE_SCHEMA = @schemaName
    `;

    const request = this.pool!.request();
    request.input('viewName', mssql.VarChar, viewName);
    request.input('schemaName', mssql.VarChar, schemaName);
    
    const result = await request.query(query);

    if (result.recordset.length === 0) {
      throw new Error(`View ${schemaName}.${viewName} not found`);
    }

    const row = result.recordset[0];

    await this.disconnect();

    return {
      name: row.view_name,
      schema: row.schema_name,
      definition: row.view_definition,
    };
  }

  async testConnection(): Promise<boolean> {
    try {
      await this.connect();
      await this.disconnect();
      return true;
    } catch {
      return false;
    }
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
    default:
      throw new Error(`Unsupported database type: ${dbType}`);
  }
}
