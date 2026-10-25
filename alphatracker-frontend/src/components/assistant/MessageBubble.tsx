import React from 'react';
import type { ChatMessage } from '../../types/Assistant';
import { describeSources } from '../../utils/assistantChat';
import { AnswerMarkdown } from './AnswerMarkdown';

export const MessageBubble: React.FC<{ message: ChatMessage }> = ({ message }) => {
  if (message.role === 'user') {
    return (
      <div className="flex justify-end">
        {/* Plain text, never Markdown: what you typed is shown as you typed it. */}
        <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-slate-900 px-4 py-2.5 text-sm text-white">
          {message.content}
        </div>
      </div>
    );
  }

  const sources = describeSources(message.toolCalls);
  return (
    <div className="flex justify-start">
      <div className="max-w-[92%]">
        <div className="break-words rounded-2xl rounded-bl-md border border-gray-100 bg-gray-50 px-4 py-3 text-sm text-gray-800">
          <AnswerMarkdown text={message.content} />
        </div>
        {/* What the answer was built from. A native <details>: keyboard and screen-reader friendly, no state. */}
        {sources.length > 0 && (
          <details className="mt-1.5 px-1 text-xs text-gray-400">
            <summary className="cursor-pointer select-none font-semibold hover:text-gray-600">
              Looked at {sources.length} {sources.length === 1 ? 'source' : 'sources'}
            </summary>
            <ul className="mt-1 space-y-0.5">
              {sources.map((source) => (
                <li key={source}>• {source}</li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </div>
  );
};
