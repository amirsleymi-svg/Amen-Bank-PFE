import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';
import { v4 as uuidv4 } from 'uuid';

interface ChatMsg {
  role: 'user' | 'assistant';
  content: string;
  topic?: string;
  actions?: string[];
  timestamp: Date;
  loading?: boolean;
}

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  template: `
<div class="chat-window">
  <div class="chat-header">
    <div class="chat-avatar">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="white" stroke-width="1.75" stroke-linejoin="round"/>
        <path d="M9 12l2 2 4-4" stroke="white" stroke-width="1.75" stroke-linecap="round"/>
      </svg>
    </div>
    <div class="chat-title">
      <strong>Assistant Amen Bank</strong>
      <span class="online-dot">En ligne</span>
    </div>
  </div>

  <div class="chat-messages" #msgContainer>
    <div *ngFor="let msg of messages" class="msg-wrapper" [class.user]="msg.role === 'user'">
      <div class="msg-bubble" [class.user]="msg.role === 'user'" [class.loading]="msg.loading">
        <div class="msg-content" [innerHTML]="formatMessage(msg.content)"></div>
        <span class="msg-time">{{ msg.timestamp | date:'HH:mm' }}</span>
      </div>
      <div class="msg-actions" *ngIf="msg.actions?.length && msg.role === 'assistant'">
        <button *ngFor="let action of msg.actions" class="action-chip"
                (click)="sendAction(action)">{{ action }}</button>
      </div>
    </div>

    <!-- Typing indicator -->
    <div class="msg-wrapper" *ngIf="isTyping">
      <div class="msg-bubble typing">
        <span class="dot"></span><span class="dot"></span><span class="dot"></span>
      </div>
    </div>
  </div>

  <div class="chat-input">
    <input
      #msgInput
      [(ngModel)]="inputText"
      (keydown.enter)="sendMessage()"
      placeholder="Posez votre question..."
      [disabled]="isTyping"
      maxlength="500"
    />
    <button class="send-btn" (click)="sendMessage()" [disabled]="!inputText.trim() || isTyping">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
        <path d="M22 2L11 13M22 2L15 22l-4-9-9-4 20-7z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </button>
  </div>
</div>
  `,
  styleUrls: ['./chatbot-widget.component.scss']
})
export class ChatbotWidgetComponent implements OnInit, AfterViewChecked {

  @ViewChild('msgContainer') msgContainer!: ElementRef;
  @ViewChild('msgInput') msgInput!: ElementRef;

  messages: ChatMsg[] = [];
  inputText  = '';
  isTyping   = false;
  sessionId  = '';

  private readonly CHATBOT_URL = environment.chatbotUrl;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.sessionId = localStorage.getItem('ab_chat_session') ?? uuidv4();
    localStorage.setItem('ab_chat_session', this.sessionId);
    this.addWelcome();
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  addWelcome(): void {
    this.messages.push({
      role: 'assistant',
      content: 'Bonjour ! 👋 Je suis votre assistant bancaire. Comment puis-je vous aider ?',
      actions: ['Voir mon solde', 'Faire un virement', 'Simuler un crédit', 'Aide & FAQ'],
      timestamp: new Date()
    });
  }

  sendMessage(): void {
    const text = this.inputText.trim();
    if (!text || this.isTyping) return;

    this.messages.push({ role: 'user', content: text, timestamp: new Date() });
    this.inputText = '';
    this.isTyping  = true;

    const payload: any = {
      session_id: this.sessionId,
      message:    text,
    };

    const user = this.authService.currentUser();
    if (user) payload.user_id = user.id;

    const token = this.authService.getAccessToken();
    const headers: any = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    this.http.post<any>(`${this.CHATBOT_URL}/chat`, payload, { headers }).subscribe({
      next: res => {
        this.isTyping = false;
        this.messages.push({
          role:    'assistant',
          content:  res.message,
          topic:    res.topic,
          actions:  res.suggested_actions,
          timestamp: new Date()
        });
      },
      error: () => {
        this.isTyping = false;
        this.messages.push({
          role:    'assistant',
          content: 'Désolé, je rencontre des difficultés. Veuillez réessayer.',
          timestamp: new Date()
        });
      }
    });
  }

  sendAction(action: string): void {
    this.inputText = action;
    this.sendMessage();
  }

  formatMessage(text: string): string {
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
      .replace(/•/g, '&bull;');
  }

  private scrollToBottom(): void {
    try {
      const el = this.msgContainer.nativeElement;
      el.scrollTop = el.scrollHeight;
    } catch {}
  }
}
