import React, { useState } from "react";
import apiClient from '../api/apiClient';

// Define imports and component interface
interface TradeEntryModalProps {
    isOpen: boolean;
    onClose: () => void;
    // Returns a promise when the parent refetches, so submit can await it.
    onTradeAdded: () => void | Promise<void>;
}

// Mirrors the backend Instrument enum. Presented as a dropdown rather than a
// free-text field so an unsupported ticker is impossible to submit — the server
// rejects unknown tickers outright, and this keeps that error off the screen.
const INSTRUMENTS = [
    'ES', 'MES', 'NQ', 'MNQ', 'YM', 'MYM', 'RTY', 'M2K', 'CL', 'MCL', 'GC', 'MGC',
] as const;

// datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time. toISOString() would
// convert to UTC and could file a late-evening trade under the following day on
// the calendar matrix, so the local parts are formatted by hand.
function nowForInput(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// Initialize form and UI state
export const TradeEntryModal: React.FC<TradeEntryModalProps> = ({
    isOpen,
    onClose,
    onTradeAdded,
}) => {
    // Form state fields aligned with the backend TradeRequest DTO.
    // Commission is deliberately absent: the server derives it from the ticker's
    // round-turn fee, so there is nothing here for the trader to mistype.
    const [ticker, setTicker] = useState<string>('MNQ');
    const [direction, setDirection] = useState<'LONG' | 'SHORT'>('LONG');
    const [entryPrice, setEntryPrice] = useState<string>('');
    const [exitPrice, setExitPrice] = useState<string>('');
    const [contracts, setContracts] = useState<string>('1');
    const [tradeDate, setTradeDate] = useState<string>(nowForInput);
    const [followedPlan, setFollowedPlan] = useState<boolean>(true);
    const [notes, setNotes] = useState<string>('');

    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');

    // The modal stays mounted while closed (the early return below renders null),
    // so state has to be cleared explicitly or the next open shows stale values.
    const resetForm = () => {
        setTicker('MNQ');
        setDirection('LONG');
        setEntryPrice('');
        setExitPrice('');
        setContracts('1');
        setTradeDate(nowForInput());
        setFollowedPlan(true);
        setNotes('');
        setError('');
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    // Keys match TradeRequest exactly. No commission and no profitLoss: both are
    // derived server-side from the instrument's point value and round-turn fee.
    const payload = {
      ticker,
      direction,
      entryPrice: parseFloat(entryPrice),
      exitPrice: parseFloat(exitPrice),
      contracts: parseInt(contracts, 10),
      followedPlan,
      notes,
      // "2026-08-14T09:30" — Jackson parses this straight into LocalDateTime.
      tradeDate,
    };

    try {
      await apiClient.post('/trades', payload);
      // Await the parent's refetch so the calendar and equity curve already show
      // this trade by the time the modal disappears, rather than briefly showing
      // stale totals.
      await onTradeAdded();
      setLoading(false);
      resetForm();
      onClose();
    } catch (err: any) {
      console.error(err);
      setLoading(false);
      setError(
        err.response?.data?.message || 'Failed to log trade. Check backend connectivity.'
      );
    }
  };

  // Backdrop and panel match DayDetailModal so both dialogs read as the same
  // surface as the dashboard cards behind them.
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white border border-gray-100 p-6 shadow-2xl text-gray-800">
        <div className="flex items-center justify-between pb-4 border-b border-gray-100">
          <h3 className="text-sm font-black text-gray-900 uppercase tracking-wider">
            Log Execution
          </h3>
          <button
            onClick={handleClose}
            type="button"
            className="text-gray-300 hover:text-gray-900 font-bold text-xl transition-colors"
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="mt-4 p-3 bg-red-50 border border-red-100 text-red-600 text-xs rounded-lg font-semibold">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {/* Ticker & Direction */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Instrument
              </label>
              <select
                value={ticker}
                onChange={(e) => setTicker(e.target.value)}
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              >
                {INSTRUMENTS.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Direction
              </label>
              <select
                value={direction}
                onChange={(e) => setDirection(e.target.value as 'LONG' | 'SHORT')}
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              >
                <option value="LONG">LONG 📈</option>
                <option value="SHORT">SHORT 📉</option>
              </select>
            </div>
          </div>

          {/* Entry & Exit Prices */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Entry Price
              </label>
              <input
                type="number"
                step="any"
                required
                value={entryPrice}
                onChange={(e) => setEntryPrice(e.target.value)}
                placeholder="20150.25"
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Exit Price
              </label>
              <input
                type="number"
                step="any"
                required
                value={exitPrice}
                onChange={(e) => setExitPrice(e.target.value)}
                placeholder="20185.00"
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              />
            </div>
          </div>

          {/* Contracts & Timestamp */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Contract Size
              </label>
              <input
                type="number"
                min="1"
                required
                value={contracts}
                onChange={(e) => setContracts(e.target.value)}
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              />
            </div>

            {/* Commissions used to live here. The server now derives them from the
                instrument, so this slot holds the trade timestamp instead — the
                calendar matrix needs a real date to file the trade under, and
                backfilling yesterday's session is impossible without it. */}
            <div>
              <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
                Date & Time
              </label>
              <input
                type="datetime-local"
                required
                value={tradeDate}
                onChange={(e) => setTradeDate(e.target.value)}
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black"
              />
            </div>
          </div>

          {/* Discipline Flag */}
          <div className="flex items-center gap-3 pt-2">
            <input
              type="checkbox"
              id="followedPlan"
              checked={followedPlan}
              onChange={(e) => setFollowedPlan(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 bg-gray-50 text-emerald-600 focus:ring-0"
            />
            <label htmlFor="followedPlan" className="text-xs font-semibold text-gray-600">
              Followed Trading Rules & Risk Plan
            </label>
          </div>

          {/* Trade Notes */}
          <div>
            <label className="block text-xs font-bold uppercase text-gray-600 mb-1">
              Execution Notes / Setup Details
            </label>
            <textarea
              rows={3}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Key levels, catalyst, entry strategy..."
              className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-900 focus:outline-none focus:border-black resize-none"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-gray-100">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-600 text-xs font-bold rounded-lg transition-colors"
            >
              Cancel
            </button>
            {/* Emerald primary matches the "+ Log Trade" button that opens this dialog. */}
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-lg transition-colors disabled:opacity-50"
            >
              {loading ? 'Posting...' : 'Save Trade'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};


