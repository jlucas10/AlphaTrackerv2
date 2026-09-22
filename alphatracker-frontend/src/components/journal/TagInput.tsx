import React, { useState } from 'react';

interface TagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  suggestions?: string[];
  disabled?: boolean;
}

// Freeform multi-select chips: type + Enter (or pick a suggestion) to add,
// click the x to remove. `suggestions` is whatever tags are already visible
// on this page (no dedicated GET /api/v1/setup-tags endpoint exists yet), so
// autocomplete is scoped to "tags used elsewhere today," not a trader's full
// history - a reasonable MVP limit, not a hard architectural one.
export const TagInput: React.FC<TagInputProps> = ({ tags, onChange, suggestions = [], disabled }) => {
  const [draft, setDraft] = useState('');

  const addTag = (raw: string) => {
    const value = raw.trim();
    if (!value || tags.includes(value)) return;
    onChange([...tags, value]);
    setDraft('');
  };

  const removeTag = (value: string) => {
    onChange(tags.filter((t) => t !== value));
  };

  const visibleSuggestions = suggestions.filter(
    (s) => !tags.includes(s) && (draft === '' || s.toLowerCase().includes(draft.toLowerCase())),
  );

  return (
    <div>
      <div className="flex flex-wrap gap-1.5 items-center">
        {tags.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs font-semibold"
          >
            {tag}
            {!disabled && (
              <button
                type="button"
                onClick={() => removeTag(tag)}
                className="text-emerald-500 hover:text-emerald-700"
                aria-label={`Remove ${tag}`}
              >
                ✕
              </button>
            )}
          </span>
        ))}
        {!disabled && (
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                addTag(draft);
              } else if (e.key === 'Backspace' && draft === '' && tags.length > 0) {
                removeTag(tags[tags.length - 1]);
              }
            }}
            placeholder="Add a setup tag..."
            className="min-w-[120px] flex-1 text-xs font-semibold text-gray-600 placeholder-gray-400 outline-none px-1 py-0.5"
          />
        )}
      </div>
      {!disabled && draft && visibleSuggestions.length > 0 && (
        <div className="mt-1 flex flex-wrap gap-1.5">
          {visibleSuggestions.slice(0, 6).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => addTag(s)}
              className="px-2 py-0.5 rounded-full bg-gray-50 border border-gray-200 text-gray-500 text-xs font-semibold hover:bg-gray-100"
            >
              + {s}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
