import React, { useState } from 'react';
import { format, parseISO } from 'date-fns';
import type { Trade } from '../../types/Trade';
import { formatUsd } from '../../utils/formatters';

interface TradeRowProps {
  trade: Trade;
  onDelete: (id: number) => Promise<void>;
  // The day-detail modal already states the date in its header, so it hides the
  // per-row date column to avoid repeating it 8 times.
  showDate?: boolean;
}

const TradeRow: React.FC<TradeRowProps> = ({ trade, onDelete, showDate = true }) => {
  const [deleting, setDeleting] = useState(false);

  const isWin = trade.profitLoss > 0;
  const isLong = trade.direction?.toUpperCase() === 'LONG';

  const handleDelete = async () => {
    // Deleting is irreversible and there is no undo, so confirm first.
    if (!window.confirm(`Delete this ${trade.ticker} trade? This cannot be undone.`)) {
      return;
    }
    setDeleting(true);
    try {
      await onDelete(trade.id);
      // No setDeleting(false) on success: the refetch unmounts this row.
    } catch {
      setDeleting(false); // keep the row usable if the delete failed
    }
  };

  return (
    <tr className="border-b border-gray-50 last:border-0 hover:bg-gray-50/70 transition-colors">
      {showDate && (
        <td className="py-3 px-3 text-xs font-semibold text-gray-500 whitespace-nowrap">
          {format(parseISO(trade.tradeDate), 'MMM d, HH:mm')}
        </td>
      )}

      <td className="py-3 px-3 text-xs font-black text-gray-900">{trade.ticker}</td>

      <td className="py-3 px-3">
        <span
          className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
            isLong ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'
          }`}
        >
          {isLong ? 'Long' : 'Short'}
        </span>
      </td>

      <td className="py-3 px-3 text-xs font-semibold text-gray-600 text-right tabular-nums">
        {trade.entryPrice}
      </td>
      <td className="py-3 px-3 text-xs font-semibold text-gray-600 text-right tabular-nums">
        {trade.exitPrice}
      </td>
      <td className="py-3 px-3 text-xs font-semibold text-gray-600 text-right tabular-nums">
        {trade.contracts}
      </td>

      <td
        className={`py-3 px-3 text-xs font-black text-right tabular-nums ${
          isWin ? 'text-emerald-600' : 'text-red-600'
        }`}
      >
        {formatUsd(trade.profitLoss)}
      </td>

      <td className="py-3 px-3 text-center">
        {/* Nullable: trades logged before the followedPlan column existed have no value. */}
        {trade.followedPlan == null ? (
          <span className="text-xs text-gray-300">—</span>
        ) : trade.followedPlan ? (
          <span className="text-xs text-emerald-500">✓</span>
        ) : (
          <span className="text-xs text-red-500">✕</span>
        )}
      </td>

      <td className="py-3 px-3 text-right">
        <button
          onClick={handleDelete}
          disabled={deleting}
          title="Delete trade"
          className="text-xs font-bold text-gray-300 hover:text-red-500 transition-colors disabled:opacity-40"
        >
          {deleting ? '…' : '✕'}
        </button>
      </td>
    </tr>
  );
};

export default TradeRow;
