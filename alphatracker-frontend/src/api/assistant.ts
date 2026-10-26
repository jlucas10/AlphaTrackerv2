import axios from 'axios';
import { attachToken } from './apiClient';
import type { ChatReply, HistoryTurn } from '../types/Assistant';
import { normalizeBaseUrl } from '../utils/assistantChat';

// The assistant is a separate service from the Spring backend, so it has its own base URL.
// Same pattern as apiClient: baked in at build time, with a localhost fallback for `npm run dev`.
const assistantClient = axios.create({
  baseURL: normalizeBaseUrl(import.meta.env.VITE_ASSISTANT_API_URL, 'http://localhost:8000'),
  headers: { 'Content-Type': 'application/json' },
  // The slowest answer measured was about 36 seconds (a corrected answer is a second model round
  // trip), and there is no streaming, so allow comfortably more before giving up.
  timeout: 90_000,
});

// The assistant forwards this same JWT to Spring, which is what enforces that you only ever see
// your own data. The browser sends the token it already has; there is no separate assistant login.
assistantClient.interceptors.request.use(attachToken);

export async function askAssistant(message: string, history: HistoryTurn[], signal?: AbortSignal): Promise<ChatReply> {
  const res = await assistantClient.post<ChatReply>('/chat', { message, history }, { signal });
  return res.data;
}
