declare module 'oracledb' {
  export interface Connection {
    execute(sql: string, binds?: any, options?: any): Promise<any>;
    close(): Promise<void>;
  }

  export interface ConnectionAttributes {
    user: string;
    password: string;
    connectString: string;
  }

  export function getConnection(attrs: ConnectionAttributes): Promise<Connection>;
  
  export const OUT_FORMAT_OBJECT: number;
}