import React from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';

// The assistant writes Markdown: bold figures, short lists, the odd heading. react-markdown turns it
// into real React elements, and it is safe to point at text we do not control (an answer can quote a
// journal note, and a journal note can say anything): raw HTML is NOT rendered, it shows up as plain
// text, so a `<script>` or `<img onerror=...>` is inert; and `javascript:` links are dropped. Markdown
// IMAGES are not dropped by the library, so they are blocked below (see `img`).
//
// Tailwind has no typography plugin in this project, so every element is styled here. Headings are all
// squeezed to one small label style: a 420px panel has no room for page-sized headings.
const components: Components = {
  p: ({ children }) => <p className="leading-relaxed [&:not(:first-child)]:mt-2">{children}</p>,
  ul: ({ children }) => <ul className="mt-2 list-disc space-y-1 pl-5">{children}</ul>,
  ol: ({ children }) => <ol className="mt-2 list-decimal space-y-1 pl-5">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  strong: ({ children }) => <strong className="font-bold text-gray-900">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  h1: ({ children }) => <h3 className="mt-3 text-xs font-black uppercase tracking-wider text-gray-500">{children}</h3>,
  h2: ({ children }) => <h3 className="mt-3 text-xs font-black uppercase tracking-wider text-gray-500">{children}</h3>,
  h3: ({ children }) => <h3 className="mt-3 text-xs font-black uppercase tracking-wider text-gray-500">{children}</h3>,
  h4: ({ children }) => <h3 className="mt-3 text-xs font-black uppercase tracking-wider text-gray-500">{children}</h3>,
  hr: () => <hr className="my-3 border-gray-200" />,
  blockquote: ({ children }) => <blockquote className="mt-2 border-l-2 border-gray-300 pl-3 text-gray-600">{children}</blockquote>,
  code: ({ children }) => <code className="rounded bg-gray-100 px-1 py-0.5 text-[0.85em]">{children}</code>,
  // Links open in a new tab without handing the new page a reference back to this one. A link with no
  // safe destination (react-markdown blanks a `javascript:` URL to "") becomes plain text: an empty href
  // would otherwise reload this page in a new tab.
  a: ({ href, children }) =>
    href ? (
      <a href={href} target="_blank" rel="noopener noreferrer" className="underline">
        {children}
      </a>
    ) : (
      <span>{children}</span>
    ),
  // Images are NEVER rendered. The browser fetches an <img> automatically, with no click, so a
  // prompt-injected answer (say, steered by text planted in a journal note) could embed private figures
  // in an image URL and quietly send them to someone else's server. Markdown images are not something
  // a trade answer needs, so they are replaced with a note instead.
  img: () => <span className="text-gray-400">[image omitted]</span>,
};

export const AnswerMarkdown: React.FC<{ text: string }> = ({ text }) => (
  <ReactMarkdown components={components}>{text}</ReactMarkdown>
);
