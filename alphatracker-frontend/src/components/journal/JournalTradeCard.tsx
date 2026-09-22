import React, { useState } from 'react';
import { format, parseISO } from 'date-fns';
import type { Trade } from '../../types/Trade';
import { formatUsd } from '../../utils/formatters';
import { updateTrade } from '../../api/trades';
import { TagInput } from './TagInput';

interface JournalTradeCardProps {
  trade: Trade;
  allTagsToday: string[];
  onUpdated: (trade: Trade) => void;
}

// Reflection editor for one trade inside the day panel: execution rating and
// setup tags are trade-level (Sprint 3.5), unlike the day's notes/bias, since
// different trades on the same day can execute differently and use different
// setups. Ticker/direction/prices/P&L are read-only here - see
// TradeUpdateRequest on the backend for why those stay immutable after creation.
export const JournalTradeCard: React.FC<JournalTradeCardProps> = ({ trade, allTagsToday, onUpdated }) => {
  const [saving, setSaving] = useState(false);
  const isWin = trade.profitLoss > 0;
  const isLong = trade.direction?.toUpperCase() === 'LONG';

  const saveRating = async (rating: number) => {
    // Clicking the star that's already selected clears the rating - a rating
    // is optional, and there'd otherwise be no way to un-rate a trade.
    const nextRating = trade.executionRating === rating ? null : rating;
    setSaving(true);
    try {
      const updated = await updateTrade(trade.id, { executionRating: nextRating });
      onUpdated(updated);
    } finally {
      setSaving(false);
    }
  };

  const saveTags = async (tags: string[]) => {
    setSaving(true);
    try {
      const updated = await updateTrade(trade.id, { setupTags: tags });
      onUpdated(updated);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-gray-50 rounded-xl border border-gray-100 p-4 space-y-3">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xs font-black text-gray-900">{trade.ticker}</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
              isLong ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'
            }`}
          >
            {isLong ? 'Long' : 'Short'}
          </span>
          <span className="text-[10px] font-semibold text-gray-400">
            {format(parseISO(trade.tradeDate), 'h:mm a')}
          </span>
        </div>
        <span className={`text-xs font-black tabular-nums ${isWin ? 'text-emerald-600' : 'text-red-600'}`}>
          {formatUsd(trade.profitLoss)}
        </span>
      </div>

      <div className="flex items-center gap-1">
        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mr-1">Rating</span>
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            disabled={saving}
            onClick={() => saveRating(star)}
            className={`text-sm leading-none ${
              trade.executionRating != null && star <= trade.executionRating
                ? 'text-amber-400'
                : 'text-gray-200 hover:text-amber-300'
            }`}
            aria-label={`Rate ${star}`}
          >
            ★
          </button>
        ))}
      </div>

      <div>
        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">
          Setup Tags
        </span>
        <TagInput
          tags={trade.setupTags ?? []}
          onChange={saveTags}
          suggestions={allTagsToday}
          disabled={saving}
        />
      </div>
    </div>
  );
};
