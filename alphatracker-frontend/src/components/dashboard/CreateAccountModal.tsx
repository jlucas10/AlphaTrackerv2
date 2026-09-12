import React, { useState } from 'react';
import type { AccountType, CreateAccountPayload, DrawdownMode } from '../../types/Account';

interface CreateAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateAccountPayload) => Promise<void>;
}

export const CreateAccountModal: React.FC<CreateAccountModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [name, setName] = useState('');
  const [firm, setFirm] = useState('');
  const [accountType, setAccountType] = useState<AccountType>('EVALUATION');
  const [startingBalance, setStartingBalance] = useState('50000');
  const [maxDrawdown, setMaxDrawdown] = useState('2000');
  const [profitTarget, setProfitTarget] = useState('3000');
  const [drawdownMode, setDrawdownMode] = useState<DrawdownMode>('END_OF_DAY');
  const [trailingStopsAtBalance, setTrailingStopsAtBalance] = useState('');
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
      });
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4">
      <div className="bg-neutral-900 border border-neutral-800 rounded-xl max-w-md w-full p-6 shadow-2xl">
        <div className="flex items-center justify-between pb-3 border-b border-neutral-800">
          <h2 className="text-base font-bold text-white font-mono">Create Prop Account</h2>
          <button
            onClick={onClose}
            className="text-neutral-400 hover:text-neutral-200 transition text-sm font-mono"
          >
            ✕
          </button>
        </div>

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