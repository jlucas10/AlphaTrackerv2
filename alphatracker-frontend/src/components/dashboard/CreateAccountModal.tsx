import React, { useState } from 'react';
import type { AccountType, CreateAccountPayload, DrawdownMode } from '../../types/Account';

interface CreateAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  // makePrimary is true only when the trader ticked the box, which exists only for a promotion.
  onSubmit: (payload: CreateAccountPayload, makePrimary: boolean) => Promise<void>;
  // Starting values for a funded account created after passing an evaluation (see fundedPrefillFrom).
  // The parent mounts this modal fresh each time it opens, so these only seed the initial state.
  prefill?: CreateAccountPayload;
  defaultMakePrimary?: boolean;
}

export const CreateAccountModal: React.FC<CreateAccountModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
  prefill,
  defaultMakePrimary = false,
}) => {
  const promoting = prefill?.promotedFromAccountId != null;
  const [name, setName] = useState(prefill?.name ?? '');
  const [firm, setFirm] = useState(prefill?.firm ?? '');
  const [accountType, setAccountType] = useState<AccountType>(prefill?.accountType ?? 'EVALUATION');
  const [startingBalance, setStartingBalance] = useState(String(prefill?.startingBalance ?? 50000));
  const [maxDrawdown, setMaxDrawdown] = useState(String(prefill?.maxDrawdown ?? 2000));
  // A funded account has no profit target, so a promotion starts with it blank rather than the 3000 default.
  const [profitTarget, setProfitTarget] = useState(prefill ? '' : '3000');
  const [drawdownMode, setDrawdownMode] = useState<DrawdownMode>(prefill?.drawdownMode ?? 'END_OF_DAY');
  const [trailingStopsAtBalance, setTrailingStopsAtBalance] = useState(
    prefill?.trailingStopsAtBalance != null ? String(prefill.trailingStopsAtBalance) : '',
  );
  // Closing the primary evaluation un-sets it, so its funded successor is the natural replacement: the
  // parent ticks this by default only in that case, so another primary account is never taken over silently.
  const [makePrimary, setMakePrimary] = useState(defaultMakePrimary);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await onSubmit({
        name: name.trim(),
        firm: firm.trim(),
        accountType,
        startingBalance: parseFloat(startingBalance),
        maxDrawdown: parseFloat(maxDrawdown),
        profitTarget: profitTarget ? parseFloat(profitTarget) : undefined,
        drawdownMode,
        trailingStopsAtBalance: trailingStopsAtBalance ? parseFloat(trailingStopsAtBalance) : undefined,
        promotedFromAccountId: prefill?.promotedFromAccountId,
      }, promoting && makePrimary);
      // Reset & close
      setName('');
      setFirm('');
      onClose();
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to create account');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    // Clicking the backdrop closes; stopPropagation on the card keeps a click
    // inside the dialog from bubbling up and closing it (same pattern as
    // DayDetailModal/JournalDayPanel).
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4"
      onClick={onClose}
    >
      <div
        className="bg-neutral-900 border border-neutral-800 rounded-xl max-w-md w-full max-h-[90vh] overflow-y-auto p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
          <h2 className="text-base font-bold text-white font-mono">
            {promoting ? 'Set Up Funded Account' : 'Create Prop Account'}
          </h2>
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-200 transition text-sm font-mono"
          >
            ✕
          </button>
        </div>

        {promoting && (
          <p className="mt-4 text-xs font-mono text-emerald-400">
            🎉 Evaluation passed. These values were copied from the evaluation; change anything your funded account does differently.
          </p>
        )}

        {error && (
          <div className="mt-4 p-3 bg-rose-950/60 border border-rose-800 rounded-lg text-rose-300 text-xs font-mono">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="block text-xs font-mono text-neutral-400 mb-1">Account Label</label>
            <input
              type="text"
              required
              placeholder="e.g. Apex 50k #1"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Prop Firm</label>
              <input
                type="text"
                required
                placeholder="e.g. Apex, Topstep"
                value={firm}
                onChange={(e) => setFirm(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Account Type</label>
              <select
                value={accountType}
                // Locked while promoting: the server only accepts a FUNDED account as the successor.
                disabled={promoting}
                onChange={(e) => setAccountType(e.target.value as AccountType)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              >
                <option value="EVALUATION">Evaluation</option>
                <option value="FUNDED">Funded</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Starting ($)</label>
              <input
                type="number"
                step="any"
                required
                value={startingBalance}
                onChange={(e) => setStartingBalance(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Max DD ($)</label>
              <input
                type="number"
                step="any"
                required
                value={maxDrawdown}
                onChange={(e) => setMaxDrawdown(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              />
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Target ($)</label>
              <input
                type="number"
                step="any"
                placeholder="Optional"
                value={profitTarget}
                onChange={(e) => setProfitTarget(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Drawdown Mode</label>
              <select
                value={drawdownMode}
                onChange={(e) => setDrawdownMode(e.target.value as DrawdownMode)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              >
                <option value="END_OF_DAY">End of Day</option>
                <option value="PER_TRADE_CLOSE">Per Trade Close</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-mono text-neutral-400 mb-1">Trailing Lock ($)</label>
              <input
                type="number"
                step="any"
                placeholder="Optional"
                value={trailingStopsAtBalance}
                onChange={(e) => setTrailingStopsAtBalance(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 rounded-lg px-3 py-2 text-sm text-neutral-200 font-mono focus:outline-hidden focus:border-emerald-500"
              />
            </div>
          </div>

          {promoting && (
            <label className="flex items-center gap-2 text-xs font-mono text-neutral-300 cursor-pointer">
              <input
                type="checkbox"
                checked={makePrimary}
                onChange={(e) => setMakePrimary(e.target.checked)}
                className="accent-emerald-500"
              />
              Make this my primary account
            </label>
          )}

          <div className="flex justify-end gap-3 pt-3 border-t border-neutral-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-mono text-neutral-400 hover:text-neutral-200"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-mono font-medium rounded-lg transition disabled:opacity-50"
            >
              {submitting ? 'Creating...' : 'Create Account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};