// The pure logic behind the assistant chat: what to send as history, how to read an error,
// who a token belongs to. No React and no network in here, on purpose, so it can be checked
// on its own and the hook stays thin.

import type { AssistantErrorKind, ChatMessage, HistoryTurn, ToolCall } from '../types/Assistant';

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

// ------------------------------------------------------------------ configuration
// A base URL typed into a hosting dashboard often loses its scheme ("my-service.up.railway.app").
// Axios then treats it as a RELATIVE path, so every request goes to the website itself and fails with
// a 405 that explains nothing (this happened on the first deploy). So: add https:// when it is missing
// (http:// for localhost), trim spaces, and drop trailing slashes. Empty means "use the fallback".
export function normalizeBaseUrl(raw: string | undefined, fallback: string): string {
  const value = raw?.trim();
  if (!value) return fallback;
  const hasScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(value);
  const scheme = /^(localhost|127\.0\.0\.1)(:|\/|$)/.test(value) ? 'http' : 'https';
  return (hasScheme ? value : `${scheme}://${value}`).replace(/\/+$/, '');
}

// ------------------------------------------------------------------ errors
export class AssistantError extends Error {
  readonly kind: AssistantErrorKind;
  readonly retryAfterSeconds?: number;
  readonly createdAt: number;  // when the error arrived (ms since epoch)
  readonly retryAt?: number;   // the moment sending is allowed again (ms since epoch), for rate limits

  // `now` is a parameter so the clock can be controlled when checking this; callers normally omit it.
  constructor(kind: AssistantErrorKind, message: string, retryAfterSeconds?: number, now: number = Date.now()) {
    super(message);
    this.name = 'AssistantError';
    this.kind = kind;
    this.retryAfterSeconds = retryAfterSeconds;
    this.createdAt = now;
    // A fixed deadline, worked out ONCE here. A countdown that subtracts a second per tick drifts, and
    // browsers slow timers in background tabs; comparing against a deadline is right however late a tick is.
    this.retryAt = retryAfterSeconds === undefined ? undefined : now + retryAfterSeconds * 1000;
  }
}

// "45 seconds", "about 12 minutes", "about 20 hours": a daily limit can mean hours, and "72000s" is no use.
export function formatWait(seconds: number): string {
  if (seconds < 90) {
    const whole = Math.max(1, Math.ceil(seconds));
    return `${whole} ${whole === 1 ? 'second' : 'seconds'}`;
  }
  if (seconds < 7200) return `about ${Math.round(seconds / 60)} minutes`;
  return `about ${Math.round(seconds / 3600)} hours`;
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

export function toAssistantError(err: unknown, now: number = Date.now()): AssistantError {
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
        now,
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

// ------------------------------------------------------------------ presentation
// What the empty chat offers. Each is a question the assistant answers well, so a first-time user
// gets a good first answer instead of a blank box.
export const STARTER_QUESTIONS = [
  "What's my win rate this month?",
  'Do I do better when I follow my plan?',
  'Which setup has made me the most money?',
  'What did I write in my journal last week?',
];

const TOOL_LABELS: Record<string, string> = {
  get_summary: 'Performance summary',
  get_breakdown: 'Breakdown',
  get_discipline: 'Plan-following stats',
  get_journal_entries: 'Journal notes',
  list_accounts: 'Your accounts',
};

const PERIOD_LABELS: Record<string, string> = {
  today: 'today',
  this_week: 'this week',
  last_week: 'last week',
  this_month: 'this month',
  last_month: 'last month',
  last_30_days: 'last 30 days',
  all_time: 'all time',
};

// Tools that look at trades over a window; with no period or dates given they cover all time.
const WINDOWED_TOOLS = new Set(['get_summary', 'get_breakdown', 'get_discipline']);

// "Breakdown (by instrument, this month)": what the assistant looked at to answer, in the trader's
// words rather than tool names and ids. Shown under each answer so a number can be traced to its source.
export function describeToolCall(call: ToolCall): string {
  const parts: string[] = [];
  if (call.name === 'get_breakdown' && typeof call.args.by === 'string') parts.push(`by ${call.args.by}`);

  const { period, from_date: from, to_date: to } = call.args;
  if (typeof period === 'string') parts.push(PERIOD_LABELS[period] ?? period);
  else if (from || to) parts.push(`${from ?? '…'} to ${to ?? '…'}`);
  else if (WINDOWED_TOOLS.has(call.name)) parts.push('all time');

  const label = TOOL_LABELS[call.name] ?? call.name.replace(/_/g, ' ');
  return parts.length ? `${label} (${parts.join(', ')})` : label;
}

// The distinct things looked at. A call the assistant repeated is not listed twice, and one that
// failed says so, because an answer built partly on a failed lookup should not look fully sourced.
export function describeSources(calls: ToolCall[] | undefined): string[] {
  const lines = (calls ?? [])
    .filter((c) => c.status !== 'repeat')
    .map((c) => (c.status === 'ok' ? describeToolCall(c) : `${describeToolCall(c)}: couldn't load`));
  return [...new Set(lines)];
}
