import React, { useState } from "react";
import apiClient from '../api/apiClient';

// Define imports and component interface 
interface TradeEntryModalProps {
    isOpen: boolean;
    onClose: () => void;
    onTradeAdded: () => void;
}

// Initialize form and UI state
export const TradeEntryModalProps: React.FC<TradeEntryModalProps> = ({
    isOpen,
    onClose,
    onTradeAdded,
}) => {
    // Form state fields aligned with your backend Trade entity
    const [symbol, setSymbol] = useState<string>('MNQ');
    const [direction, setDirection] = useState<'LONG' | 'SHORT'>('LONG');
    const [entryPrice, setEntryPrice] = useState<string>('');
    const [exitPrice, setExitPrice] = useState<string>('');
    const [quantity, setQuantity] = useState<string>('1');
    const [commission, setCommission] = useState<string>('1.30');
    const [followedPlan, setFollowedPlan] = useState<boolean>(true);
    const [notes, setNotes] = useState<string>('');
    
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string>('');

    if (!isOpen) return null;

    const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const payload = {
      symbol,
      direction,
      entryPrice: parseFloat(entryPrice),
      exitPrice: parseFloat(exitPrice),
      quantity: parseInt(quantity, 10),
      commission: parseFloat(commission),
      followedPlan,
      notes,
    };

    try {
      await apiClient.post('/trades', payload);
      setLoading(false);
      onTradeAdded(); // Refetch live trades in the parent view
      onClose();      // Close modal on success
    } catch (err: any) {
      console.error(err);
      setLoading(false);
      setError(
        err.response?.data?.message || 'Failed to log trade. Check backend connectivity.'
      );
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg rounded-2xl bg-slate-900 border border-slate-800 p-6 shadow-2xl text-slate-100">
        <div className="flex items-center justify-between pb-4 border-b border-slate-800">
          <h3 className="text-lg font-bold text-white">Log Execution</h3>
          <button
            onClick={onClose}
            type="button"
            className="text-slate-400 hover:text-white font-bold text-xl transition-colors"
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="mt-4 p-3 bg-red-950/50 border border-red-800 text-red-300 text-xs rounded-lg font-semibold">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {/* Ticker & Direction */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Instrument
              </label>
              <input
                type="text"
                required
                value={symbol}
                onChange={(e) => setSymbol(e.target.value.toUpperCase())}
                placeholder="MNQ, NQ, ES..."
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Direction
              </label>
              <select
                value={direction}
                onChange={(e) => setDirection(e.target.value as 'LONG' | 'SHORT')}
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
              >
                <option value="LONG">LONG 📈</option>
                <option value="SHORT">SHORT 📉</option>
              </select>
            </div>
          </div>

          {/* Entry & Exit Prices */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Entry Price
              </label>
              <input
                type="number"
                step="any"
                required
                value={entryPrice}
                onChange={(e) => setEntryPrice(e.target.value)}
                placeholder="20150.25"
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Exit Price
              </label>
              <input
                type="number"
                step="any"
                required
                value={exitPrice}
                onChange={(e) => setExitPrice(e.target.value)}
                placeholder="20185.00"
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
              />
            </div>
          </div>

          {/* Contracts & Commissions */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Contract Size
              </label>
              <input
                type="number"
                min="1"
                required
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
                Commissions ($)
              </label>
              <input
                type="number"
                step="any"
                value={commission}
                onChange={(e) => setCommission(e.target.value)}
                className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600"
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
              className="h-4 w-4 rounded border-slate-800 bg-slate-950 text-emerald-500 focus:ring-0"
            />
            <label htmlFor="followedPlan" className="text-xs font-semibold text-slate-300">
              Followed Trading Rules & Risk Plan
            </label>
          </div>

          {/* Trade Notes */}
          <div>
            <label className="block text-xs font-bold uppercase text-slate-400 mb-1">
              Execution Notes / Setup Details
            </label>
            <textarea
              rows={3}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Key levels, catalyst, entry strategy..."
              className="w-full p-2.5 bg-slate-950 border border-slate-800 rounded-lg text-sm text-white focus:outline-none focus:border-slate-600 resize-none"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-bold rounded-lg transition-colors"
            >
              Cancel
            </button>
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


