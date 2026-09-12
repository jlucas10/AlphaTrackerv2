import React, { useState } from 'react';
import type { Account, BackfillResult } from '../../types/Account';

interface BackfillBannerProps {
  primaryAccount: Account;
  unassignedTradeCount: number;
  onBackfill: () => Promise<BackfillResult>;
  onBackfilled: () => void | Promise<void>;
}

// Explicit-confirm step for reassigning unassigned trades onto the primary
// account: shown only when there's something to backfill, and only acts on
// a real click, since it rewrites historical trades and changes a live
// balance the moment it runs.
export const BackfillBanner: React.FC<BackfillBannerProps> = ({
  primaryAccount,
  unassignedTradeCount,
  onBackfill,
  onBackfilled,
}) => {
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (unassignedTradeCount === 0) return null;

  const handleConfirm = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await onBackfill();
      await onBackfilled();
      setConfirming(false);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to backfill trades');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-amber-50 border border-amber-100 rounded-2xl shadow-sm p-4 flex items-center justify-between gap-4 flex-wrap">
      <div>
        <p className="text-sm font-bold text-amber-800">
          {unassignedTradeCount} unassigned {unassignedTradeCount === 1 ? 'trade' : 'trades'} found
        </p>
        <p className="text-xs font-semibold text-amber-700 mt-0.5">
          {confirming
            ? `This will attach ${unassignedTradeCount} trade${unassignedTradeCount === 1 ? '' : 's'} to "${primaryAccount.name}" and update its balance.`
            : `Trades logged before a primary account existed aren't counted in any account's balance yet.`}
        </p>
        {error && <p className="text-xs font-bold text-red-600 mt-1">{error}</p>}
      </div>

      {confirming ? (
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setConfirming(false)}
            disabled={submitting}
            className="text-xs font-bold bg-white hover:bg-gray-50 text-gray-600 px-3 py-2 rounded-lg border border-gray-200 transition-colors disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className="text-xs font-bold bg-amber-600 hover:bg-amber-500 text-white px-3 py-2 rounded-lg transition-colors disabled:opacity-50"
          >
            {submitting ? 'Backfilling...' : `Confirm: Backfill to ${primaryAccount.name}`}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setConfirming(true)}
          className="text-xs font-bold bg-white hover:bg-gray-50 text-amber-700 px-3 py-2 rounded-lg border border-amber-200 transition-colors"
        >
          Backfill to Primary
        </button>
      )}
    </div>
  );
};
