import React, { useMemo } from 'react';
import { compareDesc, parseISO } from 'date-fns';
import type { Trade } from '../../types/Trade';
import TradeRow from './TradeRow';

interface TradeTableProps {
  trades: Trade[];
  onDelete: (id: number) => Promise<void>;
}

const TradeTable: React.FC<TradeTableProps> = ({ trades, onDelete }) => {
  // Newest first: a journal is read from the most recent session backwards.
  // Sorting a copy because the array is shared with the calendar and equity
  // curve, and sort() mutates in place.
  const sorted = useMemo(
    () => [...trades].sort((a, b) => compareDesc(parseISO(a.tradeDate), parseISO(b.tradeDate))),
    [trades],
  );

  return (
    <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">
          Trade Executions
        </p>
        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">
          {sorted.length} logged
        </p>
      </div>

      {sorted.length === 0 ? (
        <p className="text-sm text-gray-400 font-semibold py-8 text-center">
          No trades logged yet. Use “+ Log Trade” to add your first execution.
        </p>
      ) : (
        // Horizontal scroll container: the row has 9 columns and would otherwise
        // force the whole dashboard to scroll sideways on a narrow window.
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="py-2 px-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-wider">Date</th>
                <th className="py-2 px-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-wider">Ticker</th>
                <th className="py-2 px-3 text-left text-[10px] font-bold text-gray-400 uppercase tracking-wider">Side</th>
                <th className="py-2 px-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-wider">Entry</th>
                <th className="py-2 px-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-wider">Exit</th>
                <th className="py-2 px-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-wider">Qty</th>
                <th className="py-2 px-3 text-right text-[10px] font-bold text-gray-400 uppercase tracking-wider">Net P/L</th>
                <th className="py-2 px-3 text-center text-[10px] font-bold text-gray-400 uppercase tracking-wider">Plan</th>
                <th className="py-2 px-3" />
              </tr>
            </thead>
            <tbody>
              {sorted.map((trade) => (
                <TradeRow key={trade.id} trade={trade} onDelete={onDelete} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default TradeTable;
