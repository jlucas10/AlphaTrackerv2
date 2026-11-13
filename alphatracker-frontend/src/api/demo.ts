import { isAxiosError } from 'axios';
import apiClient from './apiClient';

// POST /api/v1/auth/demo: one-click login as the shared demo user. No credentials; the backend rate-limits
// it per IP and answers with a short-lived token, in the same { token } shape as a normal login.
export async function startDemoSession(): Promise<string> {
  const res = await apiClient.post<{ token: string }>('/auth/demo');
  return res.data.token;
}

// A message a visitor can act on, from whatever went wrong.
export function demoErrorMessage(err: unknown): string {
  if (!isAxiosError(err) || !err.response) return "We couldn't reach the server. Check your connection and try again.";
  const status = err.response.status;
  if (status === 429) {
    const wait = Number(err.response.headers?.['retry-after']);
    return Number.isFinite(wait) && wait > 0
      ? `Lots of people are trying the demo right now. Please try again in about ${Math.ceil(wait / 60)} minute(s).`
      : 'Lots of people are trying the demo right now. Please try again in a few minutes.';
  }
  if (status === 503) return 'The demo is temporarily unavailable. Please try again later.';
  return 'Something went wrong starting the demo. Please try again.';
}
