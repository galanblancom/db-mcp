import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';

export interface ChatMessage {
  role: string;
  content: string;
}

export interface ChatResponse {
  success: boolean;
  response?: string;
  threadId?: string;
  error?: string;
}

export interface ConversationHistory {
  threadId: string;
  messageCount: number;
  messages: ChatMessage[];
}

export interface ThreadsResponse {
  count: number;
  threads: string[];
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private apiUrl = 'http://localhost:8081/api/chat';
  private currentThreadId: string | null = null;
  
  // Observable for current thread ID
  private threadIdSubject = new BehaviorSubject<string | null>(null);
  public threadId$ = this.threadIdSubject.asObservable();

  constructor(private http: HttpClient) {}

  /**
   * Send a message in the current conversation thread
   * If no thread exists, creates a new one
   */
  sendMessage(message: string): Observable<ChatResponse> {
    const url = this.currentThreadId 
      ? `${this.apiUrl}/${this.currentThreadId}`
      : this.apiUrl;

    console.log('Sending message with threadId:', this.currentThreadId);
    console.log('URL:', url);

    return this.http.post<ChatResponse>(url, { message }).pipe(
      tap(response => {
        console.log('Response received:', response);
        if (response.success && response.threadId) {
          console.log('Setting thread ID to:', response.threadId);
          this.setThreadId(response.threadId);
        } else {
          console.warn('No thread ID in response or request failed');
        }
      }),
      // Handle HTTP errors gracefully
      tap({
        error: (error) => {
          console.error('Chat API error:', error);
        }
      })
    );
  }

  /**
   * Send a message in a specific thread
   */
  sendMessageToThread(threadId: string, message: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.apiUrl}/${threadId}`, { message }).pipe(
      tap(response => {
        if (response.success && response.threadId) {
          this.setThreadId(response.threadId);
        }
      })
    );
  }

  /**
   * Get conversation history for current thread
   */
  getHistory(): Observable<ConversationHistory | null> {
    if (!this.currentThreadId) {
      return new Observable(observer => {
        observer.next(null);
        observer.complete();
      });
    }
    return this.http.get<ConversationHistory>(`${this.apiUrl}/${this.currentThreadId}/history`);
  }

  /**
   * Get history for a specific thread
   */
  getThreadHistory(threadId: string): Observable<ConversationHistory> {
    return this.http.get<ConversationHistory>(`${this.apiUrl}/${threadId}/history`);
  }

  /**
   * Clear current conversation
   */
  clearConversation(): Observable<any> {
    if (!this.currentThreadId) return new Observable(obs => obs.complete());
    
    const threadId = this.currentThreadId;
    return this.http.delete(`${this.apiUrl}/${threadId}`).pipe(
      tap(() => {
        this.currentThreadId = null;
        this.threadIdSubject.next(null);
      })
    );
  }

  /**
   * Clear a specific thread
   */
  clearThread(threadId: string): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${threadId}`).pipe(
      tap(() => {
        if (this.currentThreadId === threadId) {
          this.currentThreadId = null;
          this.threadIdSubject.next(null);
        }
      })
    );
  }

  /**
   * List all active conversation threads
   */
  listThreads(): Observable<ThreadsResponse> {
    return this.http.get<ThreadsResponse>(`${this.apiUrl}/threads`);
  }

  /**
   * Start a new conversation (clears current thread)
   */
  newConversation(): void {
    this.currentThreadId = null;
    this.threadIdSubject.next(null);
  }

  /**
   * Set the current thread ID
   */
  setThreadId(threadId: string): void {
    this.currentThreadId = threadId;
    this.threadIdSubject.next(threadId);
  }

  /**
   * Get the current thread ID
   */
  getCurrentThreadId(): string | null {
    return this.currentThreadId;
  }
}
