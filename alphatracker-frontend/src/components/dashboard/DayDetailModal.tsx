import React from 'react';
import { format } from 'date-fns';
import type { Trade } from '../../types/Trade';
import TradeRow from './TradeRow';
import { formatUsd } from '../../utils/formatters';

interface DayDetailModalProps {
  day: Date;
  trades: Trade[];
  onClose: () => void;
  onDelete: (id: number) => Promise<void>;
}

const DayDetailModal: React.FC<DayDetailModalProps> = ({ day, trades, onClose, onDelete }) => {
  const dayTotal = trades.reduce((sum, t) => sum + t.profitLoss, 0);
  const wins = trades.filter((t) => t.profitLoss > 0).length;

  return (
    // Clicking the backdrop closes; the stopPropagation on the panel keeps a
    // click inside the dialog from bubbling up and closing it.
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-3xl rounded-2xl bg-white border border-gray-100 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between p-6 border-b border-gray-100">
          <div>
            <h3 className="text-sm font-black text-gray-900 uppercase tracking-wider">
              {format(day, 'EEEE, MMMM d, yyyy')}
            </h3>
            <p className="text-xs font-semibold text-gray-400 mt-1">
              {trades.length} {trades.length === 1 ? 'execution' : 'executions'} · {wins}W /{' '}
              {trades.length - wins}L
            </p>
          </div>

          <div className="flex items-center gap-6">
            <div className="text-right">
              <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Net P/L</p>
              <p
                className={`text-2xl font-black ${
                  dayTotal >= 0 ? 'text-emerald-500' : 'text-red-500'
                }`}
              >
                {formatUsd(dayTotal)}
              </p>
            </div>
            <button
              onClick={onClose}
              type="button"
              className="text-gray-300 hover:text-gray-900 font-bold text-xl transition-colors"
            >
              ✕
            </button>
          </div>
        </div>

        <div className="p-6 max-h-[60vh] overflow-y-auto">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] border-collapse">
              <thead>
                <tr className="border-b border-gray-100">
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
                {trades.map((trade) => (
                  // showDate off: the header already states the day.
                  <TradeRow key={trade.id} trade={trade} onDelete={onDelete} showDate={false} />
                ))}
              </tbody>
            </table>
          </div>

          {/* Notes are the point of a journal, so surface them under the table
              rather than cramming them into a column. */}
          {trades.some((t) => t.notes) && (
            <div className="mt-6 space-y-3 border-t border-gray-100 pt-4">
              <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Notes</p>
              {trades
                .filter((t) => t.notes)
                .map((t) => (
                  <div key={t.id} className="text-xs text-gray-600">
                    <span className="font-black text-gray-900">{t.ticker}</span>{' '}
                    <span className="text-gray-400">—</span> {t.notes}
                  </div>
                ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DayDetailModal;
