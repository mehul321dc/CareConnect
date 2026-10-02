export interface AssistantMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface PendingAssistantAction {
  toolName: string;
  arguments: Record<string, unknown>;
  summary: string;
}

export interface AssistantChatResponse {
  reply: string;
  pendingAction: PendingAssistantAction | null;
}

export interface AssistantConfirmResponse {
  result: string;
}