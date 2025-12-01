import { Inject, Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { MCP_BASE_URL } from './app.config';

export interface McpTool {
  name: string;
  description?: string;
  inputSchema?: any;
}

@Injectable({
  providedIn: 'root'
})
export class McpClientService {
  private eventSource: EventSource | null = null;
  private postEndpoint: string | null = null;
  
  private toolsSubject = new BehaviorSubject<McpTool[]>([]);
  public tools$ = this.toolsSubject.asObservable();

  private logsSubject = new BehaviorSubject<string[]>([]);
  public logs$ = this.logsSubject.asObservable();

  private connectedSubject = new BehaviorSubject<boolean>(false);
  public connected$ = this.connectedSubject.asObservable();

  constructor(private http: HttpClient, private zone: NgZone, @Inject(MCP_BASE_URL) private baseUrl: string) {}

  connect(url: string) {
    this.log(`Connecting to SSE endpoint: ${url}`);
    this.eventSource = new EventSource(url);

    this.eventSource.onmessage = (event) => {
      this.zone.run(() => {
        this.log(`Received SSE message: ${event.data}`);
        try {
          const data = JSON.parse(event.data);
          this.handleMessage(data);
        } catch (e) {
          this.log(`Error parsing message: ${e}`);
        }
      });
    };

    this.eventSource.onerror = (error) => {
      this.zone.run(() => {
        this.log(`SSE Error: ${JSON.stringify(error)}`);
        this.eventSource?.close();
      });
    };
    
    // Listen for specific 'endpoint' event if Spring AI sends it as a named event
    this.eventSource.addEventListener('endpoint', (event: any) => {
         this.zone.run(() => {
             this.log(`Received endpoint event: ${event.data}`);
             try {
               // Parse the JSON data to extract the endpoint
               const data = JSON.parse(event.data);
               this.postEndpoint = data.endpoint;
               this.connectedSubject.next(true);
               this.log(`POST endpoint set to: ${this.postEndpoint}`);
               this.initializeMcp();
             } catch (e) {
               this.log(`Error parsing endpoint data: ${e}`);
             }
         });
    });
  }

  private handleMessage(data: any) {
    // Handle JSON-RPC notifications or responses if they come via SSE
    if (data.method === 'notifications/tools/list_changed') {
        this.fetchTools();
    }
  }

  private initializeMcp() {
      if (!this.postEndpoint) return;
      
      const initRequest = {
          jsonrpc: '2.0',
          id: 1,
          method: 'initialize',
          params: {
              protocolVersion: '2024-11-05',
              capabilities: {},
              clientInfo: {
                  name: 'angular-client',
                  version: '1.0.0'
              }
          }
      };
      
      this.sendRequest(initRequest).subscribe(response => {
          this.log(`Initialize response: ${JSON.stringify(response)}`);
          // Send initialized notification
          this.sendNotification('notifications/initialized');
          this.fetchTools();
      });
  }

  private fetchTools() {
      const request = {
          jsonrpc: '2.0',
          id: 2,
          method: 'tools/list',
          params: {}
      };
      
      this.sendRequest(request).subscribe((response: any) => {
          if (response.result && response.result.tools) {
              this.toolsSubject.next(response.result.tools);
              this.log(`Tools updated: ${response.result.tools.length} tools found`);
          }
      });
  }

  callTool(toolName: string, args: any) {
      const request = {
          jsonrpc: '2.0',
          id: Date.now(),
          method: 'tools/call',
          params: {
              name: toolName,
              arguments: args
          }
      };
      
      return this.sendRequest(request);
  }

  sendRawRequest(request: any): Observable<any> {
      return this.sendRequest(request);
  }

  private sendRequest(payload: any): Observable<any> {
      if (!this.postEndpoint) {
          this.log('Error: No POST endpoint available');
          throw new Error('No POST endpoint');
      }
      
      // If the endpoint is relative, make it absolute based on the SSE URL origin?
      // Usually Spring AI sends a full URL or a relative path.
      // For now assume it might be relative to the server root.
      let url = this.postEndpoint;
      if (url.startsWith('/')) {
          // Assuming localhost:8080 for now if relative
           url = `${this.baseUrl}${url}`;
      }

      return this.http.post(url, payload);
  }
  
  private sendNotification(method: string, params?: any) {
      if (!this.postEndpoint) return;
      
       let url = this.postEndpoint;
      if (url.startsWith('/')) {
           url = `${this.baseUrl}${url}`;
      }
      
      this.http.post(url, {
          jsonrpc: '2.0',
          method: method,
          params: params
      }).subscribe();
  }

  private log(message: string) {
    const currentLogs = this.logsSubject.value;
    this.logsSubject.next([...currentLogs, `[${new Date().toLocaleTimeString()}] ${message}`]);
    console.log(message);
  }
}
