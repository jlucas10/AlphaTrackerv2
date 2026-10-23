import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { askAssistant } from '../api/assistant';
import type { ChatMessage } from '../types/Assistant';
import {
  AssistantError,
  MAX_MESSAGE_CHARS,
  buildHistory,
  isCanceled,
  subjectOf,
  toAssistantError,
} from '../utils/assistantChat';

// sessionStorage, not localStorage: it is cleared when the tab closes. Trading notes are private,
// and a conversation is not worth keeping around on a shared computer.
const STORAGE_KEY = 'assistant.chat.v1';
const MAX_SAVED_MESSAGES = 40;

export interface AssistantChat {
  messages: ChatMessage[];
  sending: boolean;                 // an answer is on its way (they take 4-20 seconds; there is no streaming)
  error: AssistantError | null;     // why the last message failed, if it did
  send: (text: string) => Promise<void>;
  retry: () => Promise<void>;       // re-send the message that got no answer, without duplicating it
  clear: () => void;
}

const newId = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;

function isChatMessage(x: unknown): x is ChatMessage {
  const m = x as Partial<ChatMessage> | null;
  return !!m && typeof m.id === 'string' && typeof m.content === 'string' && (m.role === 'user' || m.role === 'assistant');
}

// A saved conversation counts only if it was saved by whoever is signed in RIGHT NOW. Reading the
// token straight from storage (as apiClient does) means there is no moment where the previous
// person's messages are rendered, even for one frame.
function loadSaved(): ChatMessage[] {
  try {
    const subject = subjectOf(localStorage.getItem('token'));
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!subject || !raw) return [];
    const saved = JSON.parse(raw) as { owner?: unknown; messages?: unknown };
    if (saved.owner !== subject || !Array.isArray(saved.messages)) return [];
    return saved.messages.filter(isChatMessage);
  } catch {
    return [];
  }
}

function save(owner: string, messages: ChatMessage[]) {
  try {
    if (messages.length === 0) sessionStorage.removeItem(STORAGE_KEY);
    else sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ owner, messages: messages.slice(-MAX_SAVED_MESSAGES) }));
  } catch {
    // Storage blocked or full: the chat still works, it just won't survive a refresh.
  }
}

// `owner` is who is signed in: undefined = auth is still loading (do nothing yet), null = signed out,
// a string = that user. The distinction matters: on page load the token is briefly null while it is
// read from storage, and treating that as a sign-out would wipe the conversation on every refresh.
export function useAssistantChat(owner: string | null | undefined): AssistantChat {
  const [messages, setMessages] = useState<ChatMessage[]>(loadSaved);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<AssistantError | null>(null);

  // Refs let the callbacks read the current values without being re-created on every message.
  const messagesRef = useRef(messages);
  const sendingRef = useRef(false);     // blocks a double-click from starting two requests before React re-renders
  const abortRef = useRef<AbortController | null>(null);
  const ownerRef = useRef<string | null | undefined>(undefined);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const run = useCallback(async (content: string, appendUser: boolean) => {
    if (sendingRef.current) return;
    sendingRef.current = true;

    // The history is everything BEFORE this message, taken before it is appended.
    const history = buildHistory(messagesRef.current);
    if (appendUser) setMessages((prev) => [...prev, { id: newId(), role: 'user', content }]);
    setSending(true);
    setError(null);

    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const reply = await askAssistant(content, history, controller.signal);
      setMessages((prev) => [
        ...prev,
        { id: newId(), role: 'assistant', content: reply.answer, toolCalls: reply.tool_calls },
      ]);
    } catch (err) {
      if (isCanceled(err)) return; // the user cleared the chat or left; nothing to report
      setError(toAssistantError(err));
    } finally {
      // Only the request that is still current may mark things finished. If the user cleared the
      // chat and started a new message meanwhile, this older request must not switch off the new one.
      if (abortRef.current === controller) {
        abortRef.current = null;
        sendingRef.current = false;
        setSending(false);
      }
    }
  }, []);

  const send = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed) return;
      if (trimmed.length > MAX_MESSAGE_CHARS) {
        setError(new AssistantError('invalid', `Messages are limited to ${MAX_MESSAGE_CHARS} characters.`));
        return;
      }
      await run(trimmed, true);
    },
    [run],
  );

  const retry = useCallback(async () => {
    const last = messagesRef.current[messagesRef.current.length - 1];
    if (last?.role !== 'user') return; // the last message was answered; there is nothing to retry
    await run(last.content, false);
  }, [run]);

  const clear = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    sendingRef.current = false;
    setSending(false);
    setError(null);
    setMessages([]);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* storage unavailable: nothing was saved anyway */
    }
  }, []);

  // Signing out, or a DIFFERENT person signing in, wipes the conversation. (undefined = still loading.)
  useEffect(() => {
    if (owner === undefined) return;
    if (ownerRef.current !== undefined && ownerRef.current !== owner) clear();
    ownerRef.current = owner;
  }, [owner, clear]);

  // Keep the saved copy in step, tagged with who it belongs to.
  useEffect(() => {
    if (typeof owner === 'string') save(owner, messages);
  }, [owner, messages]);

  // Leaving the app abandons a request that is still in flight.
  useEffect(() => () => abortRef.current?.abort(), []);

  return useMemo(
    () => ({ messages, sending, error, send, retry, clear }),
    [messages, sending, error, send, retry, clear],
  );
}
