// Shapes for the AI assistant. The wire format mirrors alphatracker-assistant/app/chat.py.

// One tool the assistant used to answer. Shown to the trader as "what it looked at".
export interface ToolCall {
  name: string;                    // get_summary, get_breakdown, get_discipline, get_journal_entries, list_accounts
  args: Record<string, unknown>;   // e.g. { period: 'this_month', by: 'instrument' }
  status: string;                  // 'ok' | 'error' | 'repeat'
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCall[];          // assistant messages only
}

// What the server accepts as earlier conversation: text only, no ids and no tool traces.
export interface HistoryTurn {
  role: 'user' | 'assistant';
  content: string;
}

// POST /chat response (note snake_case: it is the service's JSON, passed through untouched).
export interface ChatReply {
  answer: string;
  tool_calls: ToolCall[];
}

// Each kind needs a different reaction in the panel, which is why they are separate.
export type AssistantErrorKind =
  | 'auth'          // 401: the login is missing or expired -> offer to sign in again
  | 'rate_limited'  // 429: too many messages -> show how long to wait
  | 'unavailable'   // 502/503: the assistant or its data is down -> try again shortly
  | 'invalid'       // 422: the message itself was rejected -> shorten it
  | 'network';      // never got an answer: offline, service not running, or timed out
