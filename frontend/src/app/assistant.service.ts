import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AssistantChatResponse, AssistantConfirmResponse, AssistantMessage, PendingAssistantAction } from './assistant.model';

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly apiUrl = 'http://localhost:8080/api/assistant';

  constructor(private http: HttpClient) {}

  chat(patientId: number | null, messages: AssistantMessage[]): Observable<AssistantChatResponse> {
    return this.http.post<AssistantChatResponse>(`${this.apiUrl}/chat`, { patientId, messages });
  }

  confirm(action: PendingAssistantAction): Observable<AssistantConfirmResponse> {
    return this.http.post<AssistantConfirmResponse>(`${this.apiUrl}/confirm`, action);
  }
}