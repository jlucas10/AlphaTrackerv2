import React, { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useAssistant } from '../../hooks/useAssistant';
import { MAX_MESSAGE_CHARS, STARTER_QUESTIONS } from '../../utils/assistantChat';
import { MessageBubble } from './MessageBubble';

// A slide-over drawer, mounted once at the top of the app (App.tsx) and always in the page: closing it
// only slides it away, so a half-typed message and the scroll position survive. `inert` while closed
// keeps keyboard focus from wandering into something off-screen.
export const ChatPanel: React.FC = () => {
  const { isAuthenticated, logout } = useAuth();
  const { isOpen, close, messages, sending, error, send, retry, clear } = useAssistant();
  const [draft, setDraft] = useState('');
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isOpen) inputRef.current?.focus();
  }, [isOpen]);

  // Keep the newest message (or the "thinking" line, or an error) in view.
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, sending, error]);

  if (!isAuthenticated) return null; // nothing to ask about until you are signed in

  const submit = () => {
    const text = draft.trim();
    if (!text || sending) return;
    setDraft('');
    void send(text);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter sends, Shift+Enter makes a new line. Ignore Enter while an IME is composing (e.g. Japanese
    // input), where it confirms the text instead of sending it.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const showCounter = draft.length > MAX_MESSAGE_CHARS * 0.8;
  const overLimit = draft.length > MAX_MESSAGE_CHARS;
  // Only a message that got no answer can be retried; a rejected-for-length draft was never sent.
  const canRetry = messages[messages.length - 1]?.role === 'user' && error?.kind !== 'invalid';

  return (
    <aside
      role="dialog"
      aria-label="Trade assistant"
      aria-modal="false"
      aria-hidden={!isOpen}
      inert={!isOpen}
      onKeyDown={(e) => {
        if (e.key === 'Escape') close();
      }}
      className={`fixed inset-y-0 right-0 z-40 flex w-full flex-col border-l border-gray-100 bg-white shadow-2xl transition-[transform,visibility] duration-200 sm:w-[420px] ${
        isOpen ? 'visible translate-x-0' : 'invisible translate-x-full'
      }`}
    >
      <header className="flex items-start justify-between border-b border-gray-100 px-5 py-4">
        <div>
          <h2 className="text-sm font-black uppercase tracking-wider text-gray-900">Trade assistant</h2>
          <p className="mt-0.5 text-xs font-semibold text-gray-400">Answers come from your own trade data</p>
        </div>
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={clear}
            disabled={messages.length === 0}
            className="text-xs font-bold text-gray-400 transition-colors hover:text-gray-900 disabled:opacity-40 disabled:hover:text-gray-400"
          >
            Clear
          </button>
          <button
            type="button"
            onClick={close}
            aria-label="Close assistant"
            className="text-lg font-bold text-gray-300 transition-colors hover:text-gray-900"
          >
            ✕
          </button>
        </div>
      </header>

      <div role="log" aria-live="polite" className="flex-1 space-y-4 overflow-y-auto px-5 py-5">
        {messages.length === 0 && !sending && (
          <div>
            <p className="text-sm font-bold text-gray-900">Ask about your trading</p>
            <p className="mt-1 text-sm text-gray-500">
              Win rates, P&amp;L, setups, plan-following, your journal. It only reads your data; it can&apos;t change anything.
            </p>
            <div className="mt-4 flex flex-col gap-2">
              {STARTER_QUESTIONS.map((question) => (
                <button
                  key={question}
                  type="button"
                  onClick={() => void send(question)}
                  className="rounded-xl border border-gray-100 bg-gray-50 px-4 py-2.5 text-left text-sm font-semibold text-gray-700 transition-colors hover:border-gray-200 hover:bg-white"
                >
                  {question}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}

        {/* Answers take 4-20 seconds and there is no streaming, so say that something is happening. */}
        {sending && (
          <div className="flex justify-start">
            <div className="rounded-2xl rounded-bl-md border border-gray-100 bg-gray-50 px-4 py-3 text-sm text-gray-400">
              <span className="animate-pulse font-semibold">Looking through your trades…</span>
            </div>
          </div>
        )}

        {error && (
          <div role="alert" className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
            <p>{error.message}</p>
            <div className="mt-2 flex gap-4 text-xs font-bold">
              {error.kind === 'auth' && (
                <button type="button" onClick={logout} className="underline">
                  Sign in again
                </button>
              )}
              {error.kind !== 'auth' && canRetry && (
                <button type="button" onClick={() => void retry()} disabled={sending} className="underline disabled:opacity-40">
                  Try again
                </button>
              )}
            </div>
          </div>
        )}

        <div ref={endRef} />
      </div>

      <footer className="border-t border-gray-100 px-5 py-4">
        <div className="flex items-end gap-2">
          <textarea
            ref={inputRef}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
            rows={2}
            placeholder="Ask about your trades…"
            aria-label="Message the assistant"
            className="max-h-32 min-h-[3rem] flex-1 resize-none rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-800 outline-none transition-colors focus:border-slate-900"
          />
          <button
            type="button"
            onClick={submit}
            disabled={!draft.trim() || sending || overLimit}
            className="rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white transition-colors hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-40"
          >
            Send
          </button>
        </div>
        <div className="mt-2 flex items-center justify-between text-[11px] font-semibold text-gray-400">
          <span>Check anything important. Not financial advice.</span>
          {showCounter && (
            <span className={overLimit ? 'text-red-500' : ''}>
              {draft.length}/{MAX_MESSAGE_CHARS}
            </span>
          )}
        </div>
      </footer>
    </aside>
  );
};
