// The pure logic behind the assistant chat: what to send as history, how to read an error,
// who a token belongs to. No React and no network in here, on purpose, so it can be checked
// on its own and the hook stays thin.

import type { AssistantErrorKind, ChatMessage, HistoryTurn } from '../types/Assistant';

// These MIRROR alphatracker-assistant/app/chat.py (MAX_MESSAGE_CHARS, MAX_HISTORY_TURNS,
// MAX_HISTORY_ITEM_CHARS). If the server tightens one and this is not updated, requests start
// failing with a 422 partway through a conversation, which is a confusing way to find out.
export const MAX_MESSAGE_CHARS = 2000;
export const MAX_HISTORY_TURNS = 20;
export const MAX_HISTORY_ITEM_CHARS = 4000;

// The earlier conversation to send along with a new message. The service keeps no memory,
// so the browser is the memory.
export function buildHistory(messages: ChatMessage[]): HistoryTurn[] {
  const turns: HistoryTurn[] = [];
  messages.forEach((m, i) => {
    // A user message with no answer after it is one whose request FAILED. Leave it out: sending
    // it would put two user turns in a row in front of the model.
    if (m.role === 'user' && messages[i + 1]?.role !== 'assistant') return;
    // A long answer sent back as history could exceed the server's per-item limit and get the
    // whole request rejected, so cap it here.
    turns.push({ role: m.role, content: m.content.slice(0, MAX_HISTORY_ITEM_CHARS) });
  });

  const recent = turns.slice(-MAX_HISTORY_TURNS);
  // Cutting to the last N can start the conversation on an assistant turn, which the service
  // would trim anyway; doing it here keeps what we send equal to what it uses.
  while (recent.length > 0 && recent[0].role !== 'user') recent.shift();
  return recent;
}

// ------------------------------------------------------------------ errors
export class AssistantError extends Error {
  readonly kind: AssistantErrorKind;
  readonly retryAfterSeconds?: number;

  constructor(kind: AssistantErrorKind, message: string, retryAfterSeconds?: number) {
    super(message);
    this.name = 'AssistantError';
    this.kind = kind;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

// The few fields we read from an Axios error. Declared structurally, instead of importing
// Axios's type, so this file has no dependency on it.
interface ErrorLike {
  code?: string;
  response?: { status?: number; data?: unknown; headers?: Record<string, unknown> };
}

export function isCanceled(err: unknown): boolean {
  return (err as ErrorLike)?.code === 'ERR_CANCELED';
}

// The assistant service is FastAPI, so its errors are { detail: "..." }, unlike the Spring
// backend's { message: "..." }. Reading the wrong key would silently lose every message.
function detailOf(data: unknown): string | undefined {
  const detail = (data as { detail?: unknown } | null)?.detail;
  return typeof detail === 'string' && detail.trim() ? detail : undefined; // a 422 detail is a list; not for display
}

export function toAssistantError(err: unknown): AssistantError {
  const { code, response } = (err ?? {}) as ErrorLike;

  if (!response) {
    return code === 'ECONNABORTED'
      ? new AssistantError('network', 'The assistant took too long to answer. Please try again.')
      : new AssistantError('network', "Couldn't reach the assistant. Check that the service is running.");
  }

  switch (response.status) {
    case 401:
      return new AssistantError('auth', 'Your session has expired. Please sign in again.');
    case 429: {
      // The server's header, readable by the browser only because the service exposes it (CORS).
      const wait = Number(response.headers?.['retry-after']);
      return new AssistantError(
        'rate_limited',
        detailOf(response.data) ?? 'Too many messages. Please wait a moment.',
        Number.isFinite(wait) && wait > 0 ? wait : undefined,
      );
    }
    case 422:
      return new AssistantError('invalid', "That message couldn't be sent. Try a shorter one.");
    default:
      return new AssistantError('unavailable', detailOf(response.data) ?? 'The assistant is unavailable right now.');
  }
}

// ------------------------------------------------------------------ identity
// Who a JWT belongs to (its `sub`, the email). CLIENT-SIDE ONLY, to keep one person's saved chat
// from showing to the next person on this browser. The token is not verified here and must never
// be trusted for anything about access: the server does that.
export function subjectOf(token: string | null): string | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload + '='.repeat((4 - (payload.length % 4)) % 4)), (c) => c.charCodeAt(0));
    const sub = (JSON.parse(new TextDecoder().decode(bytes)) as { sub?: unknown }).sub;
    return typeof sub === 'string' && sub ? sub.toLowerCase() : null;
  } catch {
    return null;
  }
}
