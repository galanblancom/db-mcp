import { Component, OnInit, Inject, NgZone, ChangeDetectorRef, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { McpClientService, McpTool } from './mcp-client.service';
import { ChatService, ChatMessage } from './chat.service';
import { MCP_BASE_URL } from './app.config';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, HttpClientModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, AfterViewChecked {
  @ViewChild('chatMessages') private chatMessagesContainer?: ElementRef;
  
  baseUrl: string;
  sseUrl: string;
  tools$: any;
  logs$: any;
  
  selectedTool: McpTool | null = null;
  toolArgs = '{}';
  executionResult: any = null;

  // Chat functionality
  chatMessage = '';
  chatHistory: Array<{type: 'user' | 'assistant', content: string}> = [];
  isConnected = false;
  isSendingMessage = false;
  private shouldScrollToBottom = false;
  currentThreadId: string | null = null;
  threadCount = 0;

  constructor(
    private mcpService: McpClientService,
    private chatService: ChatService,
    @Inject(MCP_BASE_URL) baseUrl: string,
    private zone: NgZone,
    private cdr: ChangeDetectorRef
  ) {
    this.baseUrl = baseUrl;
    this.sseUrl = `${baseUrl}/mcp/sse`;
  }

  ngOnInit() {
      this.tools$ = this.mcpService.tools$;
      this.logs$ = this.mcpService.logs$;
      
      // Subscribe to connection status
      this.mcpService.connected$.subscribe(connected => {
        this.isConnected = connected;
      });

      // Subscribe to thread ID changes
      this.chatService.threadId$.subscribe(threadId => {
        this.currentThreadId = threadId;
      });

      // Check if chat backend is available
      this.checkChatBackend();

      // Load active threads count
      this.loadThreadsCount();
  }

  checkChatBackend() {
    this.chatService.listThreads().subscribe({
      next: () => {
        console.log('Chat backend is available');
      },
      error: (err) => {
        console.warn('Chat backend not available:', err.status === 0 ? 'Server not running' : err.message);
      }
    });
  }

  ngAfterViewChecked() {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  private scrollToBottom(): void {
    try {
      if (this.chatMessagesContainer) {
        this.chatMessagesContainer.nativeElement.scrollTop = 
          this.chatMessagesContainer.nativeElement.scrollHeight;
      }
    } catch(err) {
      console.error('Scroll error:', err);
    }
  }

  connect() {
    this.mcpService.connect(this.sseUrl);
  }

  selectTool(tool: McpTool) {
    this.selectedTool = tool;
    // Pre-fill arguments based on schema if possible, for now just empty JSON
    this.toolArgs = JSON.stringify(this.generateDefaultArgs(tool.inputSchema), null, 2);
    this.executionResult = null;
  }

  executeTool() {
    if (!this.selectedTool) return;
    
    try {
      const args = JSON.parse(this.toolArgs);
      this.mcpService.callTool(this.selectedTool.name, args).subscribe({
        next: (res) => this.executionResult = res,
        error: (err) => this.executionResult = { error: err.message }
      });
    } catch (e) {
      alert('Invalid JSON arguments');
    }
  }

  private generateDefaultArgs(schema: any): any {
      if (!schema || !schema.properties) return {};
      const args: any = {};
      for (const key in schema.properties) {
          args[key] = ""; // Default empty string or null
      }
      return args;
  }

  sendChatMessage() {
    if (!this.chatMessage.trim() || this.isSendingMessage) return;
    
    this.isSendingMessage = true;
    
    // Add user message to history
    this.chatHistory = [...this.chatHistory, {
      type: 'user',
      content: this.chatMessage
    }];
    this.shouldScrollToBottom = true;
    
    const userMessage = this.chatMessage;
    this.chatMessage = '';
    
    // Use the chat service with conversation threads
    this.chatService.sendMessage(userMessage).subscribe({
      next: (response) => {
        this.zone.run(() => {
          if (response.success && response.response) {
            this.chatHistory = [...this.chatHistory, {
              type: 'assistant',
              content: response.response
            }];
          } else if (response.error) {
            this.chatHistory = [...this.chatHistory, {
              type: 'assistant',
              content: `❌ Error: ${response.error}`
            }];
          }
          this.isSendingMessage = false;
          this.shouldScrollToBottom = true;
          this.loadThreadsCount();
          this.cdr.detectChanges();
        });
      },
      error: (err: any) => {
        this.zone.run(() => {
          let errorMessage = '❌ Error: ';
          
          if (err.status === 0) {
            errorMessage += 'Cannot connect to server. Please ensure the Spring Boot backend is running on http://localhost:8081';
          } else if (err.status === 503) {
            errorMessage += err.error?.error || 'Chat service is not available. OpenAI may not be configured.';
          } else if (err.error?.error) {
            errorMessage += err.error.error;
          } else {
            errorMessage += err.message || 'Unknown error occurred';
          }
          
          this.chatHistory = [...this.chatHistory, {
            type: 'assistant',
            content: errorMessage
          }];
          this.isSendingMessage = false;
          this.shouldScrollToBottom = true;
          this.cdr.detectChanges();
        });
      }
    });
  }

  newConversation() {
    this.chatService.newConversation();
    this.chatHistory = [];
    this.shouldScrollToBottom = true;
  }

  clearConversation() {
    if (!this.currentThreadId) {
      this.newConversation();
      return;
    }

    this.chatService.clearConversation().subscribe({
      next: () => {
        this.chatHistory = [];
        this.loadThreadsCount();
        this.shouldScrollToBottom = true;
      },
      error: (err) => console.error('Error clearing conversation:', err)
    });
  }

  loadThreadsCount() {
    this.chatService.listThreads().subscribe({
      next: (response) => {
        this.threadCount = response.count;
      },
      error: (err) => console.error('Error loading threads:', err)
    });
  }

  showThreads() {
    this.chatService.listThreads().subscribe({
      next: (response) => {
        if (response.threads.length === 0) {
          alert('No active conversations');
        } else {
          const threadList = response.threads.map(t => `• ${t}`).join('\n');
          alert(`Active Conversations (${response.count}):\n\n${threadList}`);
        }
      },
      error: (err) => alert(`Error: ${err.message}`)
    });
  }
}
