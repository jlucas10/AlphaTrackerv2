import React from 'react';
import type { Account } from '../../types/Account';

interface DrawdownGaugeProps {
  account: Account | null;
}

export const DrawdownGauge: React.FC<DrawdownGaugeProps> = ({ account }) => {
  if (!account) {
    return (
      <div className="bg-white border border-gray-100 rounded-2xl shadow-sm p-4 text-gray-400 text-xs font-semibold flex items-center justify-between">
        <span>No specific prop account selected.</span>
        <span className="text-gray-300">Select an account to view trailing drawdown.</span>
      </div>
    );
  }

  const starting = account.startingBalance;
  const current = account.currentBalance;
  const maxDd = account.maxDrawdown;
  const pnl = current - starting;

  // highWaterMark and drawdownFloor are computed server-side from the full
  // trade history under the account's drawdownMode (END_OF_DAY vs
  // PER_TRADE_CLOSE) and trailingStopsAtBalance — never re-derived here.
  const drawdownFloor = account.drawdownFloor;
  const cushion = Math.max(0, current - drawdownFloor);
  const cushionPercent = Math.min(100, Math.max(0, (cushion / maxDd) * 100));

  const isSafe = cushionPercent > 50;
  const isWarning = cushionPercent <= 50 && cushionPercent > 20;

  const barColor = isSafe ? 'bg-emerald-500' : isWarning ? 'bg-amber-500' : 'bg-red-500';

  return (
    <div className="bg-white border border-gray-100 rounded-2xl shadow-sm p-6 flex flex-col justify-between">
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
              {account.firm}
            </span>
            <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">
              {account.accountType}
            </span>
            <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">
              {account.drawdownMode === 'PER_TRADE_CLOSE' ? 'Per-Trade' : 'EOD'} DD
            </span>
          </div>
          <h3 className="text-sm font-black text-gray-900 mt-1">{account.name}</h3>
        </div>
        <div className="text-right">
          <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">Net P/L</span>
          <p className={`text-sm font-bold ${pnl >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
            {pnl >= 0 ? `+$${pnl.toFixed(2)}` : `-$${Math.abs(pnl).toFixed(2)}`}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2 py-2.5 border-y border-gray-100 my-3 text-center">
        <div>
          <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">Balance</span>
          <p className="text-xs font-bold text-gray-900">
            ${current.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
        <div>
          <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">DD Floor</span>
          <p className="text-xs font-bold text-gray-500">
            ${drawdownFloor.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
        <div>
          <span className="text-[10px] font-bold uppercase tracking-wider text-gray-400">Buffer Left</span>
          <p className={`text-xs font-bold ${cushionPercent <= 20 ? 'text-red-600' : 'text-emerald-600'}`}>
            ${cushion.toLocaleString('en-US', { minimumFractionDigits: 2 })}
          </p>
        </div>
      </div>

      <div>
        <div className="flex justify-between text-[11px] font-semibold text-gray-400 mb-1">
          <span>Trailing Drawdown Room</span>
          <span>{cushionPercent.toFixed(1)}%</span>
        </div>
        <div className="w-full bg-gray-100 h-1.5 rounded-full overflow-hidden">
          <div
            className={`h-full ${barColor} transition-all duration-500`}
            style={{ width: `${cushionPercent}%` }}
          />
        </div>
      </div>
    </div>
  );
};